package io.github.russianranger.wurmlauncher

import java.util.concurrent.LinkedBlockingQueue
import org.junit.Assert.*
import org.junit.Test

class GameTextInputTest {
    @Test fun submitIncludesCharacterAfterTextAndReleasesEnter() {
        val queue=LinkedBlockingQueue<String>(512)
        assertTrue(GameTextInput.enqueue(queue,"Hi",true))
        assertEquals(listOf("TEXT 72","TEXT 105","KEYCHAR 28 13 0","KEY 28 0"),queue.toList())
    }
    @Test fun insertLeavesGameFieldUnsubmitted() {
        val queue=LinkedBlockingQueue<String>(512)
        assertTrue(GameTextInput.enqueue(queue,"Hi",false))
        assertEquals(listOf("TEXT 72","TEXT 105"),queue.toList())
    }
    @Test fun emptySubmitSendsExistingGameDraft() {
        val queue=LinkedBlockingQueue<String>(512)
        assertFalse(GameTextInput.enqueue(queue,"",false))
        assertTrue(GameTextInput.enqueue(queue,"",true))
        assertEquals(listOf("KEYCHAR 28 13 0","KEY 28 0"),queue.toList())
    }
    @Test fun fullQueueDoesNotInsertPartialDraftOrEnter() {
        val queue=LinkedBlockingQueue<String>(12)
        queue.add("KEY 17 0")
        assertFalse(GameTextInput.enqueue(queue,"Hi",true))
        assertEquals(listOf("KEY 17 0"),queue.toList())
        queue.clear()
        assertTrue(GameTextInput.enqueue(queue,"Hi",true))
        assertEquals(8,queue.remainingCapacity())
    }
    @Test fun invalidDraftDoesNotSendAnything() {
        val queue=LinkedBlockingQueue<String>(512)
        for(text in listOf("a\nb","\r","\u007f","x".repeat(241))) {
            assertFalse(GameTextInput.enqueue(queue,text,true))
            assertTrue(queue.isEmpty())
        }
    }
    @Test fun maximumDraftAndUnicodeKeepExactOrder() {
        val queue=LinkedBlockingQueue<String>(512)
        val text="\u00e9\ud83d\ude00"+"x".repeat(237)
        assertTrue(GameTextInput.enqueue(queue,text,true))
        assertEquals(text.map { "TEXT ${it.code}" }+listOf("KEYCHAR 28 13 0","KEY 28 0"),queue.toList())
    }
}
