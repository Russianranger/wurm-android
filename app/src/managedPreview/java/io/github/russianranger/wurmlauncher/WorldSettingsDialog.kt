package io.github.russianranger.wurmlauncher

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.widget.*

object WorldSettingsDialog {
    private val store=WorldSettingsStore(::AndroidWorldSettingsDatabase,ManagedSession::log)
    fun show(activity: Activity, world: String) {
        val app=activity.applicationContext
        fun message(text: String) { if (!activity.isDestroyed) Toast.makeText(activity,text,Toast.LENGTH_LONG).show() }
        val accepted=ManagedSession.mutate(app,"Reading world settings") { workspace ->
            val result=runCatching {
                check(!workspace.recoveryRequired.exists()) { "Recover the interrupted server before editing settings." }
                val installed=requireNotNull(workspace.imports.current()) { "Import a server runtime first." }
                require(world in installed.worlds)
                store.read(workspace.ensureWorking { ManagedSession.status("Preparing",it) },world)
            }
            activity.runOnUiThread {
                if (!activity.isFinishing && !activity.isDestroyed) result.fold({ render(activity,it) }, { message(it.message ?: "Settings unavailable") })
            }
        }
        if (!accepted) message("Stop the server and wait for the active operation to finish first.")
    }
    private fun render(activity: Activity, before: WorldSettingsStore.Snapshot) {
        val form=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(24,12,24,12) }
        fun label(value: String) { form.addView(TextView(activity).apply { text=value }) }
        label("World: ${before.world} · server ${before.server}\nApplies at next server start. A database backup is saved before changes. Worlds using the same database share these settings.\nDatabase: ${before.database.relativeTo(before.runtime)}")
        val fields=linkedMapOf<String,()->String>()
        WorldSettings.options.forEach { option ->
            val raw=before.values[option.column] ?: return@forEach
            if (option.boolean) {
                val box=CheckBox(activity).apply { text=option.label; isChecked=raw.toDoubleOrNull()!=0.0; form.addView(this) }
                fields[option.column]={ if (box.isChecked) "1" else "0" }
            } else {
                label("\n${option.label} · ${option.range}")
                val input=EditText(activity).apply {
                    inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    setText(option.display(raw)); form.addView(this)
                }
                fields[option.column]={ input.text.toString().trim() }
            }
            label(option.help)
        }
        val dialog=AlertDialog.Builder(activity).setTitle("World gameplay settings")
            .setView(ScrollView(activity).apply { addView(form) }).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val edits=fields.mapValues { it.value() }
            val invalid=runCatching { edits.forEach { (key,value) ->
                val option=WorldSettings.options.single { it.column==key }
                if (value!=option.display(before.values.getValue(key))) option.value(value)
            } }.exceptionOrNull()
            if (invalid!=null) { Toast.makeText(activity,invalid.message,Toast.LENGTH_LONG).show(); return@setOnClickListener }
            val accepted=ManagedSession.mutate(activity.applicationContext,"Saving world settings") { workspace ->
                val result=runCatching {
                    check(!workspace.recoveryRequired.exists() && workspace.working()?.canonicalFile==before.runtime) { "Working world changed. Reopen settings." }
                    store.save(before,edits)
                }
                activity.runOnUiThread {
                    if (!activity.isDestroyed) {
                        Toast.makeText(activity,result.fold({ if (it==0) "No changes." else "$it settings saved. Applies at next server start." },{ it.message ?: "Save failed" }),Toast.LENGTH_LONG).show()
                        if (result.isSuccess) dialog.dismiss()
                        else dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=true
                    }
                }
                result.exceptionOrNull()?.let { throw it }
            }
            if (accepted) dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false
            else Toast.makeText(activity,"Stop the active operation first.",Toast.LENGTH_LONG).show()
        } }
        dialog.show()
    }
}
