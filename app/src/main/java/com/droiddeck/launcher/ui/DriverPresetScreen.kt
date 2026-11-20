package com.droiddeck.launcher.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.droiddeck.launcher.R
import com.droiddeck.launcher.gpu.DriverPreset

/**
 * The two-way driver preset: MaliBridge (known-good default) or mali-open (our own driver).
 * Nothing else is selectable; the preset is stored by DriverPreset and applied at the next session start.
 */
@Composable
internal fun DriverPresetBody(onImport: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        PanelHeading(stringResource(R.string.driver_preset_title))
        DriverPresetChoices(showImport = onImport != null, onImport = onImport)
    }
}

/** The same choice as a row for the Setup > GPU card. */
@Composable
fun DriverPresetRow() {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(stringResource(R.string.driver_preset_title), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        DriverPresetChoices(showImport = false, onImport = null)
    }
}

@Composable
private fun DriverPresetChoices(showImport: Boolean, onImport: (() -> Unit)?) {
    val ctx = LocalContext.current
    val installed = remember { DriverPreset.installedDrivers(ctx) }
    var active by remember { mutableStateOf(DriverPreset.active(ctx)) }
    var problem by remember { mutableStateOf<String?>(null) }
    val openInstalled = installed.any { com.droiddeck.launcher.gpu.DriverSelector.isMaliOpen(it) }
    fun pick(preset: DriverPreset.Preset) {
        val ok = DriverPreset.apply(ctx, preset)
        active = if (ok) preset else DriverPreset.Preset.MALIBRIDGE
        problem = if (!ok) ctx.getString(R.string.driver_preset_no_mali_open) else null
    }
    for (preset in DriverPreset.Preset.entries) {
        val title = stringResource(
            if (preset == DriverPreset.Preset.MALIBRIDGE) R.string.driver_preset_malibridge else R.string.driver_preset_mali_open,
        )
        val detail = if (preset == DriverPreset.Preset.MALIBRIDGE) stringResource(R.string.driver_preset_malibridge_detail)
        else if (openInstalled) stringResource(R.string.driver_preset_mali_open_detail)
        else stringResource(R.string.driver_preset_no_mali_open)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().clickable { pick(preset) }.padding(vertical = 6.dp),
        ) {
            RadioButton(selected = active == preset, onClick = { pick(preset) })
            Column {
                Text(title + if (active == preset) stringResource(R.string.driver_preset_active) else "")
                Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    problem?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
    Text(stringResource(R.string.driver_preset_restart), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (showImport && onImport != null && !openInstalled) {
        SecondaryButton(stringResource(R.string.driver_preset_import), compact = true, onClick = onImport)
    }
}
