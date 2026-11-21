@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.flabuis.tgmusic.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flabuis.tgmusic.data.AppTheme
import com.flabuis.tgmusic.data.Downloads
import com.flabuis.tgmusic.data.Settings
import com.flabuis.tgmusic.data.UpdateState
import com.flabuis.tgmusic.data.Updater
import com.flabuis.tgmusic.BuildConfig
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val theme by Settings.theme.collectAsState()
    var cacheSize by remember { mutableStateOf<Long?>(null) }
    var clearing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(clearing) {
        if (!clearing) cacheSize = try { Downloads.cacheSize() } catch (e: Exception) { null }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад")
            }
            Text("Настройки", style = MaterialTheme.typography.titleLargeEmphasized)
        }

        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
            Section("Тема") {
                AppTheme.entries.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable { Settings.setTheme(t) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = theme == t, onClick = { Settings.setTheme(t) })
                        Spacer(Modifier.width(8.dp))
                        Text(t.title, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            val crossfade by Settings.crossfadeSec.collectAsState()
            Section("Кроссфейд") {
                Text(
                    if (crossfade == 0) "Выключен: треки идут друг за другом"
                    else "Конец трека плавно перетекает в следующий за $crossfade сек",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Slider(
                    value = crossfade.toFloat(),
                    onValueChange = { Settings.setCrossfade(it.roundToInt()) },
                    valueRange = 0f..12f,
                    steps = 11,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            val live by Settings.liveBackground.collectAsState()
            Section("Живой фон") {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { Settings.setLiveBackground(!live) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Фон плеера, текста и режима ожидания двигается и пульсирует под басы. " +
                            "Цвета берутся из обложки, а в режиме ожидания всё плавно смещается, чтобы экран не выгорал.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = live, onCheckedChange = { Settings.setLiveBackground(it) })
                }
            }

            Spacer(Modifier.height(16.dp))

            val update by Updater.state.collectAsState()
            Section("Обновления") {
                Text(
                    "Установлена версия ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Text(
                    when (val u = update) {
                        UpdateState.Checking -> "Проверяю…"
                        UpdateState.UpToDate -> "Это последняя версия"
                        is UpdateState.Available -> "Есть новая версия ${u.version}, она на главном экране"
                        is UpdateState.Downloading -> "Скачиваю ${u.version}… ${(u.progress * 100).toInt()}%"
                        is UpdateState.Ready -> "Версия ${u.version} скачана"
                        is UpdateState.Failed -> u.message
                        UpdateState.Idle -> "Приложение само проверяет GitHub при запуске"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { scope.launch { Updater.check() } },
                    enabled = update != UpdateState.Checking && update !is UpdateState.Downloading,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp),
                ) { Text("Проверить обновления") }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(16.dp))

            Section("Офлайн") {
                Text(
                    "Каждый прослушанный трек сохраняется на телефон. В следующий раз он играет " +
                        "без интернета. Скачанные треки отмечены значком в списке.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Занято: " + (cacheSize?.let { formatSize(it) } ?: "…"),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        clearing = true
                        scope.launch {
                            try { Downloads.clear() } catch (_: Exception) { }
                            clearing = false
                        }
                    },
                    enabled = !clearing,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp),
                ) {
                    if (clearing) LoadingIndicator(Modifier.size(28.dp))
                    else Text("Удалить скачанное")
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            content()
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f ГБ".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.0f МБ".format(bytes / (1L shl 20).toDouble())
    else -> "%.0f КБ".format(bytes / 1024.0)
}
