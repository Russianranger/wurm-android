package io.github.russianranger.wurmlauncher

import android.app.Activity
import android.os.Bundle

/** Compatibility entry for existing intents; the launcher now owns all three tabs. */
class ClientActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(ManagedActivity.tabIntent(this,ManagedActivity.CLIENT))
        finish()
    }
}
