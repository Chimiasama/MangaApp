package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.data.repository.MangaRepository
import com.example.source.*
import kotlinx.coroutines.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONTokener

/** Browser has no JS/native bridge, file access or arbitrary-scheme navigation. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun SourceToolsScreen(repository: MangaRepository, initialInput: String = "", onClose: () -> Unit,
    onRead: suspend (WebCapture, Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val workspace = repository.sourceWorkspace
    val revision by (workspace?.revision ?: kotlinx.coroutines.flow.MutableStateFlow(0)).collectAsState()
    val registryRevision by repository.sourceRevision.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) }
    var input by rememberSaveable { mutableStateOf(initialInput) }
    var location by rememberSaveable { mutableStateOf("") }
    var browser by remember { mutableStateOf<WebView?>(null) }
    var generation by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var selector by rememberSaveable { mutableStateOf("") }
    var language by rememberSaveable { mutableStateOf("pt-br") }
    var preview by remember { mutableStateOf<WebCapture?>(null) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var rules by rememberSaveable { mutableStateOf(workspace?.snapshot()?.optString("rules", "[]") ?: "[]") }
    var pendingRules by remember { mutableStateOf<String?>(null) }
    var manifestUrl by rememberSaveable { mutableStateOf(workspace?.feedUrl().orEmpty()) }
    var sourceId by rememberSaveable { mutableStateOf("mangadash") }
    var candidate by rememberSaveable { mutableStateOf("") }
    var testedCandidate by remember { mutableStateOf<Pair<String, String>?>(null) }
    var apiUrl by rememberSaveable { mutableStateOf("") }
    var provider by rememberSaveable { mutableStateOf("mangakakalot") }
    var apiLanguage by rememberSaveable { mutableStateOf("en") }
    var apiTested by remember { mutableStateOf<Triple<String, String, String>?>(null) }
    var captureRevision by remember { mutableIntStateOf(0) }
    var addToLibrary by rememberSaveable { mutableStateOf(true) }
    var directed by rememberSaveable { mutableStateOf(true) }
    val sources = remember(revision, registryRevision) { repository.getManagedSources() }
    fun launchTask(task: suspend () -> String) {
        if (busy) return
        busy = true; message = null
        scope.launch {
            try { message = task() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { message = SourcePresentation.readError(error) }
            finally { busy = false }
        }
    }
    fun navigate(value: String) {
        runCatching { WebUrls.valid(value) }.onSuccess {
            tab = 0; generation++; preview = null; selected = emptySet(); message = null
            location = it; browser?.loadUrl(it)
        }.onFailure { message = it.message }
    }
    fun search(query: String, hosts: List<String> = emptyList()) { navigate(WebUrls.search(query, hosts)) }
    BackHandler { if (tab == 0 && browser?.canGoBack() == true) browser?.goBack() else onClose() }
    LaunchedEffect(initialInput) {
        if (initialInput.isNotBlank()) {
            if (initialInput.trim().startsWith("https://")) navigate(initialInput.trim()) else search(initialInput)
        }
    }
    Column(Modifier.fillMaxSize().padding(12.dp).testTag("source_tools")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onClose) { Text("Voltar") }
            Text("Web e fontes", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { tab = 1 }) { Text("Fontes") }
        }
        Row {
            listOf("Navegar", "Reparar", "Regras / API").forEachIndexed { index, title ->
                FilterChip(selected = tab == index, onClick = { tab = index }, label = { Text(title) }, modifier = Modifier.padding(end = 4.dp))
            }
        }
        if (busy || loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { Text(it, Modifier.padding(vertical = 6.dp).testTag("source_tools_message"), style = MaterialTheme.typography.bodySmall) }
        when (tab) {
            0 -> {
                OutlinedTextField(input, { input = it }, label = { Text("Pesquisar mangá ou colar link HTTPS") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("web_address"))
                Row {
                    Button(onClick = {
                        if (input.trim().startsWith("https://")) navigate(input.trim())
                        else search(input, if (directed) sources.map { it.baseUrl.toHttpUrl().host }.filter { it != "api.mangadex.org" } else emptyList())
                    }, modifier = Modifier.testTag("web_go")) { Text("Abrir / buscar") }
                    Checkbox(directed, { directed = it }); Text("Sites conhecidos", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)
                }
                Row {
                    OutlinedTextField(selector, { selector = it }, label = { Text("Seletor de imagens (opcional)") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(enabled = browser != null && !busy && location.isNotBlank(), onClick = {
                        val view = browser ?: return@TextButton
                        val address = view.url ?: return@TextButton
                        val ticket = generation
                        val requestedSelector = selector
                        busy = true
                        view.evaluateJavascript(CAPTURE_DOM) { encoded ->
                            scope.launch {
                                try {
                                    val html = JSONTokener(encoded).nextValue() as? String ?: error("O site não permitiu analisar a página.")
                                    val result = withContext(Dispatchers.Default) { WebExtraction.extract(html, address, requestedSelector, language) }
                                    if (ticket == generation && view.url == address) {
                                        preview = result; selected = result.pages.toSet()
                                        SourceHttp.rememberImages(result.pages, result.url)
                                        message = "${result.pages.size} imagens e ${result.chapters.size} links de capítulos candidatos. Confira a prévia."
                                    } else message = "A página mudou durante a análise. Tente novamente."
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (error: Exception) { message = SourcePresentation.readError(error) }
                                finally { busy = false }
                            }
                        }
                    }, modifier = Modifier.testTag("web_analyze")) { Text("Analisar") }
                }
                if (preview != null) {
                    val capture = preview!!
                    Text(capture.title, maxLines = 2)
                    OutlinedTextField(language, { language = it }, label = { Text("Idioma confirmado por você (pt-br, en, es…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row { Checkbox(addToLibrary, { addToLibrary = it }); Text("Adicionar à biblioteca", Modifier.padding(top = 12.dp)) }
                    Row {
                        Button(enabled = selected.isNotEmpty() && !busy, onClick = { val chosen = capture.copy(pages = capture.pages.filter { it in selected }, language = language); launchTask { onRead(chosen, addToLibrary); "Capítulo importado." } },
                            modifier = Modifier.testTag("web_read")) { Text("Ler ${selected.size} páginas") }
                        TextButton(onClick = { preview = null }) { Text("Navegador") }
                        TextButton(onClick = { selected = emptySet() }) { Text("Desmarcar") }
                    }
                    Text("Extração experimental: confirme a ordem e retire imagens que não são páginas.", style = MaterialTheme.typography.bodySmall)
                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(capture.pages, key = { _, url -> url }) { index, page ->
                            Row(Modifier.fillMaxWidth().padding(4.dp)) {
                                Checkbox(page in selected, { checked -> selected = if (checked) selected + page else selected - page })
                                AsyncImage(page, "Página ${index + 1}", Modifier.size(88.dp, 120.dp))
                                Column {
                                    TextButton(enabled = index > 0, onClick = { val order = capture.pages.toMutableList(); java.util.Collections.swap(order, index, index - 1); preview = capture.copy(pages = order) }) { Text("↑") }
                                    TextButton(enabled = index < capture.pages.lastIndex, onClick = { val order = capture.pages.toMutableList(); java.util.Collections.swap(order, index, index + 1); preview = capture.copy(pages = order) }) { Text("↓") }
                                }
                                Text("${index + 1}. ${page.substringAfterLast('/').take(80)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        itemsIndexed(capture.chapters, key = { _, ch -> ch.id }) { _, ch ->
                            TextButton(onClick = { navigate(ch.chapterUrl) }) { Text("Abrir candidato: ${ch.title}") }
                        }
                    }
                } else {
                    if (location.isBlank()) Text("Busque uma obra, abra um capítulo e toque em Analisar. A pesquisa usa a página do Google; os resultados não são importados automaticamente.", style = MaterialTheme.typography.bodySmall)
                    Text(location, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                    if (location.isNotBlank()) TextButton(onClick = {
                        runCatching { WebUrls.origin(location) }.onSuccess { candidate = it; testedCandidate = null; tab = 1 }.onFailure { message = it.message }
                    }) { Text("Propor este domínio para $sourceId") }
                }
            }
            1 -> {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { Text("Teste a fonte, procure seu endereço atual ou aplique uma correção. HTTP 200 sozinho não confirma a identidade do site.") }
                    itemsIndexed(sources, key = { _, source -> source.id }) { _, source ->
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(10.dp)) {
                            Text(source.name, style = MaterialTheme.typography.titleMedium)
                            if (workspace?.isDisabled(source.id) == true) Text("Pausada nas pesquisas")
                            Text(source.baseUrl, style = MaterialTheme.typography.bodySmall)
                            Row {
                                TextButton(onClick = { navigate(source.baseUrl) }) { Text("Abrir") }
                                TextButton(enabled = !busy, onClick = { launchTask {
                                    withContext(Dispatchers.IO) {
                                        val books = source.searchManga("dragon", language)
                                        if (books.isEmpty()) "${source.name}: consulta respondeu sem resultados. Isso não confirma que a leitura funciona."
                                        else "${source.name}: ${books.size} resultados; abra uma obra para testar capítulos e páginas."
                                    }
                                } }) { Text("Diagnosticar") }
                                TextButton(onClick = { sourceId = source.id; candidate = ""; testedCandidate = null }) { Text("Reparar") }
                                TextButton(enabled = !busy, onClick = { launchTask {
                                    withContext(Dispatchers.IO) { workspace?.setDisabled(source.id, !workspace.isDisabled(source.id)) }
                                    "Estado da fonte atualizado."
                                } }) { Text(if (workspace?.isDisabled(source.id) == true) "Ativar" else "Pausar") }
                            }
                        } }
                    }
                    item {
                        Text("Fonte selecionada: $sourceId")
                        TextButton(onClick = { search("${sources.find { it.id == sourceId }?.name ?: sourceId} site oficial novo endereço") }) { Text("Pesquisar novo endereço") }
                        OutlinedTextField(candidate, { candidate = it; testedCandidate = null }, label = { Text("Novo domínio HTTPS (mesmo site)") }, modifier = Modifier.fillMaxWidth())
                        Row {
                            Button(enabled = !busy, onClick = { val id = sourceId; val target = candidate; launchTask {
                                withContext(Dispatchers.IO) {
                                    val origin = WebUrls.origin(target)
                                    val html = SourceHttp.get(origin)
                                    val doc = org.jsoup.Jsoup.parse(html, origin)
                                    require(doc.title().isNotBlank() && !doc.title().contains("just a moment", true)) { "O endereço não entregou uma página identificável." }
                                    testedCandidate = id to origin
                                    "Endereço respondeu como: ${doc.title().take(150)}. Confira no navegador se é a mesma fonte antes de aplicar."
                                }
                            } }) { Text("Testar endereço") }
                            TextButton(onClick = { runCatching { WebUrls.origin(candidate) }.onSuccess(::navigate).onFailure { message = it.message } }) { Text("Conferir site") }
                        }
                        Button(enabled = !busy && testedCandidate != null && testedCandidate == runCatching { sourceId to WebUrls.origin(candidate) }.getOrNull(), onClick = {
                            val id = sourceId; val target = candidate
                            launchTask { withContext(Dispatchers.IO) { workspace?.relocate(id, target) ?: error("Configuração indisponível"); repository.reloadSourceWorkspace() }; "Endereço aplicado. Favoritos e histórico mantêm seus identificadores. Teste uma obra antiga; use Desfazer se necessário." }
                        }, modifier = Modifier.testTag("apply_source_address")) { Text("Confirmo que é a mesma fonte: aplicar") }
                        TextButton(enabled = !busy, onClick = { launchTask { withContext(Dispatchers.IO) { workspace?.rollback() ?: error("Configuração indisponível"); repository.reloadSourceWorkspace() }; "Última alteração desfeita." } }) { Text("Desfazer última alteração") }
                        Text("Mudança de domínio preserva o caminho. Se os caminhos ou a estrutura mudaram, atualize as regras ou abra pelo navegador. APIs e MangaDex exigem seus próprios conectores.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            2 -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("Regras de fontes HTML", style = MaterialTheme.typography.titleMedium)
                    Text("Importe dados de uma origem em que confia. A lista substitui as fontes personalizadas; não executa scripts. Fontes padrão permanecem.", style = MaterialTheme.typography.bodySmall)
                    Button(enabled = !busy, onClick = { launchTask {
                        val preset = withContext(Dispatchers.IO) { context.assets.open("source-presets/mangapill.json").bufferedReader().use { it.readText() } }
                        val merged = org.json.JSONArray()
                        val existing = org.json.JSONArray(workspace?.snapshot()?.optString("rules", "[]") ?: "[]")
                        for (index in 0 until existing.length()) if (existing.getJSONObject(index).optString("id") != "mangapill") merged.put(existing.getJSONObject(index))
                        merged.put(org.json.JSONArray(preset).getJSONObject(0))
                        rules = merged.toString(2); pendingRules = null
                        "Preset real MangaPill preparado. Valide e aplique; selecione Inglês nos filtros. Compatibilidade pode mudar com o site."
                    } }, modifier = Modifier.testTag("load_mangapill_preset")) { Text("Preparar fonte MangaPill (EN)") }
                    OutlinedTextField(manifestUrl, { manifestUrl = it }, label = { Text("URL HTTPS do arquivo JSON") }, modifier = Modifier.fillMaxWidth())
                    Button(enabled = !busy, onClick = { launchTask {
                        val json = withContext(Dispatchers.IO) { SourceHttp.get(WebUrls.valid(manifestUrl)) }
                        val parsed = withContext(Dispatchers.Default) { workspace?.parseRules(json) ?: error("Configuração indisponível") }
                        withContext(Dispatchers.IO) { workspace?.rememberFeedUrl(manifestUrl) }
                        rules = json; pendingRules = json
                        "Prévia: " + parsed.joinToString { "${it.name} v${it.version} (${WebUrls.origin(it.baseUrl)})" } + ". Revise antes de aplicar."
                    } }) { Text("Buscar e validar regras") }
                    OutlinedTextField(rules, { rules = it; pendingRules = null }, label = { Text("Regras JSON") }, modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 300.dp))
                    Row {
                        TextButton(enabled = !busy, onClick = { launchTask {
                            val list = withContext(Dispatchers.Default) { workspace?.parseRules(rules) ?: error("Configuração indisponível") }
                            pendingRules = rules
                            "Prévia: " + list.joinToString { "${it.name} v${it.version}: ${it.baseUrl}" } + if (list.isEmpty()) "nenhuma fonte personalizada." else ""
                        } }) { Text("Validar e revisar") }
                        Button(enabled = pendingRules == rules && !busy, onClick = { val json = rules; launchTask {
                            withContext(Dispatchers.IO) { workspace?.applyRules(json) ?: error("Configuração indisponível"); repository.reloadSourceWorkspace() }
                            pendingRules = null; "Regras aplicadas e salvas. Você pode desfazer em Reparar."
                        } }, modifier = Modifier.testTag("apply_reviewed_rules")) { Text("Aplicar") }
                    }
                    HorizontalDivider()
                    Text("Capturas web salvas", style = MaterialTheme.typography.titleMedium)
                    Text("Remover a captura libera espaço e não apaga favoritos, progresso ou downloads. A próxima leitura poderá exigir nova extração.", style = MaterialTheme.typography.bodySmall)
                    val saved = remember(captureRevision, tab) { repository.webLibrary?.savedUrls().orEmpty() }
                    saved.forEach { url ->
                        Row {
                            TextButton(onClick = { navigate(url) }, modifier = Modifier.weight(1f)) { Text(repository.webLibrary?.capture(url)?.title ?: url, maxLines = 1) }
                            TextButton(enabled = !busy, onClick = { launchTask { withContext(Dispatchers.IO) { repository.webLibrary?.removeCapture(url) }; captureRevision++; "Captura removida. Histórico e downloads preservados." } }) { Text("Remover captura") }
                        }
                    }
                    HorizontalDivider()
                    Text("API Consumet experimental", style = MaterialTheme.typography.titleMedium)
                    Text("Requer uma instância HTTPS sua ou autorizada compatível com o contrato Consumet. Não inclui extensões Mihon/Suwayomi nem garante que provedores externos estejam disponíveis. Nenhum catálogo é simulado.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(apiUrl, { apiUrl = it; apiTested = null }, label = { Text("Endereço da instância HTTPS") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(provider, { provider = it; apiTested = null }, label = { Text("ID do provedor") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(apiLanguage, { apiLanguage = it; apiTested = null }, label = { Text("Idioma do provedor (ex.: en)") })
                    Button(enabled = !busy, onClick = { val settings = Triple(apiUrl, provider, apiLanguage); launchTask {
                        val count = withContext(Dispatchers.IO) { ConsumetInstanceSource(WebUrls.valid(settings.first), settings.second, settings.third).searchManga("dragon").size }
                        require(count > 0) { "A instância não retornou resultados para o teste. Confira URL, provedor e contrato." }
                        apiTested = settings; "Contrato de pesquisa respondeu com $count resultados. Capítulos e páginas devem ser testados após ativar."
                    } }) { Text("Testar API") }
                    Button(enabled = apiTested == Triple(apiUrl, provider, apiLanguage) && !busy, onClick = { launchTask {
                        withContext(Dispatchers.IO) { workspace?.configureApi(apiUrl, provider, apiLanguage) ?: error("Configuração indisponível"); repository.reloadSourceWorkspace() }; "API adicionada aos filtros de fontes. Escolha o idioma e faça uma pesquisa."
                    } }) { Text("Ativar API testada") }
                    TextButton(enabled = !busy, onClick = { launchTask { withContext(Dispatchers.IO) { workspace?.disableApi(); repository.reloadSourceWorkspace() }; "API desativada." } }) { Text("Desativar API") }
                }
            }
        }
        // Keep the browser mounted while showing a preview or another tab: no page/session reset.
        Box(if (tab == 0 && preview == null) Modifier.weight(1f).fillMaxWidth() else Modifier.size(0.dp)) {
            AndroidView(factory = { context -> WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.setSupportMultipleWindows(false)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = runCatching { WebUrls.valid(request.url.toString()); false }.getOrDefault(true)
                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { generation++; loading = true; preview = null; location = url.orEmpty() }
                    override fun onPageFinished(view: WebView, url: String?) { loading = false; location = url.orEmpty() }
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) { loading = false; message = "Não foi possível abrir o site: ${error.description}" }
                    }
                }
                browser = this
                if (location.isNotBlank()) loadUrl(location)
            } }, onRelease = { view -> view.stopLoading(); view.destroy() }, modifier = Modifier.fillMaxSize())
        }
    }
}

private const val CAPTURE_DOM = """
(function(){
 const root=document.documentElement.cloneNode(true);
 root.querySelectorAll('script,style,noscript').forEach(x=>x.remove());
 const originals=Array.from(document.images);
 root.querySelectorAll('img').forEach((img,i)=>{
   const original=originals[i]; if(!original)return;
   img.setAttribute('data-natural-width',original.naturalWidth||original.width||0);
   img.setAttribute('data-natural-height',original.naturalHeight||original.height||0);
   if(original.currentSrc && !original.currentSrc.startsWith('data:') && !original.currentSrc.startsWith('blob:'))img.setAttribute('src',original.currentSrc);
 });
 const html=root.outerHTML;
 return html.length<=4000000?html:'';
})()
"""
