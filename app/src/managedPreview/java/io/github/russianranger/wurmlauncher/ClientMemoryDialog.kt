package io.github.russianranger.wurmlauncher

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.os.Looper
import android.widget.TextView

object ClientMemoryDialog {
    fun show(activity: Activity) {
        val text=TextView(activity).apply { setPadding(24,16,24,16) }
        val dialog=AlertDialog.Builder(activity).setTitle("Client memory recording").setView(text)
            .setPositiveButton("Record 5 minutes",null).setNeutralButton("Stop recording",null)
            .setNegativeButton("Close",null).create()
        val handler=Handler(Looper.getMainLooper())
        val poll=object: Runnable {
            override fun run() {
                if(!dialog.isShowing) return
                val ready=ClientSession.gameActive() && ClientSession.inputReady() && ClientSession.memoryRecordingAvailable
                text.text=(if(ready) ClientSession.memoryRecordingNotice else
                    "Enable job profiling in Diagnostics while the client is stopped, then start the game.")+
                    "\n\nRecords allocations, memory use and slow frames during normal play. Takes limited stack samples during long client-work stalls. Stops after five minutes; Close keeps recording. Export the support bundle afterwards.\n\nThis does not force cleanup, take a heap dump, or generate extra game load. Recording adds measurement overhead and cannot by itself prove there is no memory leak."
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=ready && !ClientSession.memoryRecording
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled=ready && ClientSession.memoryRecording
                handler.postDelayed(this,500)
            }
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { ClientSession.send("MEMORY start") }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { ClientSession.send("MEMORY stop") }
            handler.post(poll)
        }
        dialog.setOnDismissListener { handler.removeCallbacks(poll) }
        dialog.show()
    }
}
