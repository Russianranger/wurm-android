package io.github.russianranger.wurmlauncher

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.*
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Executors

/** Uses the live game's catalog and binding source; never guesses profile paths on Android. */
class GameKeybindsActivity : Activity() {
    private val main=Handler(Looper.getMainLooper())
    private val worker=Executors.newSingleThreadExecutor()
    private lateinit var column: LinearLayout
    private lateinit var status: TextView
    private var waiting=false
    private var catalog: GameKeybinds.Catalog?=null
    private var selected: GameKeybinds.Action?=null
    private var selectedCommand: String?=null
    private var draft: EditText?=null
    private val drafts=mutableMapOf<String,String>()
    private val controls=mutableListOf<View>()
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        selectedCommand=state?.getString("selected")
        state?.getBundle("drafts")?.let { saved -> saved.keySet().forEach { key -> drafts[key]=saved.getString(key,"") } }
        renderShell(); request()
    }
    private fun remember() { selected?.let { action -> draft?.let {
        val value=it.text.toString()
        if (value==action.keys) drafts.remove(action.command) else drafts[action.command]=value
    } } }
    override fun onSaveInstanceState(out: Bundle) {
        remember(); out.putString("selected",selected?.command ?: selectedCommand)
        out.putBundle("drafts",Bundle().apply { drafts.forEach { (key,value) -> putString(key,value) } })
        super.onSaveInstanceState(out)
    }
    override fun onDestroy() { worker.shutdownNow(); main.removeCallbacksAndMessages(null); super.onDestroy() }
    private fun label(text: String) = TextView(this).apply { this.text=text; column.addView(this) }
    private fun button(text: String,action: ()->Unit)=Button(this).apply {
        this.text=text; setOnClickListener { action() }; column.addView(this); controls.add(this)
    }
    private fun renderShell() {
        controls.clear()
        column=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,16,24,16) }
        setContentView(ScrollView(this).apply { addView(column) })
        label("Game keybindings").textSize=22f
        label("Edit Wurm's keyboard and mouse bindings. Save one action at a time. Controller mappings choose which keys your handheld sends; those mappings are separate.")
        status=label("Loading the running game's bindings…")
        button("Refresh bindings") { remember(); request() }
        button("Return to game") { finish() }
    }
    private fun render(data: GameKeybinds.Catalog) {
        remember(); selectedCommand=selected?.command ?: selectedCommand
        selected=null; draft=null; renderShell(); catalog=data
        status.text=data.notice.ifEmpty { "${data.actions.size} actions loaded. Changes apply when saved." }
        if (!data.editable) label("This file contains custom console commands or unsaved changes. It is preserved; editing is disabled.")
        val categories=data.actions.map { it.category }.distinct()
        label("Category")
        val category=Spinner(this).apply { adapter=ArrayAdapter(this@GameKeybindsActivity,android.R.layout.simple_spinner_dropdown_item,categories); column.addView(this); controls.add(this) }
        label("Action")
        val actions=Spinner(this).also { column.addView(it); controls.add(it) }
        val commandLabel=label("")
        label("Assigned keys (comma-separated; leave empty to unbind)")
        val text=MultiAutoCompleteTextView(this).apply {
            setSingleLine(false); minLines=1
            inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setTokenizer(MultiAutoCompleteTextView.CommaTokenizer()); threshold=1
            setAdapter(ArrayAdapter(this@GameKeybindsActivity,android.R.layout.simple_dropdown_item_1line,data.choices))
            column.addView(this); isEnabled=data.editable; controls.add(this)
        }
        draft=text
        label("Add a key without typing:")
        val key=Spinner(this).apply { adapter=ArrayAdapter(this@GameKeybindsActivity,android.R.layout.simple_spinner_dropdown_item,data.choices); column.addView(this); controls.add(this) }
        val modifiers=LinearLayout(this).also { column.addView(it) }
        val mods=listOf("ALT","CTRL","SHIFT").associateWith { name -> CheckBox(this).apply { this.text=name; modifiers.addView(this); controls.add(this) } }
        button("Add selected key") {
            val value=(mods.filterValues { it.isChecked }.keys+key.selectedItem.toString()).joinToString("+")
            text.setText((text.text.toString().trim().trimEnd(',').takeIf { it.isNotEmpty() }?.plus(", ") ?: "")+value)
        }.isEnabled=data.editable
        button("Clear this action") { text.setText("") }.isEnabled=data.editable
        button("Save this action") {
            val action=selected ?: return@button
            val id=UUID.randomUUID().toString()
            runCatching { GameKeybinds.command(id,data,action,text.text.toString()) }
                .onSuccess { wire -> request(id,wire,action.command) }
                .onFailure { status.text=it.message }
        }.isEnabled=data.editable
        var visible=emptyList<GameKeybinds.Action>()
        actions.onItemSelectedListener=object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?,view: View?,position: Int,id: Long) {
                remember(); selected=visible.getOrNull(position)
                selected?.let { action -> selectedCommand=action.command; commandLabel.text=action.command; text.setText(drafts[action.command] ?: action.keys) }
            }
        }
        category.onItemSelectedListener=object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?,view: View?,position: Int,id: Long) {
                remember(); selected=null
                visible=data.actions.filter { it.category==categories[position] }
                actions.adapter=ArrayAdapter(this@GameKeybindsActivity,android.R.layout.simple_spinner_dropdown_item,visible.map { it.label })
                actions.setSelection(visible.indexOfFirst { it.command==selectedCommand }.coerceAtLeast(0))
            }
        }
        val initial=data.actions.find { it.command==selectedCommand }?.category
        category.setSelection(categories.indexOf(initial).coerceAtLeast(0))
    }
    private fun request(id: String=UUID.randomUUID().toString(),wire: String="BINDS $id READ",savedAction: String?=null) {
        if (waiting) return
        if (!ClientSession.gameActive() || !ClientSession.send(wire)) { status.text="Start the client and wait for the world, then Refresh bindings."; return }
        remember(); waiting=true; controls.forEach { it.isEnabled=false }; status.text=if (savedAction==null) "Loading bindings…" else "Saving and applying…"
        val epoch=ClientSession.frameEpoch
        worker.execute {
            val result=runCatching {
                val deadline=SystemClock.elapsedRealtime()+15000
                while (!Thread.currentThread().isInterrupted) {
                    check(ClientSession.frameEpoch==epoch && ClientSession.gameActive()) { "The client session ended. Reopen bindings in the new session." }
                    val file=ClientSession.keybindReport(this)
                    if (file.isFile && file.length() in 1..524288) {
                        val p=Properties().apply { file.inputStream().use { load(it) } }
                        if (p.getProperty("request")==id) return@runCatching GameKeybinds.parse(p,id)
                    }
                    check(SystemClock.elapsedRealtime()<deadline) { "No confirmation yet. Refresh to check whether the change applied." }
                    Thread.sleep(100)
                }
                error("Editor closed")
            }
            main.post {
                if (!isDestroyed) {
                    waiting=false
                    result.fold({ data ->
                        if (savedAction!=null) { drafts.remove(savedAction); selected=null; draft=null }
                        render(data)
                    }, { failure ->
                        controls.forEach { it.isEnabled=true }; status.text=failure.message
                    })
                }
            }
        }
    }
}
