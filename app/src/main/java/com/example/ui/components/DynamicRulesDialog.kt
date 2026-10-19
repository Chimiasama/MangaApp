package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.source.SampleSources

@Composable
fun DynamicRulesDialog(
    currentJson: String,
    onSaveRules: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var jsonText by remember {
        mutableStateOf(if (currentJson.isNotBlank()) currentJson else SampleSources.sampleScraperRulesJson)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("dynamic_rules_modal"),
        title = {
            Text(
                text = "Regras Dinâmicas de Scraping (JSON)",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column {
                Text(
                    text = "Atualize os seletores CSS das fontes web sem atualizar o APK.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = jsonText,
                    onValueChange = { jsonText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 320.dp)
                        .testTag("json_rules_input"),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    placeholder = { Text("Cole o JSON com as regras de scraping...") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSaveRules(jsonText) },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("save_rules_button")
            ) {
                Text("Aplicar Regras")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dismiss_rules_button")
            ) {
                Text("Fechar")
            }
        }
    )
}
