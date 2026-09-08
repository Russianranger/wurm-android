package io.github.russianranger.wurmlauncher

import android.content.Context
import android.net.Uri
import java.io.File

/** One import per app process, survives Activity rotation; no Activity is retained. */
object ImportCoordinator {
    data class Snapshot(val busy: Boolean, val message: String, val installed: ManagedRuntimeStore.Installed?)
    @Volatile private var state = Snapshot(false, "No runtime imported.", null)
    private var loaded = false

    @Synchronized fun snapshot(context: Context): Snapshot {
        if (!loaded) {
            state = try {
                val installed = store(context).current()
                Snapshot(false, if (installed == null) "No runtime imported." else "Runtime imported. Embedded Java runtime is still required.", installed)
            } catch (e: Exception) {
                Snapshot(false, "Cannot read import: ${e.message}", null)
            }
            loaded = true
        }
        return state
    }

    @Synchronized fun start(context: Context, uri: Uri): Boolean {
        snapshot(context)
        if (state.busy) return false
        val app = context.applicationContext
        val previous = state.installed
        state = Snapshot(true, "Opening ZIP… Keep the app open until import completes.", previous)
        Thread({
            try {
                val poc = app.assets.open("wurm-arm64-poc.jar").use { it.readBytes() }
                val installed = requireNotNull(app.contentResolver.openInputStream(uri)) { "Cannot open selected ZIP." }.use { input ->
                    store(app).importZip(input, poc) { message -> state = Snapshot(true, message, previous) }
                }
                state = Snapshot(false, "Import complete. Server is stopped; embedded Java runtime is still required.", installed)
            } catch (e: Exception) {
                state = Snapshot(false, "Import failed: ${e.message ?: e.javaClass.simpleName}. Previous import retained.", previous)
            }
        }, "wurm-runtime-import").start()
        return true
    }

    private fun store(context: Context) = ManagedRuntimeStore(File(context.filesDir, "managed-server"))
}
