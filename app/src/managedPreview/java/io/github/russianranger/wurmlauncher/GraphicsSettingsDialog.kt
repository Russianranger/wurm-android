package io.github.russianranger.wurmlauncher

import android.app.Activity
import android.app.AlertDialog
import android.widget.*

/** Android controls for inspected dynamic graphics options and the owned viewer. */
object GraphicsSettingsDialog {
    fun show(activity: Activity, live: Boolean = false): AlertDialog {
        val prefs = activity.getSharedPreferences("client-settings", Activity.MODE_PRIVATE)
        val column = LinearLayout(activity).apply {
            orientation=LinearLayout.VERTICAL; setPadding(28,12,28,12)
        }
        fun choice(label: String, names: List<String>, selected: Int): Spinner {
            column.addView(TextView(activity).apply { text=label })
            return Spinner(activity).apply {
                adapter=ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,names)
                setSelection(selected.coerceIn(names.indices)); column.addView(this)
            }
        }
        val preset=choice("Base graphics preset",listOf("Performance: shorter distance, fewer effects", "Imported game settings"),
            if (prefs.getString("graphics-preset","performance") == "imported") 1 else 0)
        val resolution=choice("Render resolution (after client restart)",
            listOf("800 × 480 — faster", "960 × 540", "1280 × 720 — HD"),
            GraphicsOptions.resolutions.indexOf(prefs.getString("resolution","800x480")).coerceAtLeast(0))
        val fps=choice("Frame target",listOf("30 FPS — smoother", "15 FPS — lower load"),
            if (prefs.getInt("frame-fps",30) == 15) 1 else 0)
        column.addView(TextView(activity).apply {
            text="Individual choices override the base preset and apply during play. Higher resolution, longer distances and extra effects can reduce speed. Fullscreen and panel opacity are in the gear menu."
        })
        column.addView(TextView(activity).apply {
            text="Occlusion culling is disabled for this renderer to prevent nearby objects being incorrectly hidden. Distance settings still apply."
        })
        val reset=Button(activity).apply { text="Use preset values for all options"; column.addView(this) }
        val controls=GraphicsOptions.options.map { option ->
            val value=prefs.getInt("graphics-option-${option.field}",-1).takeIf(option::valid) ?: -1
            choice(option.label,listOf("Use base preset")+option.choices,if (value == -1) 0 else value-option.minimum+1)
        }
        reset.setOnClickListener { controls.forEach { it.setSelection(0) } }
        return AlertDialog.Builder(activity).setTitle("Graphics settings").setView(ScrollView(activity).apply { addView(column) })
            .setNegativeButton("Cancel",null).setPositiveButton("Save") { _, _ ->
                val name=if (preset.selectedItemPosition == 0) "performance" else "imported"
                val size=GraphicsOptions.resolutions[resolution.selectedItemPosition]
                val rate=if (fps.selectedItemPosition == 0) 30 else 15
                val values=controls.mapIndexed { i, control ->
                    if (control.selectedItemPosition == 0) -1 else control.selectedItemPosition-1+GraphicsOptions.options[i].minimum
                }
                val edit=prefs.edit().putString("graphics-preset",name).putString("resolution",size).putInt("frame-fps",rate)
                GraphicsOptions.options.forEachIndexed { i, option -> edit.putInt("graphics-option-${option.field}",values[i]) }
                edit.apply()
                val requested=live && ClientSession.inputReady() && ClientSession.send("VISUAL ${GraphicsOptions.command(name,values)}") && ClientSession.send("FPS $rate")
                Toast.makeText(activity,if (requested) "Graphics change requested. Resolution applies after restarting the client."
                    else "Graphics saved for the next client start.",Toast.LENGTH_LONG).show()
            }.show()
    }
}
