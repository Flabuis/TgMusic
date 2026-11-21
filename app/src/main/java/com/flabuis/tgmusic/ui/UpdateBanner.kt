@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.flabuis.tgmusic.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.flabuis.tgmusic.data.UpdateState
import com.flabuis.tgmusic.data.Updater
import kotlinx.coroutines.launch

/** Card on the home screen when a new version is out on GitHub. */
@Composable
fun UpdateBanner() {
    val state by Updater.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val st = state
    val failedDownload = st is UpdateState.Failed && Updater.lastAvailable() != null
    if (st !is UpdateState.Available && st !is UpdateState.Downloading && st !is UpdateState.Ready && !failedDownload) return

    // "Allow installing from TgMusic" screen: when the user comes back with it allowed, install right away.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val ready = Updater.state.value as? UpdateState.Ready
        if (ready != null && Updater.canInstall(context)) Updater.install(context, ready.file)
    }
    val installNow: (UpdateState.Ready) -> Unit = { r ->
        if (Updater.canInstall(context)) Updater.install(context, r.file)
        else permissionLauncher.launch(Updater.permissionIntent(context))
    }
    // As soon as the download finishes, open the installer (from the visible screen).
    LaunchedEffect(st) {
        if (st is UpdateState.Ready) installNow(st)
    }

    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().animateContentSize(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(MaterialTheme.colorScheme.tertiary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Download, null, tint = MaterialTheme.colorScheme.onTertiary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    val version = when (st) {
                        is UpdateState.Available -> st.version
                        is UpdateState.Downloading -> st.version
                        is UpdateState.Ready -> st.version
                        else -> Updater.lastAvailable()?.version ?: ""
                    }
                    Text(
                        "Обновление $version",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    if (st is UpdateState.Available && st.notes.isNotBlank()) {
                        Text(
                            st.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            when (st) {
                is UpdateState.Available -> Row {
                    TextButton(onClick = { Updater.dismiss() }) { Text("Позже") }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { scope.launch { Updater.download(context.applicationContext, st) } },
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                    ) { Text("Обновить · ${st.size / (1024 * 1024)} МБ") }
                }
                is UpdateState.Downloading -> {
                    LinearWavyProgressIndicator(
                        progress = { st.progress },
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Скачиваю… ${(st.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
                is UpdateState.Ready -> {
                    if (!Updater.canInstall(context)) {
                        Text(
                            "Android попросит разрешить TgMusic устанавливать приложения: включи переключатель и вернись назад.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Button(
                        onClick = { installNow(st) },
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Установить") }
                }
                is UpdateState.Failed -> {
                    Text(
                        st.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row {
                        TextButton(onClick = { Updater.dismiss() }) { Text("Позже") }
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { Updater.lastAvailable()?.let { Updater.retry(it) } },
                            shapes = ButtonDefaults.shapes(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        ) { Text("Ещё раз") }
                    }
                }
                else -> {}
            }
        }
    }
}
