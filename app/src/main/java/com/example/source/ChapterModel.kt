package com.example.source

data class ChapterModel(
    val id: String,
    val title: String,
    val chapterUrl: String,
    val chapterNumber: Float = 1.0f,
    val dateUpload: String = "",
    val language: String = "",
    val externalUrl: String = "",
    val pageCount: Int? = null
)
