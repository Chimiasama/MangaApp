package com.example.source

data class MangaModel(
    val url: String,
    val id: String,
    val title: String,
    val coverUrl: String,
    val description: String = "",
    val genres: List<String> = emptyList(),
    val isNsfw: Boolean = false,
    val sourceId: String,
    val author: String = "",
    val status: String = "Unknown"
)
