package com.example.data.pdf

import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.Fetcher
import coil.request.Options
import coil.size.Dimension

class PdfPageFetcher(private val uri: Uri, private val options: Options) : Fetcher {
    override suspend fun fetch(): DrawableResult {
        val width = (options.size.width as? Dimension.Pixels)?.px ?: 1536
        val bitmap = PdfChapterStore(options.context).render(uri, (width.toLong() * 2).coerceAtMost(3072).toInt())
        return DrawableResult(BitmapDrawable(options.context.resources, bitmap), true, DataSource.DISK)
    }
    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.scheme == PdfChapterStore.SCHEME) PdfPageFetcher(data, options) else null
    }
}
