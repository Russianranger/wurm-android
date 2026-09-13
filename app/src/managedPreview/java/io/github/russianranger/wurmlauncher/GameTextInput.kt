package io.github.russianranger.wurmlauncher

import java.util.concurrent.BlockingQueue

/** Queue a composer action as one ordered batch; never log the draft. */
object GameTextInput {
    fun enqueue(queue: BlockingQueue<String>, text: String, submit: Boolean): Boolean {
        if ((!submit && text.isEmpty()) || text.length > 240 || text.any { it.code < 32 || it.code == 127 }) return false
        synchronized(queue) {
            val count = text.length + if (submit) 2 else 0
            // Retain room for input releases. Failure leaves the entire draft with the caller.
            if (queue.remainingCapacity() < count + 8) return false
            text.forEach { queue.add("TEXT ${it.code}") }
            if (submit) {
                // WurmInputField submits in keyTyped('\r'), not keyPressed(KEY_RETURN, '\0').
                queue.add("KEYCHAR 28 13 0")
                queue.add("KEY 28 0")
            }
            return true
        }
    }
}
