package com.anos.myscreenoff.ui

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon as TileIcon
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anos.myscreenoff.R
import com.anos.myscreenoff.data.ButtonPalette
import com.anos.myscreenoff.data.ButtonSettings
import com.anos.myscreenoff.service.ScreenOffService
import com.anos.myscreenoff.service.ServiceToggleTile
import kotlin.math.roundToInt

/** The app's only screen: service setup, a button preview and the appearance and behavior options. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // Re-checked on every resume, since the service is switched on in system settings and the
    // permission to switch it is granted over adb.
    var serviceEnabled by remember { mutableStateOf(ScreenOffService.isEnabled(context)) }
    var canToggle by remember { mutableStateOf(ScreenOffService.canToggle(context)) }
    LifecycleResumeEffect(Unit) {
        serviceEnabled = ScreenOffService.isEnabled(context)
        canToggle = ScreenOffService.canToggle(context)
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = viewModel::reset, enabled = settings != null) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_reset))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        val current = settings ?: return@Scaffold
        SettingsContent(
            settings = current,
            serviceEnabled = serviceEnabled,
            canToggle = canToggle,
            contentPadding = innerPadding,
            onPreview = viewModel::preview,
            onChange = viewModel::change,
            onCommit = viewModel::commit,
            onTurnOn = {
                if (ScreenOffService.setEnabled(context, true)) {
                    serviceEnabled = ScreenOffService.isEnabled(context)
                } else {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            },
            onAddTile = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                { requestAddTile(context) }
            } else {
                null
            },
        )
    }
}

@Composable
private fun SettingsContent(
    settings: ButtonSettings,
    serviceEnabled: Boolean,
    canToggle: Boolean,
    contentPadding: PaddingValues,
    onPreview: ((ButtonSettings) -> ButtonSettings) -> Unit,
    onChange: ((ButtonSettings) -> ButtonSettings) -> Unit,
    onCommit: () -> Unit,
    onTurnOn: () -> Unit,
    onAddTile: (() -> Unit)?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!serviceEnabled) SetupCard(canToggle, onTurnOn)

        PreviewCard(settings)

        if (serviceEnabled) {
            Text(
                text = stringResource(R.string.usage_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        SettingsSection(stringResource(R.string.section_appearance)) {
            SliderSetting(
                label = R.string.button_size,
                valueText = stringResource(R.string.value_dp, settings.sizeDp.roundToInt()),
                value = settings.sizeDp,
                valueRange = ButtonSettings.MIN_SIZE_DP..ButtonSettings.MAX_SIZE_DP,
                onValueChange = { value -> onPreview { it.copy(sizeDp = value.roundToInt().toFloat()) } },
                onValueChangeFinished = onCommit,
            )
            ColorSetting(R.string.button_color, ButtonPalette.colors, settings.color) { color ->
                onChange { it.copy(color = color) }
            }
            SliderSetting(
                label = R.string.button_opacity,
                valueText = percentText(settings.opacity),
                value = settings.opacity,
                valueRange = ButtonSettings.MIN_OPACITY..1f,
                onValueChange = { value -> onPreview { it.copy(opacity = value) } },
                onValueChangeFinished = onCommit,
            )
        }

        SettingsSection(stringResource(R.string.section_behavior)) {
            SwitchSetting(R.string.fade_when_idle, settings.fadeWhenIdle) { checked ->
                onChange { it.copy(fadeWhenIdle = checked) }
            }
            SliderSetting(
                label = R.string.idle_opacity,
                valueText = percentText(settings.idleOpacity),
                value = settings.idleOpacity,
                valueRange = ButtonSettings.MIN_IDLE_OPACITY..1f,
                enabled = settings.fadeWhenIdle,
                onValueChange = { value -> onPreview { it.copy(idleOpacity = value) } },
                onValueChangeFinished = onCommit,
            )
            SliderSetting(
                label = R.string.fade_delay,
                valueText = secondsText(settings.fadeDelayMs),
                value = settings.fadeDelayMs.toFloat(),
                valueRange = ButtonSettings.MIN_FADE_DELAY_MS.toFloat()..ButtonSettings.MAX_FADE_DELAY_MS.toFloat(),
                enabled = settings.fadeWhenIdle,
                onValueChange = { value -> onPreview { it.copy(fadeDelayMs = value.roundToStep(500)) } },
                onValueChangeFinished = onCommit,
            )
            SliderSetting(
                label = R.string.lock_delay,
                valueText = if (settings.lockDelayMs == 0) {
                    stringResource(R.string.value_none)
                } else {
                    secondsText(settings.lockDelayMs)
                },
                hint = stringResource(R.string.lock_delay_hint),
                value = settings.lockDelayMs.toFloat(),
                valueRange = 0f..ButtonSettings.MAX_LOCK_DELAY_MS.toFloat(),
                onValueChange = { value -> onPreview { it.copy(lockDelayMs = value.roundToStep(250)) } },
                onValueChangeFinished = onCommit,
            )
            SwitchSetting(R.string.snap_to_edge, settings.snapToEdge) { checked ->
                onChange { it.copy(snapToEdge = checked) }
            }
            SwitchSetting(R.string.haptic_feedback, settings.haptic) { checked ->
                onChange { it.copy(haptic = checked) }
            }
        }

        QuickToggleSection(canToggle, onAddTile)
    }
}

/**
 * Shown until the accessibility service is on, since nothing works without it. Turns it on directly
 * once the app may switch it, otherwise opens Accessibility settings.
 */
@Composable
private fun SetupCard(canToggle: Boolean, onTurnOn: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.titleMedium)
            Text(
                text = stringResource(R.string.setup_message, stringResource(R.string.app_name)),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(stringResource(R.string.setup_restricted_hint), style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = onTurnOn,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(52.dp),
            ) {
                Text(stringResource(if (canToggle) R.string.setup_turn_on else R.string.setup_open_settings))
            }
        }
    }
}

/** The Quick Settings tile that switches the service off around banking apps, and its one-time adb setup. */
@Composable
private fun QuickToggleSection(canToggle: Boolean, onAddTile: (() -> Unit)?) {
    val packageName = LocalContext.current.packageName
    SettingsSection(stringResource(R.string.section_quick_toggle)) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.quick_toggle_message, stringResource(R.string.app_name)),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (canToggle) {
                Text(
                    text = stringResource(R.string.quick_toggle_ready),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text(
                    text = stringResource(R.string.quick_toggle_setup),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer {
                    Text(
                        text = "adb shell pm grant $packageName ${Manifest.permission.WRITE_SECURE_SETTINGS}",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(12.dp),
                    )
                }
            }
            if (onAddTile != null) {
                OutlinedButton(onClick = onAddTile) {
                    Text(stringResource(R.string.quick_toggle_add))
                }
            }
        }
    }
}

/** Asks System UI to add the tile to Quick Settings, so the user need not find it in the editor. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun requestAddTile(context: Context) {
    context.getSystemService(StatusBarManager::class.java).requestAddTileService(
        ComponentName(context, ServiceToggleTile::class.java),
        context.getString(R.string.app_name),
        TileIcon.createWithResource(context, R.drawable.ic_lock),
        context.mainExecutor,
    ) { }
}

/** The button on a wallpaper-like backdrop, so its opacity is visible. */
@Composable
private fun PreviewCard(settings: ButtonSettings) {
    val color = Color(settings.color)
    val light = color.luminance() > 0.5f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PreviewHeight)
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(WallpaperColors)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(settings.sizeDp.dp)
                .alpha(settings.opacity)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, if (light) Color.Black.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.33f), CircleShape)
                .padding((settings.sizeDp * 0.26f).dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lock),
                contentDescription = null,
                tint = if (light) Color.Black else Color.White,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 8.dp), content = content)
        }
    }
}

@Composable
private fun ColorSetting(@StringRes label: Int, colors: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 10.dp),
        ) {
            items(colors) { color ->
                ColorSwatch(color, selected = color == selected, onClick = { onSelect(color) })
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Int, selected: Boolean, onClick: () -> Unit) {
    val swatch = Color(color)
    val hex = "#%06X".format(color and 0xFFFFFF)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(swatch)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape,
            )
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = hex },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = if (swatch.luminance() > 0.5f) Color.Black else Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun SliderSetting(
    @StringRes label: Int,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    enabled: Boolean = true,
    hint: String? = null,
) {
    val contentAlpha = if (enabled) 1f else 0.38f
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SwitchSetting(
    @StringRes label: Int,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun percentText(fraction: Float): String =
    stringResource(R.string.value_percent, (fraction * 100).roundToInt())

@Composable
private fun secondsText(milliseconds: Int): String =
    stringResource(R.string.value_seconds, milliseconds / 1000f)

private fun Float.roundToStep(step: Int): Int = (this / step).roundToInt() * step

/** Fits the largest button size with room to spare. */
private val PreviewHeight = 160.dp

private val WallpaperColors = listOf(Color(0xFF355C7D), Color(0xFF6C5B7B), Color(0xFFC06C84))
