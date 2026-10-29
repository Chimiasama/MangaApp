package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.DownloadEntity

data class DownloadCounts(val total: Int, val completed: Int, val queued: Int, val preparing: Int,
    val downloading: Int, val retrying: Int, val failed: Int) {
    val pending get() = queued + preparing + downloading + retrying
}

fun downloadCounts(items: List<DownloadEntity>) = DownloadCounts(items.size,
    items.count { it.downloadState == DownloadEntity.STATE_COMPLETED },
    items.count { it.downloadState == DownloadEntity.STATE_QUEUED },
    items.count { it.downloadState == DownloadEntity.STATE_PREPARING },
    items.count { it.downloadState == DownloadEntity.STATE_DOWNLOADING },
    items.count { it.downloadState == DownloadEntity.STATE_RETRYING },
    items.count { it.downloadState == DownloadEntity.STATE_FAILED })

fun DownloadEntity.statusText(): String = when (downloadState) {
    DownloadEntity.STATE_QUEUED -> "Na fila · aguardando execução ou conexão"
    DownloadEntity.STATE_PREPARING -> "Preparando páginas…"
    DownloadEntity.STATE_RETRYING -> "Aguardando nova tentativa"
    DownloadEntity.STATE_FAILED -> "Falha no download"
    DownloadEntity.STATE_COMPLETED -> "$totalPages páginas disponíveis offline"
    else -> if (totalPages > 0) "Baixando ${(downloadProgress * 100).toInt()}% ($downloadedPagesCount/$totalPages páginas)"
        else "Baixando…"
}

fun DownloadEntity.displayPriority(): Int = when (downloadState) {
    DownloadEntity.STATE_PREPARING, DownloadEntity.STATE_DOWNLOADING -> 0
    DownloadEntity.STATE_QUEUED -> 1
    DownloadEntity.STATE_RETRYING -> 2
    DownloadEntity.STATE_FAILED -> 3
    else -> 4
}

@Composable
fun DownloadSummary(downloads: List<DownloadEntity>, modifier: Modifier = Modifier,
    isEnqueueing: Boolean = false, error: String? = null, onOpenDownloads: (() -> Unit)? = null) {
    if (downloads.isEmpty() && !isEnqueueing && error == null) return
    val counts = downloadCounts(downloads)
    Card(modifier.fillMaxWidth().testTag("download_summary")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Downloads: ${counts.completed}/${counts.total} concluídos", style = MaterialTheme.typography.titleSmall)
            val states = buildList {
                if (counts.queued > 0) add("${counts.queued} na fila")
                if (counts.preparing > 0) add("${counts.preparing} preparando")
                if (counts.downloading > 0) add("${counts.downloading} baixando")
                if (counts.retrying > 0) add("${counts.retrying} aguardando nova tentativa")
                if (counts.failed > 0) add("${counts.failed} com falha")
            }
            Text(if (isEnqueueing) "Adicionando capítulos à fila…" else states.joinToString(" · ").ifEmpty { if (counts.total > 0 && counts.completed == counts.total) "Pronto para ler offline." else "Nenhum capítulo concluído." },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (isEnqueueing) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (onOpenDownloads != null) TextButton(onClick = onOpenDownloads,
                modifier = Modifier.testTag("open_downloads")) { Text("Acompanhar downloads") }
        }
    }
}
