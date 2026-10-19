package com.example.source

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SourceRuleConfig(
    @Json(name = "id") val id: String,
    @Json(name = "name") val name: String,
    @Json(name = "baseUrl") val baseUrl: String,
    @Json(name = "isNsfw") val isNsfw: Boolean = false,
    @Json(name = "lang") val lang: String = "pt-BR",
    
    // Selectors for listing/search
    @Json(name = "listSelector") val listSelector: String,
    @Json(name = "titleSelector") val titleSelector: String,
    @Json(name = "coverAttrSelector") val coverAttrSelector: String,
    @Json(name = "linkAttrSelector") val linkAttrSelector: String,
    
    // Selectors for details
    @Json(name = "detailDescSelector") val detailDescSelector: String = "",
    @Json(name = "detailAuthorSelector") val detailAuthorSelector: String = "",
    
    // Selectors for chapters
    @Json(name = "chapterListSelector") val chapterListSelector: String,
    @Json(name = "chapterTitleSelector") val chapterTitleSelector: String,
    @Json(name = "chapterLinkSelector") val chapterLinkSelector: String,
    
    // Selectors for chapter reader pages
    @Json(name = "pageImageSelector") val pageImageSelector: String,
    @Json(name = "pageImageAttr") val pageImageAttr: String = "src"
)
