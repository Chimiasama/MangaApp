package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.sync.CloudSyncState
import com.example.ui.components.DynamicRulesDialog
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.PrimaryPurple
import com.example.ui.viewmodel.SettingsUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.EnergySavingsLeaf
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    syncState: CloudSyncState = CloudSyncState.Idle(),
    onSaveRulesJson: (String) -> Unit,
    onSyncClicked: () -> Unit = {},
    onBackupClicked: () -> Unit = {},
    onRestoreClicked: () -> Unit = {},
    onSetCustomSyncKey: (String) -> Unit = {},
    onThemeModeSelected: (String) -> Unit = {},
    onReaderModeSelected: (String) -> Unit = {},
    onReaderOrientationSelected: (String) -> Unit = {},
    onDefaultFullscreenToggled: (Boolean) -> Unit = {},
    onAutoCloudSyncToggled: (Boolean) -> Unit = {},
    onReaderBackgroundColorSelected: (String) -> Unit = {},
    onReaderInvertColorsToggled: (Boolean) -> Unit = {},
    onPagePreloadToggled: (Boolean) -> Unit = {},
    onClearCacheClicked: () -> Unit = {},
    onRefreshCacheSize: () -> Unit = {},
    onConnectAniList: (String) -> Unit = {},
    onDisconnectAniList: () -> Unit = {},
    onAniListAutoSyncToggled: (Boolean) -> Unit = {},
    onClearAniListError: () -> Unit = {},
    onBatterySaverToggled: (Boolean) -> Unit = {},
    onBatterySaverDimPercentChanged: (Int) -> Unit = {},
    onSyncOnWifiOnlyToggled: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var showRulesModal by remember { mutableStateOf(false) }
    var syncKeyInput by remember { mutableStateOf("") }
    var showKeyEditor by remember { mutableStateOf(false) }
    var showAniListDialog by remember { mutableStateOf(false) }
    var aniListTokenInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        onRefreshCacheSize()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("settings_screen")
    ) {
        Text(
            text = "Configurações do Leitor",
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Section: Theme & Appearance Preferences (Persisted via DataStore)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("theme_preferences_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = PrimaryPurple,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tema & Aparência (DataStore)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "A preferência selecionada é salva via Jetpack Preferences DataStore.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val themeOptions = listOf(
                        "DARK" to "Escuro",
                        "LIGHT" to "Claro",
                        "SYSTEM" to "Sistema"
                    )

                    themeOptions.forEach { (mode, label) ->
                        val isSelected = state.themeMode == mode
                        if (isSelected) {
                            Button(
                                onClick = { onThemeModeSelected(mode) },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("theme_option_$mode")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(label, fontSize = 12.sp)
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onThemeModeSelected(mode) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("theme_option_$mode")
                            ) {
                                Text(label, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section: Reader Mode Preferences (Persisted via DataStore)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reader_preferences_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        tint = PrimaryPurple,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Modo de Leitura Padrão",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Estilo de navegação salvo no DataStore.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val isWebtoon = state.readerMode == "WEBTOON"
                    if (isWebtoon) {
                        Button(
                            onClick = { onReaderModeSelected("WEBTOON") },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reader_mode_webtoon")
                        ) {
                            Text("Webtoon (Rolagem)", fontSize = 12.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onReaderModeSelected("WEBTOON") },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reader_mode_webtoon")
                        ) {
                            Text("Webtoon (Rolagem)", fontSize = 12.sp)
                        }
                    }

                    val isPaged = state.readerMode == "PAGED"
                    if (isPaged) {
                        Button(
                            onClick = { onReaderModeSelected("PAGED") },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reader_mode_paged")
                        ) {
                            Text("Página por Página", fontSize = 12.sp)
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onReaderModeSelected("PAGED") },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("reader_mode_paged")
                        ) {
                            Text("Página por Página", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ScreenRotation,
                        contentDescription = null,
                        tint = PrimaryPurple,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Bloqueio de Orientação da Tela no Leitor",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Fixa a orientação da tela durante as sessões de leitura de mangá.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val orientationOptions = listOf(
                        "SENSOR" to "Livre",
                        "PORTRAIT" to "Retrato",
                        "LANDSCAPE" to "Paisagem"
                    )

                    orientationOptions.forEach { (orientationKey, label) ->
                        val isSelected = state.readerOrientation == orientationKey
                        if (isSelected) {
                            Button(
                                onClick = { onReaderOrientationSelected(orientationKey) },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("reader_orientation_$orientationKey")
                            ) {
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onReaderOrientationSelected(orientationKey) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("reader_orientation_$orientationKey")
                            ) {
                                Text(label, fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = null,
                        tint = PrimaryPurple,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Modo Tela Cheia Imersivo no Leitor",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Oculta automaticamente as barras de status e navegação ao abrir o leitor.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = state.defaultFullscreenReader,
                        onCheckedChange = { onDefaultFullscreenToggled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = PrimaryPurple),
                        modifier = Modifier.testTag("default_fullscreen_switch")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sincronização Automática em Segundo Plano",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Sincroniza o progresso automaticamente ao abrir o app.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = state.autoCloudSync,
                        onCheckedChange = { onAutoCloudSyncToggled(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = PrimaryPurple),
                        modifier = Modifier.testTag("auto_sync_switch")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Seletor de Cor de Fundo do Leitor
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = PrimaryPurple,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cor de Fundo da Leitura",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Adapte a leitura para o seu conforto visual.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val bgColors = listOf(
                        "OLED" to "OLED",
                        "DARK" to "Cinza",
                        "SEPIA" to "Sépia",
                        "LIGHT" to "Claro"
                    )
                    bgColors.forEach { (key, label) ->
                        val isSelected = state.readerBackgroundColor == key
                        if (isSelected) {
                            Button(
                                onClick = { onReaderBackgroundColorSelected(key) },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).testTag("reader_bg_$key")
                            ) {
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = { onReaderBackgroundColorSelected(key) },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f).testTag("reader_bg_$key")
                            ) {
                                Text(label, fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Inversão Noturna de Cores
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.InvertColors,
                        contentDescription = null,
                        tint = PrimaryPurple,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Modo Noturno Invertido",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Inverte as cores das páginas (preto vira branco) para leitura no escuro.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = state.readerInvertColors,
                        onCheckedChange = { onReaderInvertColorsToggled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = PrimaryPurple),
                        modifier = Modifier.testTag("reader_invert_colors_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Pré-carregamento Inteligente (Pre-fetching)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = PrimaryPurple,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Pré-carregamento de Páginas (Pre-fetch)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Baixa as próximas páginas em background para transição instantânea.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = state.pagePreloadEnabled,
                        onCheckedChange = { onPagePreloadToggled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = PrimaryPurple),
                        modifier = Modifier.testTag("page_preload_switch")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section: Economia de Bateria & Otimizações de Leitura
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("battery_saver_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.EnergySavingsLeaf,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Economia de Energia & Bateria",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Otimize consumo de tela OLED, memória e rádio móvel para leituras longas.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Toggle Modo Economia de Bateria
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Modo Economia de Bateria (Eco Mode)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Fundo OLED preto puro (#000000) desliga pixels, decodificação RGB_565 poupa 50% de RAM/GPU e reduz pré-downloads.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = state.batterySaverEnabled,
                        onCheckedChange = { onBatterySaverToggled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF4CAF50),
                            checkedTrackColor = Color(0xFF4CAF50).copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.testTag("battery_saver_switch")
                    )
                }

                if (state.batterySaverEnabled) {
                    Spacer(modifier = Modifier.height(14.dp))

                    // Slider de Atenuador de Luz Noturno (Dimmer)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Atenuador Noturno de Luminosidade",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            Text(
                                text = "${state.batterySaverDimPercent}% de redução",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF4CAF50)
                            )
                        }
                        Text(
                            text = "Atenua o brilho do display durante a leitura sem alterar as permissões do sistema.",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = state.batterySaverDimPercent.toFloat(),
                            onValueChange = { onBatterySaverDimPercentChanged(it.toInt()) },
                            valueRange = 0f..50f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF4CAF50),
                                activeTrackColor = Color(0xFF4CAF50)
                            ),
                            modifier = Modifier.testTag("battery_dim_slider")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Toggle Sincronizar Apenas no Wi-Fi
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = PrimaryPurple,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sincronizar Progresso Apenas via Wi-Fi",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Evita ativações repetidas dos módulos de rádio 4G/5G móvel, economizando bateria do modem.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = state.syncOnWifiOnly,
                        onCheckedChange = { onSyncOnWifiOnlyToggled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = PrimaryPurple),
                        modifier = Modifier.testTag("sync_wifi_only_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Card explicativo técnico de economia
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF4CAF50).copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "⚡ Telas OLED desligam pixels pretos. Decodificação RGB_565 poupa 50% de RAM. WorkManager sincroniza em lotes inteligentes.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Section: Armazenamento & Gerenciamento de Cache de Imagens (Fase 1.2)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("storage_cache_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = PrimaryPurple,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Armazenamento & Cache de Imagens",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Controle o espaço interno utilizado pelas páginas e capas salvas.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Espaço ocupado em Cache",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = state.cacheSizeFormatted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = PrimaryPurple
                        )
                    }

                    Button(
                        onClick = {
                            onClearCacheClicked()
                            Toast.makeText(context, "Cache de imagens liberado com sucesso!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("clear_cache_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Limpar Cache", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 0: Cloud Synchronization via Firebase Firestore
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("cloud_sync_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = PrimaryPurple,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sincronização na Nuvem (Firebase)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Mantenha favoritos e histórico em tempo real.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Sync Status Box
                val statusBg = when (syncState) {
                    is CloudSyncState.Success -> Color(0xFF4CAF50).copy(alpha = 0.12f)
                    is CloudSyncState.Error -> CrimsonRed.copy(alpha = 0.12f)
                    is CloudSyncState.Syncing -> PrimaryPurple.copy(alpha = 0.12f)
                    is CloudSyncState.Idle -> MaterialTheme.colorScheme.surfaceVariant
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(statusBg, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (syncState) {
                        is CloudSyncState.Syncing -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = PrimaryPurple,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Sincronizando com Firebase Firestore...",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryPurple
                            )
                        }
                        is CloudSyncState.Success -> {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                                    .format(Date(syncState.lastSyncTimestamp))
                                Text(
                                    text = syncState.message,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Última sincronização às $timeStr • User: ${syncState.cloudUserId.take(10)}...",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        is CloudSyncState.Error -> {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = null,
                                tint = CrimsonRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Erro na sincronização na nuvem",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CrimsonRed
                                )
                                Text(
                                    text = syncState.errorMessage,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        is CloudSyncState.Idle -> {
                            Icon(
                                imageVector = Icons.Default.Cloud,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Pronto para sincronizar na nuvem.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Multi-Device Custom Sync Key Config
                if (!showKeyEditor) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Chave Multi-dispositivo",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = { showKeyEditor = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("Configurar Chave", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Digite uma chave personalizada para sincronizar o mesmo histórico e favoritos entre múltiplos aparelhos:",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = syncKeyInput,
                                onValueChange = { syncKeyInput = it },
                                placeholder = { Text("Ex: minha-familia-manga-2026", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sync_key_input")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    onSetCustomSyncKey(syncKeyInput)
                                    showKeyEditor = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                            ) {
                                Text("Salvar", fontSize = 12.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Buttons
                Button(
                    onClick = onSyncClicked,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("full_sync_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sincronização Completa (Enviar & Receber)")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onBackupClicked,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("backup_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Backup (Enviar)", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onRestoreClicked,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("restore_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Restaurar (Baixar)", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section: AniList Progress Synchronization (Fase 3.1)
        val isAniListConnected = !state.aniListToken.isNullOrBlank() && !state.aniListUsername.isNullOrBlank()
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("anilist_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SyncAlt,
                        contentDescription = null,
                        tint = Color(0xFF02A9FF),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sincronização com AniList (Nuvem)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Rastreie seu progresso de leitura automaticamente.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isAniListConnected) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF02A9FF).copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!state.aniListAvatar.isNullOrBlank()) {
                            AsyncImage(
                                model = state.aniListAvatar,
                                contentDescription = "Avatar AniList",
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = Color(0xFF02A9FF),
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.aniListUsername ?: "Conectado",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Conta conectada via GraphQL",
                                fontSize = 11.sp,
                                color = Color(0xFF4CAF50)
                            )
                        }

                        OutlinedButton(
                            onClick = onDisconnectAniList,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("anilist_disconnect_button")
                        ) {
                            Text("Sair", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Sincronizar Progresso Automaticamente",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Atualiza o capítulo lido no AniList ao concluir a página.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Switch(
                            checked = state.aniListAutoSync,
                            onCheckedChange = { onAniListAutoSyncToggled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF02A9FF)),
                            modifier = Modifier.testTag("anilist_auto_sync_switch")
                        )
                    }
                } else {
                    Text(
                        text = "Conecte sua conta AniList para sincronizar seus capítulos lidos na nuvem de forma transparente.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            onClearAniListError()
                            showAniListDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF02A9FF)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("anilist_connect_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SyncAlt,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Conectar Conta AniList", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 2: Dynamic JSON Scraper Rules Engine
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Code,
                        contentDescription = null,
                        tint = CrimsonRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Arquitetura Modular & Scraping",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Atualize os seletores HTML de raspagem via JSON dinâmico sem precisar atualizar o APK na Play Store.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { showRulesModal = true },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_rules_button")
                ) {
                    Text("Gerenciar Regras de Scraping (JSON)")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 3: Room Database Persistence Status
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Persistência Local (Room Database)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Seus favoritos e histórico de leitura são mantidos de forma resiliente em tabelas SQLite gerenciadas via Room.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // App Version and Build Info Footer
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Manga Reader v1.1.0",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Versão 1.1.0 (Build 2) • Pronto para Google Play",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }

    // Modal Dynamic Rules JSON Editor
    if (showRulesModal) {
        DynamicRulesDialog(
            currentJson = state.customRulesJson,
            onSaveRules = { newJson ->
                onSaveRulesJson(newJson)
                showRulesModal = false
            },
            onDismiss = { showRulesModal = false }
        )
    }

    // Modal AniList Authentication Dialog
    if (showAniListDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                if (!state.isCheckingAniList) {
                    showAniListDialog = false
                    onClearAniListError()
                }
            },
            title = {
                Text("Conectar Conta AniList", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            },
            text = {
                Column {
                    Text(
                        text = "Para autorizar o aplicativo a registrar seu progresso de leitura, você pode gerar um Personal Token no AniList:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://anilist.co/settings/developer"))
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Abrir AniList Developer ↗", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Cole o token de acesso pessoal abaixo:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = aniListTokenInput,
                        onValueChange = { aniListTokenInput = it },
                        placeholder = { Text("Cole o token do AniList...", fontSize = 12.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("anilist_token_input"),
                        maxLines = 3
                    )
                    if (state.aniListErrorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.aniListErrorMessage,
                            color = CrimsonRed,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onConnectAniList(aniListTokenInput)
                    },
                    enabled = !state.isCheckingAniList && aniListTokenInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF02A9FF)),
                    modifier = Modifier.testTag("anilist_dialog_submit_button")
                ) {
                    if (state.isCheckingAniList) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Validar e Conectar")
                    }
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showAniListDialog = false
                        onClearAniListError()
                    },
                    enabled = !state.isCheckingAniList
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}
