package com.droiddeck.launcher.gpu

import android.content.Context
import com.droiddeck.launcher.session.SessionPrefs

/**
 * The only user-facing Vulkan driver choice on a Mali device:
 *  - [Preset.MALIBRIDGE] (default, known-good): the driver is the phone's Mali through the vkBridge server
 *    (safe mode, Steam's UI on ANGLE-Vulkan with Vulkan compositing). The reference run is the Steam
 *    menu at 120 fps on the POCO X8 (docs/MENUBENCH.md) with Megabonk at 60-90 fps (owner's target,
 *    not re-measured here).
 *  - [Preset.MALI_OPEN]: our own open Mali driver, imported as a "Mali-open*.zip"; it replaces the bridge
 *    and is selected by its ICD (`BL_VK_DRIVER`).
 *
 * Persisted as `driverPreset` (SessionPrefs). Applying a preset writes the lower-level prefs the session
 * already reads (`linuxDriver`, `vkBridge`, `vkBridgeSafe`, `gpuDriverMode`), so
 * `SessionService.addClientEnvironment`/`startVkBridge` and `DeviceSupport` stay unchanged.
 * PanVK, Turnip and the auto-trial remain in the code for the agent/reports, but are not reachable
 * from the UI and are cleared whenever MaliBridge is applied.
 */
object DriverPreset {
    enum class Preset(val wire: String) {
        MALIBRIDGE("malibridge"), MALI_OPEN("mali-open");
        companion object { fun parse(s: String?): Preset? = entries.firstOrNull { it.wire == s } }
    }

    const val KEY = "driverPreset"

    /** The stored preset, or null on an install that predates it (see [active]). */
    fun stored(context: Context): Preset? = Preset.parse(SessionPrefs.driverPreset(context))

    /** The preset in force; migrates and stores it on first use after an update. */
    @Synchronized
    fun active(context: Context): Preset =
        stored(context) ?: migrate(context).also { apply(context, it) }

    /**
     * Migration for an existing install: a selected Mali-open driver keeps its preset, every other old
     * choice (PanVK, Turnip, auto-trial result) lands on MaliBridge, the sane default.
     */
    internal fun migrate(context: Context): Preset =
        if (maliOpenSelected(context, installedDrivers(context))) Preset.MALI_OPEN else Preset.MALIBRIDGE

    /**
     * Writes the preset and the prefs behind it. Returns false when Mali-open was asked for but no
     * Mali-open driver is imported: the preset then stays on MaliBridge instead of breaking the session.
     */
    fun apply(context: Context, preset: Preset): Boolean {
        val open = DriverSelector.pickMaliOpen(installedDrivers(context))
        val target = if (preset == Preset.MALI_OPEN && open == null) Preset.MALIBRIDGE else preset
        SessionPrefs.setDriverPreset(context, target.wire)
        // MaliBridge means "no imported Linux driver": this also clears a leftover PanVK/Turnip pick.
        SessionPrefs.setLinuxDriver(context, if (target == Preset.MALI_OPEN) open!!.id else "")
        SessionPrefs.setVkBridge(context, true)   // ignored by DeviceSupport while Mali-open is selected
        SessionPrefs.setVkBridgeSafe(context, true)
        SessionPrefs.setGpuDriverMode(context, SessionPrefs.GPU_DRIVERS_MANUAL)
        return target == preset
    }

    /**
     * Makes the preset authoritative at session start without touching the tuning prefs (safe mode,
     * Steam compositing): re-resolves the Mali-open driver so an uninstalled one falls back to MaliBridge.
     */
    fun enforce(context: Context) {
        val want = active(context)
        val open = DriverSelector.pickMaliOpen(installedDrivers(context))
        if (want == Preset.MALI_OPEN && open != null) {
            if (SessionPrefs.linuxDriver(context) != open.id) SessionPrefs.setLinuxDriver(context, open.id)
            return
        }
        if (want == Preset.MALI_OPEN) SessionPrefs.setDriverPreset(context, Preset.MALIBRIDGE.wire)
        if (SessionPrefs.linuxDriver(context).isNotEmpty()) SessionPrefs.setLinuxDriver(context, "")
    }

    fun installedDrivers(context: Context): List<DriverSelector.Driver> {
        val manager = LinuxVulkanDriverManager(context)
        return manager.enumerateInstalledDrivers().map { DriverSelector.Driver(it, manager.getDriverName(it)) }
    }

    private fun maliOpenSelected(context: Context, installed: List<DriverSelector.Driver>) =
        installed.any { it.id == SessionPrefs.linuxDriver(context) && DriverSelector.isMaliOpen(it) }
}
