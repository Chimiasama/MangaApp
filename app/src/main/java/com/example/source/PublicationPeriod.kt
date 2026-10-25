package com.example.source

/** Original publication year, never a chapter upload date. Unknown years do not match. */
data class PublicationPeriod(val from: Int, val through: Int) {
    init { require(from in 1800..2100 && through in from..2100) }
    fun matches(year: Int?) = year != null && year in from..through
    val label: String get() = "$from–$through"
    companion object {
        fun presets(): List<PublicationPeriod> {
            val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
            return listOf(PublicationPeriod(1900, 1950)) +
                (1951..currentYear step 10).map { PublicationPeriod(it, minOf(it + 9, currentYear)) }
        }
    }
}
