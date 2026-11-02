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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.sync.CloudSyncState
import com.example.ui.components.DynamicRulesDialog
import com.example.ui.theme.AccentGold
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SystemUpdate
import com.example.BuildConfig
import com.example.ui.viewmodel.UpdateUiState
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
    onClearOldDownloadsAndCacheClicked: () -> Unit = {},
    onClearAllDownloadsClicked: () -> Unit = {},
    onAutoCleanCacheToggled: (Boolean) -> Unit = {},
    onKeepFavoritesDownloadsToggled: (Boolean) -> Unit = {},
    onConnectAniList: (String) -> Unit = {},
    onDisconnectAniList: () -> Unit = {},
    onAniListAutoSyncToggled: (Boolean) -> Unit = {},
    onClearAniListError: () -> Unit = {},
    onBatterySaverToggled: (Boolean) -> Unit = {},
    onBatterySaverDimPercentChanged: (Int) -> Unit = {},
    onSyncOnWifiOnlyToggled: (Boolean) -> Unit = {},
    onConnectionDiagnosticsToggled: (Boolean) -> Unit = {},
    homeSources: List<com.example.source.MangaSource> = emptyList(),
    onHomeSectionsChanged: (List<com.example.home.HomeSection>) -> Unit = {},
    onNsfwToggled: (Boolean) -> Unit = {},
    onCheckForUpdates: (String?) -> Unit = {},
    onSetGithubRepo: (String) -> Unit = {},
    onClearUpdateState: () -> Unit = {}
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

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Modo de diagnóstico de conexão (debug)", fontWeight = FontWeight.Bold)
                    Text("Erros completos e testes de conexão.",
                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Switch(checked = state.connectionDiagnosticsEnabled,
                    onCheckedChange = onConnectionDiagnosticsToggled,
                    modifier = Modifier.testTag("connection_diagnostics_switch"))
            }
        }
        Spacer(Modifier.height(16.dp))

        com.example.ui.components.HomeSectionsEditor(state.homeSections, homeSources, onHomeSectionsChanged)
        Spacer(Modifier.height(16.dp))

        // Section: Theme & Appearance Preferences (Persisted via DataStore)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("theme_preferences_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        tint = PrimaryPurple,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tema e aparência",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = if (state.connectionDiagnosticsEnabled) "Preferência salva via Jetpack Preferences DataStore." else "Escolha o tema do aplicativo.",
                            fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
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
                Row(verticalAlignment = Alignment.Top) {
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
                            text = if (state.connectionDiagnosticsEnabled) "Estilo salvo no DataStore." else "Escolha como navegar pelas páginas.",
                            fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
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

                Row(verticalAlignment = Alignment.Top) {
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
                            text = "Escolha a orientação da tela.",
                            fontSize = 11.sp,
                            maxLines = if (state.connectionDiagnosticsEnabled) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis,
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
                            text = "Oculta as barras do sistema ao ler.",
                            fontSize = 11.sp,
                            maxLines = if (state.connectionDiagnosticsEnabled) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis,
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
                            maxLines = if (state.connectionDiagnosticsEnabled) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis,
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
                Row(verticalAlignment = Alignment.Top) {
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
                            maxLines = if (state.connectionDiagnosticsEnabled) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis,
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
                            text = "Inverte as cores das páginas.",
                            fontSize = 11.sp,
                            maxLines = if (state.connectionDiagnosticsEnabled) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis,
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
                            text = "Pré-carregamento de páginas",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Prepara as próximas páginas para a leitura.",
                            fontSize = 11.sp,
                            maxLines = if (state.connectionDiagnosticsEnabled) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis,
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
                Row(verticalAlignment = Alignment.Top) {
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
                            text = "Reduza o consumo durante a leitura.",
                            fontSize = 11.sp,
                            maxLines = if (state.connectionDiagnosticsEnabled) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis,
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
                            text = "Economia de bateria",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Text(
                            text = if (state.connectionDiagnosticsEnabled) "OLED, RGB_565 e menos pré-downloads." else "Reduz brilho e pré-carregamento.",
                            fontSize = 11.sp,
                            maxLines = if (state.connectionDiagnosticsEnabled) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis,
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
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
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
                            text = "Sincroniza somente quando houver Wi-Fi.",
                            fontSize = 11.sp,
                            maxLines = if (state.connectionDiagnosticsEnabled) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis,
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
                        text = if (state.connectionDiagnosticsEnabled) "OLED, RGB_565 e WorkManager reduzem consumo." else "Ajustes para reduzir o consumo durante a leitura.",
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
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = PrimaryPurple,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Armazenamento & Cache de Páginas",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Controle o espaço interno utilizado pelas páginas, capas e downloads.",
                            fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Detalhamento de Armazenamento
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cache de Páginas (Online)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = state.cacheSizeFormatted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = PrimaryPurple
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Capítulos Baixados",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = state.downloadsSizeFormatted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = PrimaryPurple
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Total Ocupado",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = state.totalStorageFormatted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = AccentGold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Configuração: Auto-limpeza após 3 dias
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-limpeza após 3 dias",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Remove automaticamente páginas e capítulos lidos sem abertura há mais de 3 dias, evitando encher o celular.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = state.autoCleanCacheEnabled,
                        onCheckedChange = { onAutoCleanCacheToggled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PrimaryPurple
                        ),
                        modifier = Modifier.testTag("auto_clean_cache_switch")
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Configuração: Preservar favoritos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Preservar downloads de Favoritos",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Nunca apaga capítulos baixados de mangás marcados com coração.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = state.keepFavoritesDownloads,
                        onCheckedChange = { onKeepFavoritesDownloadsToggled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PrimaryPurple
                        ),
                        modifier = Modifier.testTag("keep_favorites_downloads_switch")
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Botões de Ação
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            onClearCacheClicked()
                            Toast.makeText(context, "Cache de páginas liberado com sucesso!", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("clear_cache_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Limpar Cache", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            onClearOldDownloadsAndCacheClicked()
                            Toast.makeText(context, "Dados com mais de 3 dias limpos com sucesso!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("clear_old_cache_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Limpar >3 dias", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.Cloud,
                        contentDescription = null,
                        tint = PrimaryPurple,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sincronização na nuvem",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Mantenha favoritos e histórico em tempo real.",
                            fontSize = 11.sp,
                            maxLines = if (state.connectionDiagnosticsEnabled) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis,
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
                                text = "Sincronizando seus dados…",
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
                                    text = if (state.connectionDiagnosticsEnabled) "Última sincronização às $timeStr • User: ${syncState.cloudUserId.take(10)}..." else "Última sincronização às $timeStr",
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
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
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
                            maxLines = if (state.connectionDiagnosticsEnabled) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis,
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
                Row(verticalAlignment = Alignment.Top) {
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
                            maxLines = if (state.connectionDiagnosticsEnabled) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis,
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
                                text = if (state.connectionDiagnosticsEnabled) "Conta conectada via GraphQL" else "Conta conectada",
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
                                maxLines = if (state.connectionDiagnosticsEnabled) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis,
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
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
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

        if (state.connectionDiagnosticsEnabled) {
        // Section 2: Dynamic JSON Scraper Rules Engine
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
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
                    maxLines = if (state.connectionDiagnosticsEnabled) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis,
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
                Row(verticalAlignment = Alignment.Top) {
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
                    maxLines = if (state.connectionDiagnosticsEnabled) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section: Atualização e Releases via GitHub
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("github_update_card")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = PrimaryPurple,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Atualização via GitHub",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Versão atual instalada: v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Repositório GitHub configurável
                var isEditingRepo by remember { mutableStateOf(false) }
                var repoInput by remember(state.githubRepo) { mutableStateOf(state.githubRepo) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Repositório GitHub:",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = state.githubRepo,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrimaryPurple
                            )
                        }
                        IconButton(onClick = { isEditingRepo = !isEditingRepo }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Alterar repositório",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (isEditingRepo) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = repoInput,
                            onValueChange = { repoInput = it },
                            placeholder = { Text("usuario/repositorio", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                Button(
                                    onClick = {
                                        onSetGithubRepo(repoInput)
                                        isEditingRepo = false
                                        Toast.makeText(context, "Repositório salvo!", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple)
                                ) {
                                    Text("Salvar", fontSize = 11.sp)
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Estado da verificação
                when (val update = state.updateUiState) {
                    is UpdateUiState.Idle -> { }
                    is UpdateUiState.Checking -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PrimaryPurple.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = PrimaryPurple,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Consultando últimos releases no GitHub...",
                                fontSize = 12.sp,
                                color = PrimaryPurple
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    is UpdateUiState.UpToDate -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF4CAF50).copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Você já está na versão mais recente (v${BuildConfig.VERSION_NAME})!",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF4CAF50)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    is UpdateUiState.Error -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CrimsonRed.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = CrimsonRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = update.message,
                                fontSize = 12.sp,
                                color = CrimsonRed
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    is UpdateUiState.Available -> {
                        val release = update.info
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF02A9FF).copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        tint = Color(0xFF02A9FF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Nova Versão: ${release.tagName}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF02A9FF)
                                    )
                                }
                                if (release.publishedAt.isNotBlank()) {
                                    Text(
                                        text = release.publishedAt,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (release.changelog.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = release.changelog.take(300),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (release.apkDownloadUrl != null) {
                                    Button(
                                        onClick = {
                                            com.example.data.updater.AppUpdateManager.downloadApk(
                                                context = context,
                                                downloadUrl = release.apkDownloadUrl,
                                                fileName = release.apkName ?: "MangaReader-${release.tagName}.apk"
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF02A9FF)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Baixar APK", fontSize = 12.sp)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        com.example.data.updater.AppUpdateManager.openReleasesPage(context, release.htmlUrl)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Ver no GitHub", fontSize = 12.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Botão de checar atualizações
                Button(
                    onClick = { onCheckForUpdates(state.githubRepo) },
                    enabled = state.updateUiState !is UpdateUiState.Checking,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryPurple),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("check_github_updates_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verificar Atualizações no GitHub", fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "A cada novo push ou release gerado no GitHub, você pode atualizar o APK diretamente por aqui.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                maxLines = if (state.connectionDiagnosticsEnabled) Int.MAX_VALUE else 1, overflow = TextOverflow.Ellipsis,
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
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
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
