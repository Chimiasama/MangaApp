package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DailySpeedStat
import com.example.data.model.GenreStat
import com.example.data.model.ReadingStats
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.TextSecondaryDark
import kotlin.math.roundToInt

private val ChartColors = listOf(
    Color(0xFFD0BCFF), // Light Purple
    Color(0xFF81C784), // Emerald Green
    Color(0xFFFFB74D), // Amber / Gold
    Color(0xFF4FC3F7), // Light Blue / Cyan
    Color(0xFFFF8A65), // Coral
    Color(0xFFF06292)  // Pink
)

@Composable
fun StatsDashboardScreen(
    stats: ReadingStats,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("stats_dashboard_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Section
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoGraph,
                        contentDescription = "Estatísticas",
                        tint = PrimaryPurple,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Estatísticas de Leitura",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Acompanhe seus capítulos lidos, gêneros favoritos e velocidade de leitura.",
                    fontSize = 13.sp,
                    color = TextSecondaryDark
                )
            }
        }

        // Summary Metric Cards
        item {
            MetricGrid(stats = stats)
        }

        // Reading Speed & Velocity Chart Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("speed_chart_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = PrimaryPurple,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Velocidade de Leitura (7 dias)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(PrimaryPurple.copy(alpha = 0.15f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${String.format("%.1f", stats.averagePagesPerMinute)} p/min",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = PrimaryPurple
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    DailySpeedBarChart(dailyStats = stats.dailyTrend)

                    Spacer(modifier = Modifier.height(12.dp))

                    // Speed Insight Banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "💡 ",
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (stats.averagePagesPerMinute >= 2.0f) {
                                "Sua velocidade é excelente! Você lê em média 1 página a cada ${(stats.averageSecondsPerPage).roundToInt()}s."
                            } else {
                                "Ritmo de leitura constante e agradável! Média de ${(stats.averageSecondsPerPage).roundToInt()}s por página."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Most Read Genres Chart Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("genres_chart_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = null,
                            tint = PrimaryPurple,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Gêneros Mais Lidos",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (stats.topGenres.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Canvas Donut Chart
                            Box(
                                modifier = Modifier
                                    .size(130.dp)
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                GenreDonutChart(genres = stats.topGenres)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "${stats.topGenres.size}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = PrimaryPurple
                                    )
                                    Text(
                                        text = "Gêneros",
                                        fontSize = 10.sp,
                                        color = TextSecondaryDark
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Genre Progress Bars
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                stats.topGenres.take(4).forEachIndexed { index, genreStat ->
                                    val color = ChartColors[index % ChartColors.size]
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = genreStat.genre,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${genreStat.percentage.roundToInt()}%",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = color
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = { (genreStat.percentage / 100f).coerceIn(0f, 1f) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(RoundedCornerShape(3.dp)),
                                            color = color,
                                            trackColor = color.copy(alpha = 0.2f)
                                        )
                                    }
                                }
                            }
                        }

                        // Additional Genre Chips
                        if (stats.topGenres.size > 4) {
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(stats.topGenres.drop(4)) { genreStat ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "${genreStat.genre} (${genreStat.percentage.roundToInt()}%)",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "Nenhuma informação de gênero disponível ainda.",
                            fontSize = 12.sp,
                            color = TextSecondaryDark
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricGrid(stats: ReadingStats) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                title = "Capítulos Lidos",
                value = "${stats.totalChaptersRead}",
                unit = "capítulos",
                icon = Icons.Default.Book,
                iconTint = PrimaryPurple,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Velocidade Média",
                value = String.format("%.1f", stats.averagePagesPerMinute),
                unit = "páginas / min",
                icon = Icons.Default.Speed,
                iconTint = Color(0xFF81C784),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val hours = stats.totalReadingTimeSeconds / 3600
            val minutes = (stats.totalReadingTimeSeconds % 3600) / 60
            val timeFormatted = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

            MetricCard(
                title = "Tempo Total",
                value = timeFormatted,
                unit = "de leitura",
                icon = Icons.Default.Timer,
                iconTint = Color(0xFFFFB74D),
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Páginas Lidas",
                value = "${stats.totalPagesRead}",
                unit = "páginas",
                icon = Icons.Default.MenuBook,
                iconTint = Color(0xFF4FC3F7),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = TextSecondaryDark,
                    fontWeight = FontWeight.Medium
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = unit,
                fontSize = 11.sp,
                color = TextSecondaryDark
            )
        }
    }
}

@Composable
private fun DailySpeedBarChart(dailyStats: List<DailySpeedStat>) {
    val maxPages = (dailyStats.maxOfOrNull { it.pagesRead } ?: 30).coerceAtLeast(10)
    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(dailyStats) {
        animProgress.animateTo(1f, animationSpec = tween(durationMillis = 800))
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            dailyStats.forEach { stat ->
                val barRatio = (stat.pagesRead.toFloat() / maxPages.toFloat()).coerceIn(0.05f, 1f)
                val barHeightFraction = barRatio * animProgress.value

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    // Top page count label
                    if (stat.pagesRead > 0) {
                        Text(
                            text = "${stat.pagesRead}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryPurple
                        )
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    Spacer(modifier = Modifier.height(4.dp))

                    // Rounded Bar
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .fillMaxHeight(barHeightFraction)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(
                                if (stat.pagesRead > 0) PrimaryPurple else MaterialTheme.colorScheme.surfaceVariant
                            )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Day label
                    Text(
                        text = stat.dayLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondaryDark
                    )
                }
            }
        }
    }
}

@Composable
private fun GenreDonutChart(genres: List<GenreStat>) {
    val total = genres.sumOf { it.percentage.toDouble() }.toFloat().coerceAtLeast(1f)
    var startAngle = -90f

    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 22.dp.toPx()
        val diameter = size.minDimension - strokeWidth
        val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
        val arcSize = Size(diameter, diameter)

        genres.forEachIndexed { index, genre ->
            val sweepAngle = (genre.percentage / total) * 360f
            val color = ChartColors[index % ChartColors.size]

            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle - 2f, // Small gap between slices
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            startAngle += sweepAngle
        }
    }
}
