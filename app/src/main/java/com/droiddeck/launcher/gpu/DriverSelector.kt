package com.droiddeck.launcher.gpu

/**
 * The user-facing Mali driver choice: MaliBridge (default) or our open driver ("Mali-open" zip).
 * Pure so the rules can be unit-tested without a device. The choice is persisted as the existing
 * prefs (`linuxDriver` = id of an imported Mali-open driver, or not; `vkBridge`), so DeviceSupport,
 * the session and the driver banner keep working unchanged.
 */
object DriverSelector {
    enum class Choice(val wire: String) {
        MALIBRIDGE("malibridge"), MALI_OPEN("mali-open");
        companion object { fun parse(s: String?): Choice? = entries.firstOrNull { it.wire == s } }
    }

    data class Driver(val id: String, val name: String)

    /** The same test as LinuxVulkanDriverManager.isMaliOpen and the session script (meta.json name starts with "Mali-open"). */
    fun isMaliOpen(d: Driver) = d.name.startsWith("Mali-open")

    /** The newest import wins (ids sort by name; uniqueId appends -2, -3 ... to repeats). */
    fun pickMaliOpen(installed: List<Driver>): Driver? = installed.filter(::isMaliOpen).maxByOrNull { it.id }

    /** MaliBridge is the default: anything but a selected Mali-open driver means MaliBridge. */
    fun active(linuxDriverPref: String, installed: List<Driver>): Choice =
        if (installed.any { it.id == linuxDriverPref && isMaliOpen(it) }) Choice.MALI_OPEN else Choice.MALIBRIDGE

    /** What to write: the linuxDriver pref, or an error that must be shown instead of silently falling back. */
    data class Plan(val linuxDriver: String?, val error: String?)

    const val NO_MALI_OPEN = "No Mali-open driver is imported. Import a Mali-open*.zip first (malideckctl import-driver <zip>, or Components > GPU drivers > Import .zip); MaliBridge stays selected."

    fun plan(choice: Choice, linuxDriverPref: String, installed: List<Driver>): Plan = when (choice) {
        Choice.MALI_OPEN -> pickMaliOpen(installed)?.let { Plan(it.id, null) } ?: Plan(null, NO_MALI_OPEN)
        // Only a Mali-open selection is cleared; a PanVK choice of the user is left alone.
        Choice.MALIBRIDGE -> Plan(if (installed.any { it.id == linuxDriverPref && isMaliOpen(it) }) "" else null, null)
    }
}
