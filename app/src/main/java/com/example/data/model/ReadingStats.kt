package com.example.data.model

data class GenreStat(
    val genre: String,
    val count: Int,
    val percentage: Float
)

data class DailySpeedStat(
    val dayLabel: String,
    val pagesRead: Int,
    val pagesPerMinute: Float,
    val durationMinutes: Float
)

data class ReadingStats(
    val totalChaptersRead: Int = 0,
    val totalPagesRead: Int = 0,
    val totalReadingTimeSeconds: Long = 0,
    val averagePagesPerMinute: Float = 0f,
    val averageSecondsPerPage: Float = 0f,
    val topGenres: List<GenreStat> = emptyList(),
    val dailyTrend: List<DailySpeedStat> = emptyList()
)
