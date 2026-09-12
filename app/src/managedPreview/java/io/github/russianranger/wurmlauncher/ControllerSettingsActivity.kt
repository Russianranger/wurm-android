package io.github.russianranger.wurmlauncher

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.InputDevice
import android.widget.*

class ControllerSettingsActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); render(loadProfile()) }
    private fun loadProfile() = runCatching { ControllerProfile.load(ClientSession.profileFile(this)) }.getOrElse {
        ClientSession.log("[input] PROFILE_INVALID ${it.message}; defaults shown"); ControllerProfile()
    }
    private fun render(profile: ControllerProfile) {
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20,20,20,20) }
        setContentView(ScrollView(this).apply { addView(column) })
        fun label(text: String) { column.addView(TextView(this).apply { this.text = text }) }
        fun field(label: String, value: Float): EditText {
            label(label)
            return EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; setText(value.toString()); column.addView(this) }
        }
        label("Controller Settings · editable handheld profile")
        label("Detected: " + controllerNames())
        label("Changes apply when you return to the game or input test. Android may reserve the Home/Mode button.")
        val dead = field("Dead zone (0.05–0.8)", profile.deadZone)
        val threshold = field("Left-stick movement threshold (0.1–0.95; lower is more sensitive)", profile.moveThreshold)
        val speed = field("Right-stick mouse speed (50–2000 pixels/second)", profile.mouseSpeed)
        val invert = CheckBox(this).apply { text = "Invert mouse Y"; isChecked = profile.invertY; column.addView(this) }
        val choices = ControllerProfile.ACTIONS.keys.toList(); val values = ControllerProfile.ACTIONS.values.toList()
        val selectors = ControllerProfile.DEFAULTS.keys.associateWith { name ->
            label(name)
            Spinner(this).apply {
                adapter = ArrayAdapter(this@ControllerSettingsActivity, android.R.layout.simple_spinner_dropdown_item, choices)
                setSelection(values.indexOf(profile.bindings[name]).coerceAtLeast(0)); column.addView(this)
            }
        }
        fun button(text: String, action: () -> Unit) { column.addView(Button(this).apply { this.text = text; setOnClickListener { action() } }) }
        button("Save mappings") {
            runCatching {
                ControllerProfile(selectors.mapValues { values[it.value.selectedItemPosition] }, dead.text.toString().toFloat(),
                    threshold.text.toString().toFloat(), speed.text.toString().toFloat(), invert.isChecked).save(ClientSession.profileFile(this))
            }.onSuccess { ClientSession.log("[input] PROFILE_SAVED"); Toast.makeText(this, "Controller profile saved", Toast.LENGTH_SHORT).show(); finish() }
                .onFailure { Toast.makeText(this, "Check ranges and mappings: ${it.message}", Toast.LENGTH_LONG).show() }
        }
        button("Restore defaults") {
            runCatching { ControllerProfile().save(ClientSession.profileFile(this)); render(ControllerProfile()); ClientSession.log("[input] PROFILE_DEFAULTS_RESTORED") }
                .onFailure { Toast.makeText(this, "Save failed: ${it.message}", Toast.LENGTH_LONG).show() }
        }
    }
    companion object {
        fun controllerNames() = InputDevice.getDeviceIds().toList().mapNotNull { InputDevice.getDevice(it) }.filter {
            it.supportsSource(InputDevice.SOURCE_GAMEPAD) || it.supportsSource(InputDevice.SOURCE_JOYSTICK)
        }.joinToString { "${it.name} (id=${it.id}, vendor=${it.vendorId}, product=${it.productId})" }.ifEmpty { "No gamepad detected" }
    }
}
