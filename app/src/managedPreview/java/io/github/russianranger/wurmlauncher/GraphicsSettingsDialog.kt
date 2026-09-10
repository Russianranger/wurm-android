package io.github.russianranger.wurmlauncher

import android.app.Activity
import android.app.AlertDialog
import android.widget.*

/** Android controls for the inspected graphics preset and owned viewer. */
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
                setSelection(selected); column.addView(this)
            }
        }
        val preset=choice("Graphics preset",listOf("Performance: shorter distance, fewer effects", "Imported game settings"),
            if (prefs.getString("graphics-preset","performance") == "imported") 1 else 0)
        val resolution=choice("Render resolution (after client restart)",listOf("800 × 480 — faster", "960 × 540 — sharper"),
            if (prefs.getString("resolution","800x480") == "960x540") 1 else 0)
        val fps=choice("Frame target",listOf("30 FPS — smoother", "15 FPS — lower load"),
            if (prefs.getInt("frame-fps",30) == 15) 1 else 0)
        column.addView(TextView(activity).apply {
            text="Performance reduces water detail, reflections, object distance, tree models, weather particles and sun glare. The frame target is not a guaranteed speed."
        })
        return AlertDialog.Builder(activity).setTitle("Graphics settings").setView(ScrollView(activity).apply { addView(column) })
            .setNegativeButton("Cancel",null).setPositiveButton("Save") { _, _ ->
                val name=if (preset.selectedItemPosition == 0) "performance" else "imported"
                val size=if (resolution.selectedItemPosition == 0) "800x480" else "960x540"
                val rate=if (fps.selectedItemPosition == 0) 30 else 15
                prefs.edit().putString("graphics-preset",name).putString("resolution",size).putInt("frame-fps",rate).apply()
                val requested=live && ClientSession.inputReady() && ClientSession.send("VISUAL $name") && ClientSession.send("FPS $rate")
                Toast.makeText(activity,if (requested) "Graphics change requested. Resolution applies after restarting the client."
                    else "Graphics saved for the next client start.",Toast.LENGTH_LONG).show()
            }.show()
    }
}
