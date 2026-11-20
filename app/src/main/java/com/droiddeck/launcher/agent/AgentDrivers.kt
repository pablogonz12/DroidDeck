package com.droiddeck.launcher.agent

import android.content.Context
import android.net.Uri
import com.droiddeck.launcher.gpu.DriverSelector
import com.droiddeck.launcher.gpu.LinuxVulkanDriverManager
import com.droiddeck.launcher.session.SessionPrefs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files

/** `droiddeckctl driver status|select|import`: the Mali driver choice and the zip importer, behind Debugging tools. */
object AgentDrivers {
    private val ZIP_NAME = Regex("[A-Za-z0-9._-]{1,64}\\.zip")

    private fun installed(m: LinuxVulkanDriverManager) =
        m.enumerateInstalledDrivers().map { DriverSelector.Driver(it, m.getDriverName(it)) }

    private fun status(context: Context, m: LinuxVulkanDriverManager): JSONObject {
        val list = installed(m)
        return JSONObject()
            .put("active", DriverSelector.active(SessionPrefs.linuxDriver(context), list).wire)
            .put("linuxDriver", SessionPrefs.linuxDriver(context))
            .put("maliOpen", JSONArray(list.filter(DriverSelector::isMaliOpen).map { it.id }))
            .put("installed", JSONArray(list.map { JSONObject().put("id", it.id).put("name", it.name) }))
            .put("restartRequired", true)
    }

    fun handle(context: Context, request: JSONObject): JSONObject {
        AgentAccess.requireCommands(context)
        val m = LinuxVulkanDriverManager(context)
        when (val op = request.optString("op", "status")) {
            "status" -> {}
            "select" -> select(context, m, request.optString("choice"))
            "import" -> {
                val name = request.optString("name")
                if (!ZIP_NAME.matches(name)) throw AgentException("INVALID_NAME", "Driver zips are simple file names ending in .zip")
                val source = File(AgentEnv.inbox(context), name)
                if (Files.isSymbolicLink(source.toPath()) || !source.isFile) throw AgentException("NO_SUCH_FILE", "Push the zip to ${source.path} first")
                val id = try { m.installDriver(Uri.fromFile(source), name) } catch (e: Exception) {
                    throw AgentException("IMPORT_FAILED", e.message ?: "The driver zip was rejected")
                } finally { source.delete() }
                val maliOpen = DriverSelector.isMaliOpen(DriverSelector.Driver(id, m.getDriverName(id)))
                if (request.optBoolean("select")) {
                    if (!maliOpen) throw AgentException("NOT_MALI_OPEN", "Imported $id, but its name does not start with Mali-open, so it was not selected")
                    select(context, m, DriverSelector.Choice.MALI_OPEN.wire)
                }
                return status(context, m).put("ok", true).put("command", "driver").put("imported", id).put("importedIsMaliOpen", maliOpen)
            }
            else -> throw AgentException("INVALID_REQUEST", "driver op '$op' is not status, select or import")
        }
        return status(context, m).put("ok", true).put("command", "driver")
    }

    private fun select(context: Context, m: LinuxVulkanDriverManager, wire: String) {
        val choice = DriverSelector.Choice.parse(wire) ?: throw AgentException("INVALID_REQUEST", "choice is malibridge or mali-open")
        val plan = DriverSelector.plan(choice, SessionPrefs.linuxDriver(context), installed(m))
        plan.error?.let { throw AgentException("NO_MALI_OPEN", it) }
        plan.linuxDriver?.let { SessionPrefs.setLinuxDriver(context, it) }
        // MaliBridge is the default: it is on again whenever Mali-open is not selected (DeviceSupport.vkBridge turns it off for Mali-open).
        SessionPrefs.setVkBridge(context, true)
    }
}
