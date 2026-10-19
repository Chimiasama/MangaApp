package com.example.data.network

import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import retrofit2.Converter
import retrofit2.Retrofit
import java.lang.reflect.Type

/**
 * Custom Retrofit [Converter.Factory] that converts OkHttp [ResponseBody]
 * directly into Jsoup [Document] instances for HTML scraping.
 */
class JsoupConverterFactory private constructor(
    private val defaultBaseUrl: String? = null
) : Converter.Factory() {

    override fun responseBodyConverter(
        type: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): Converter<ResponseBody, *>? {
        if (type == Document::class.java) {
            return Converter<ResponseBody, Document> { value ->
                val html = value.string()
                val baseUrl = defaultBaseUrl ?: retrofit.baseUrl().toString()
                Jsoup.parse(html, baseUrl)
            }
        }
        return null
    }

    companion object {
        fun create(defaultBaseUrl: String? = null): JsoupConverterFactory {
            return JsoupConverterFactory(defaultBaseUrl)
        }
    }
}
