package com.droiddeck.launcher.gpu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.droiddeck.launcher.R
import com.droiddeck.launcher.session.SessionPrefs

/**
 * The two-way Mali driver choice for the Modes / GPU screen: "MaliBridge (default)" and "Mali-open (experimental)",
 * the active one marked, persisted through DriverSelector's plan. Picking Mali-open with no Mali-open driver
 * imported shows the reason instead of silently staying on MaliBridge. Drop `DriverChoiceRow()` into the page.
 */
@Composable
fun DriverChoiceRow() {
    val context = LocalContext.current
    val manager = remember { LinuxVulkanDriverManager(context) }
    fun installed() = manager.enumerateInstalledDrivers().map { DriverSelector.Driver(it, manager.getDriverName(it)) }
    var active by remember { mutableStateOf(DriverSelector.active(SessionPrefs.linuxDriver(context), installed())) }
    var problem by remember { mutableStateOf<String?>(null) }
    fun pick(choice: DriverSelector.Choice) {
        val plan = DriverSelector.plan(choice, SessionPrefs.linuxDriver(context), installed())
        problem = plan.error
        if (plan.error != null) return
        plan.linuxDriver?.let { SessionPrefs.setLinuxDriver(context, it) }
        SessionPrefs.setVkBridge(context, true)
        active = choice
    }
    Column(Modifier.padding(8.dp)) {
        Text(stringResource(R.string.driver_choice_title))
        for ((choice, label) in listOf(
            DriverSelector.Choice.MALIBRIDGE to R.string.driver_choice_malibridge,
            DriverSelector.Choice.MALI_OPEN to R.string.driver_choice_mali_open,
        )) {
            Row(Modifier.clickable { pick(choice) }) {
                RadioButton(selected = active == choice, onClick = { pick(choice) })
                Text(stringResource(label) + if (active == choice) " - " + stringResource(R.string.driver_choice_active) else "")
            }
        }
        if (problem != null) Text(stringResource(R.string.driver_choice_no_mali_open))
        Text(stringResource(R.string.driver_choice_restart))
    }
}
