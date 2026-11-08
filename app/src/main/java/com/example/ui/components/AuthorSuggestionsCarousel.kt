package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.source.AuthorSuggestions
import com.example.source.MangaModel
import com.example.source.hasKnownAuthor

@Composable
fun AuthorSuggestionsCarousel(suggestions: AuthorSuggestions, onMangaClick: (MangaModel) -> Unit) {
    if (suggestions.items.isEmpty() || !hasKnownAuthor(suggestions.author)) return
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("author_suggestions")) {
        Text("Mais de ${suggestions.author}", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(suggestions.items, key = { it.url }) { manga ->
                MangaCard(manga, { onMangaClick(manga) }, showSource = false, modifier = Modifier.width(120.dp))
            }
        }
    }
}
