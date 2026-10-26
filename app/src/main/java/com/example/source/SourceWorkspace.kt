package com.example.source

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.json.JSONObject
import org.jsoup.Jsoup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** User-reviewed data, not executable extensions. One persistent rollback snapshot. */
class SourceWorkspace(context: Context) {
    private val prefs = context.getSharedPreferences("source_workspace_v1", Context.MODE_PRIVATE)
    private val adapter = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        .adapter<List<SourceRuleConfig>>(Types.newParameterizedType(List::class.java, SourceRuleConfig::class.java))
    private val _revision = MutableStateFlow(0)
    val revision = _revision.asStateFlow()
    fun feedUrl(): String = prefs.getString("feed_url", "").orEmpty()
    fun rememberFeedUrl(url: String) { require(prefs.edit().putString("feed_url", WebUrls.valid(url)).commit()) }
    fun snapshot(): JSONObject = JSONObject(prefs.getString("current", "{}") ?: "{}")
    fun rules(): List<SourceRuleConfig> = parseRules(snapshot().optString("rules", "[]"))
    fun parseRules(json: String): List<SourceRuleConfig> {
        require(json.length <= 300_000) { "Arquivo de regras excede 300 KB." }
        val rules = adapter.fromJson(json) ?: error("Informe uma lista JSON.")
        require(rules.size <= 50 && rules.map { it.id }.distinct().size == rules.size) { "Use até 50 fontes com IDs únicos." }
        rules.forEach { rule ->
            require(rule.id.matches(Regex("[a-z][a-z0-9_-]{2,60}")) && rule.id !in setOf("mangadex", "mangadash", "weebcentral", "leercapitulo", "web", "localpdf", "experimental_api")) { "ID reservado ou inválido: ${rule.id}" }
            WebUrls.valid(rule.baseUrl)
            require(rule.name.isNotBlank() && rule.version > 0)
            listOf(rule.listSelector, rule.titleSelector, rule.coverAttrSelector, rule.linkAttrSelector, rule.chapterListSelector,
                rule.chapterTitleSelector, rule.chapterLinkSelector, rule.pageImageSelector).forEach {
                require(it.isNotBlank() && it.length <= 500) { "Seletores obrigatórios ausentes ou grandes demais." }
                Jsoup.parse("<div></div>").select(it)
            }
            listOf(rule.detailDescSelector, rule.detailAuthorSelector).filter { it.isNotBlank() }.forEach { Jsoup.parse("").select(it) }
            require(rule.pageImageAttr.matches(Regex("[a-zA-Z][a-zA-Z0-9_-]{0,60}")))
            require(rule.searchPath.startsWith("/") && !rule.searchPath.startsWith("//") && rule.searchPath.contains("{query}")) { "searchPath precisa ser relativo e incluir {query}." }
            require(rule.popularPath.startsWith("/") && !rule.popularPath.startsWith("//"))
        }
        return rules
    }
    @Synchronized private fun save(next: JSONObject) {
        require(prefs.edit().putString("previous", snapshot().toString()).putString("current", next.toString()).commit()) { "Não foi possível salvar as fontes." }
        _revision.value++
    }
    @Synchronized fun applyRules(json: String) {
        val validated = parseRules(json)
        val current = snapshot()
        val previousRules = rules().associateBy { it.id }
        val routes = current.optJSONObject("routes") ?: JSONObject()
        val canonical = validated.map { rule ->
            val old = previousRules[rule.id]
            require(old == null || rule.version >= old.version) { "Versão anterior para ${rule.name}. Use Desfazer para restaurar." }
            if (old != null && WebUrls.origin(rule.baseUrl) != WebUrls.origin(old.baseUrl)) {
                routes.put(rule.id, WebUrls.origin(rule.baseUrl))
                rule.copy(baseUrl = old.baseUrl)
            } else rule
        }
        save(current.put("rules", adapter.toJson(canonical)).put("routes", routes))
    }
    @Synchronized fun relocate(id: String, address: String) {
        require(id !in setOf("mangadex", "web", "localpdf", "experimental_api")) { "Esta fonte usa um protocolo próprio; configure seu conector." }
        val origin = WebUrls.origin(address)
        save(snapshot().also { val routes = it.optJSONObject("routes") ?: JSONObject(); routes.put(id, origin); it.put("routes", routes) })
    }
    fun destination(id: String): String? = snapshot().optJSONObject("routes")?.optString(id)?.takeIf { it.isNotBlank() }
    @Synchronized fun configureApi(endpoint: String, provider: String, language: String) {
        require(provider.matches(Regex("[a-z0-9_-]{1,60}"))) { "Provedor inválido." }
        require(language in setOf("en", "pt-br", "es", "ja", "fr"))
        save(snapshot().put("api", JSONObject().put("endpoint", WebUrls.valid(endpoint)).put("provider", provider).put("language", language)))
    }
    fun isDisabled(id: String): Boolean = snapshot().optJSONObject("disabled")?.optBoolean(id, false) ?: false
    @Synchronized fun setDisabled(id: String, disabled: Boolean) {
        save(snapshot().also { val states = it.optJSONObject("disabled") ?: JSONObject(); states.put(id, disabled); it.put("disabled", states) })
    }
    @Synchronized fun disableApi() { save(snapshot().also { it.remove("api") }) }
    @Synchronized fun rollback() {
        val old = prefs.getString("previous", null) ?: error("Nenhuma alteração anterior para restaurar.")
        require(prefs.edit().putString("previous", snapshot().toString()).putString("current", old).commit())
        _revision.value++
    }
}
