@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.flabuis.tgmusic.ui

import androidx.compose.foundation.clickable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
fun SettingsScreen(onBack: () -> Unit, onOpenAnime: () -> Unit) {
    val theme by Settings.theme.collectAsState()
    var tab by remember { mutableStateOf(0) }
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
            // Two tabs: looks & everything else
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer).padding(4.dp),
            ) {
                listOf("Кастомизация", "Основные").forEachIndexed { i, label ->
                    val selected = tab == i
                    val bg by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, label = "tabBg",
                    )
                    val fg by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, label = "tabFg",
                    )
                    Box(
                        Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(24.dp))
                            .background(bg).clickable { tab = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, color = fg, style = MaterialTheme.typography.titleSmallEmphasized)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            if (tab == 0) {
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
                if (live) {
                    val bright by Settings.liveBrightness.collectAsState()
                    val idleBright by Settings.idleBrightness.collectAsState()
                    LabeledSlider("Яркость в приложении", bright, Settings::setLiveBrightness)
                    LabeledSlider("Яркость в режиме ожидания", idleBright, Settings::setIdleBrightness)
                }
            }

            Spacer(Modifier.height(16.dp))

            Spacer(Modifier.height(16.dp))

            val buzz by Settings.bassVibration.collectAsState()
            Section("Вибрация под басы") {
                Row(
                    Modifier.fillMaxWidth()
                        .clickable { Settings.setBassVibration(!buzz) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Телефон отдаёт в руку на каждый удар баса: чем сильнее бас, тем сильнее вибрация.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = buzz, onCheckedChange = { Settings.setBassVibration(it) })
                }
            }

            Spacer(Modifier.height(16.dp))

            val lyricsColor by Settings.lyricsColor.collectAsState()
            Section("Цвет текста песни") {
                Text(
                    "Текущая строка в тексте песни и в режиме ожидания",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    textColors.forEach { (argb, name) ->
                        val selected = lyricsColor == argb
                        val scale by animateFloatAsState(if (selected) 1.12f else 1f, spring(dampingRatio = 0.5f), label = "swatch")
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.size(52.dp).scale(scale)
                                    .clip(if (selected) MaterialShapes.Cookie9Sided.toShape() else CircleShape)
                                    .background(
                                        if (argb == 0) Brush.sweepGradient(listOf(Color(0xFFFF5A36), Color(0xFFFFD43B), Color(0xFF3BC9DB), Color(0xFFB197FC), Color(0xFFFF5A36)))
                                        else Brush.linearGradient(listOf(Color(argb), Color(argb)))
                                    )
                                    .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, if (selected) MaterialShapes.Cookie9Sided.toShape() else CircleShape)
                                    .clickable { Settings.setLyricsColor(argb) },
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(name, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            } else {
            Section("Аниме") {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Каталог и плеер аниме через бота @AniMetkai",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.tertiary) {
                        Text(
                            "BETA",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onOpenAnime,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp),
                ) { Text("Открыть аниме") }
                Spacer(Modifier.height(8.dp))
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

private val textColors = listOf(
    0xFFFFFFFF.toInt() to "Белый",
    0 to "Обложка",
    0xFFFFD6A5.toInt() to "Тёплый",
    0xFFA5D8FF.toInt() to "Голубой",
    0xFFFFB3D9.toInt() to "Розовый",
    0xFFB2F2BB.toInt() to "Мятный",
    0xFFFFEC99.toInt() to "Жёлтый",
    0xFFD0BFFF.toInt() to "Сирень",
)

@Composable
private fun LabeledSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text("${(value * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onChange, valueRange = 0.3f..1.8f)
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f ГБ".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.0f МБ".format(bytes / (1L shl 20).toDouble())
    else -> "%.0f КБ".format(bytes / 1024.0)
}
