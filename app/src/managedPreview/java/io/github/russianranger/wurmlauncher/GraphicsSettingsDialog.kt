package io.github.russianranger.wurmlauncher

import android.app.Activity
import android.app.AlertDialog
import android.widget.*
import android.view.View
import android.text.Editable
import android.text.TextWatcher

/** Android controls for inspected dynamic graphics options and the owned viewer. */
object GraphicsSettingsDialog {
    fun show(activity: Activity, live: Boolean = false): AlertDialog {
        val prefs = activity.getSharedPreferences("client-settings", Activity.MODE_PRIVATE)
        val column = LauncherUi.column(activity)
        fun choice(label: String, names: List<String>, selected: Int, parent: LinearLayout=column): Spinner {
            parent.addView(TextView(activity).apply { text=label })
            return Spinner(activity).apply {
                adapter=ArrayAdapter(activity,android.R.layout.simple_spinner_dropdown_item,names)
                setSelection(selected.coerceIn(names.indices)); minimumHeight=LauncherUi.dp(activity,48); parent.addView(this)
            }
        }
        val preset=choice("Base graphics preset",listOf("Performance: shorter distance, fewer effects", "Imported game settings"),
            if (prefs.getString("graphics-preset","performance") == "imported") 1 else 0)
        val resolution=choice("Render resolution (after client restart)",
            listOf("800 × 480 — faster", "960 × 540", "1280 × 720 — HD"),
            GraphicsOptions.resolutions.indexOf(GraphicsOptions.resolution(prefs.getString("resolution",null))))
        val fps=choice("Frame target",listOf("30 FPS — smoother", "15 FPS — lower load"),
            if (prefs.getInt("frame-fps",30) == 15) 1 else 0)
        column.addView(TextView(activity).apply {
            text="Individual choices override the base preset. Settings marked restart apply on the next client launch; other settings apply during play. Higher resolution, longer distances and extra effects can reduce speed. Fullscreen and panel opacity are in the gear menu."
        })
        column.addView(TextView(activity).apply {
            text="Occlusion culling is disabled for this renderer to prevent nearby objects being incorrectly hidden. Distance settings still apply."
        })
        Button(activity).apply {
            text="Compatibility and unavailable options"; column.addView(this)
            setOnClickListener { AlertDialog.Builder(activity).setTitle("Renderer compatibility")
                .setMessage("Occlusion queries remain disabled to prevent object pop-in. Desktop multisample anti-aliasing and VSync are not connected to this offscreen viewer; use FXAA and Frame target.\n\nGround decoration density, sky detail, distant terrain and texture compression wait for restart. The client may disable compression when required extensions are missing. Fog-coordinate source has no implemented consumer.\n\nModern/deferred rendering requires desktop OpenGL 3.3, which this renderer does not provide. Low-level GL switches and clock/thread-priority workarounds stay managed by the compatibility runtime. Game brightness is available in this menu. Supersampling and effects can increase rendering cost substantially. Texture filtering is capped by the renderer's capabilities.")
                .setPositiveButton("Close",null).show() }
        }
        val categories=listOf("All","View distance","Terrain & vegetation","Lighting & shadows","Textures & display","Effects & animation","Advanced")
        val category=choice("Show options",categories,1)
        val search=EditText(activity).apply { hint="Search graphics options"; setSingleLine(); column.addView(this) }
        val reset=LauncherUi.button(column,"Use preset for visible options") {}
        fun group(field: String)=when(field) {
            "treeRenderingDistance","structureRenderingDistance","itemCreatureRenderingDistance","lod","enableLod","enableContributionCulling","contributionCullingStatic","renderDistant" -> "View distance"
            "prettyTrees","caveDetail","terrainDetail","megaTextureSize","tileTransitions","tileDecorations","skyDetail" -> "Terrain & vegetation"
            "shadowLevel","shadowMapSize","limitDynamicLights","maxDynamicLights","maxShaderLights","renderSunGlare","reflections","reflectionTextureSize" -> "Lighting & shadows"
            "anisotropicFilteringLevel","normalMapping","enableFontSmoothing","maxTextureSize","playerTextureSize","offscreenTextureSize","textureScalingHint","fovHorizontal","screenBrightness","resolutionScale" -> "Textures & display"
            "modelLoaderThreadCount","useCompressedTexture","useCompressedTextureS3TC" -> "Advanced"
            else -> "Effects & animation"
        }
        val rows=GraphicsOptions.options.map { option ->
            val row=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; column.addView(this) }
            val value=prefs.getInt("graphics-option-${option.field}",-1).takeIf(option::valid) ?: -1
            row to choice(option.label + if(option.restart) " · Restart required" else " · Live",listOf("Use base preset")+option.choices,if(value == -1) 0 else value-option.minimum+1,row)
        }
        val controls=rows.map { it.second }
        fun filter() { rows.forEachIndexed { i,(row,_) ->
            val option=GraphicsOptions.options[i]
            row.visibility=if((category.selectedItemPosition==0 || group(option.field)==category.selectedItem.toString()) &&
                (option.label+" "+option.field).contains(search.text.toString().trim(),ignoreCase=true)) View.VISIBLE else View.GONE
        } }
        category.onItemSelectedListener=object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?,view: View?,position: Int,id: Long) { filter() }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        search.addTextChangedListener(object: TextWatcher {
            override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) {}
            override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) { filter() }
            override fun afterTextChanged(s: Editable?) {}
        })
        reset.setOnClickListener { rows.filter { it.first.visibility==View.VISIBLE }.forEach { it.second.setSelection(0) } }
        val savedStatus=LauncherUi.label(column,"Changes are saved only when you tap Save.")
        column.removeView(savedStatus); column.addView(savedStatus,0)
        val main=android.os.Handler(android.os.Looper.getMainLooper())
        val dialog=AlertDialog.Builder(activity).setTitle("Graphics settings").setView(ScrollView(activity).apply { addView(column) })
            .setNegativeButton("Close",null).setPositiveButton("Save",null).create()
        var poll: Runnable?=null
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name=if (preset.selectedItemPosition == 0) "performance" else "imported"
                val size=GraphicsOptions.resolutions[resolution.selectedItemPosition]
                val rate=if (fps.selectedItemPosition == 0) 30 else 15
                val values=controls.mapIndexed { i, control ->
                    if (control.selectedItemPosition == 0) -1 else control.selectedItemPosition-1+GraphicsOptions.options[i].minimum
                }
                val edit=prefs.edit().putString("graphics-preset",name).putString("resolution",size).putInt("frame-fps",rate)
                GraphicsOptions.options.forEachIndexed { i, option -> edit.putInt("graphics-option-${option.field}",values[i]) }
                edit.apply()
                val before=ClientSession.graphicsAcknowledgment
                val requested=live && ClientSession.inputReady() && ClientSession.send("VISUAL ${GraphicsOptions.command(name,values)}") && ClientSession.send("FPS $rate")
                savedStatus.text=if(requested) "Saved · waiting for live application. Restart-only choices apply next launch." else "Saved · applies on next client launch."
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=!requested
                poll?.let(main::removeCallbacks)
                if(requested) {
                    val deadline=android.os.SystemClock.elapsedRealtime()+5000
                    poll=object: Runnable { override fun run() {
                        if(!dialog.isShowing) return
                        if(ClientSession.graphicsAcknowledgment!=before) {
                            savedStatus.text=ClientSession.graphicsNotice; dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=true
                        } else if(android.os.SystemClock.elapsedRealtime()>=deadline) {
                            savedStatus.text="Saved · live application not confirmed. Restart the client to apply saved choices."
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=true
                        } else main.postDelayed(this,100)
                    } }
                    main.post(poll!!)
                }
                Toast.makeText(activity,savedStatus.text,Toast.LENGTH_SHORT).show()
        } }
        // Check isShowing in the poll too: the viewer may install its own dismiss listener.
        dialog.setOnDismissListener { poll?.let(main::removeCallbacks) }
        dialog.show(); return dialog
    }
}
