package io.github.russianranger.wurmlauncher

import org.junit.Assert.*
import org.junit.Test

class GraphicsOptionsTest {
    @Test fun commandsRemainWithinInputLimitWithExplicitInheritedValues() {
        val inherited=List(GraphicsOptions.options.size) { -1 }
        assertEquals("performance:"+List(47) { "-1" }.joinToString(","),GraphicsOptions.command("performance",inherited))
        val highest=GraphicsOptions.options.map { it.minimum+it.choices.size-1 }
        val command=GraphicsOptions.command("imported",highest)
        assertTrue(("VISUAL "+command).length <= 520)
        assertTrue(("VISUAL "+GraphicsOptions.command("performance",inherited)).length <= 520)
        val fields=command.substringAfter(':').split(',')
        assertEquals("16",fields[16]) // existing maximum lights retains its wire position
        assertEquals("200",fields[31]) // contribution culling
        assertEquals("110",fields[36]) // field of view
        assertEquals("200",fields[44]) // +100% postprocess brightness
        assertEquals("1",fields[46]) // compression on
        assertTrue(GraphicsOptions.resolutions.contains("1280x720"))
    }
    @Test fun rejectsUnknownPresetPartialListAndEveryOutOfRangeOption() {
        val inherited=List(GraphicsOptions.options.size) { -1 }
        assertTrue(runCatching { GraphicsOptions.command("other",inherited) }.isFailure)
        assertTrue(runCatching { GraphicsOptions.command("imported",inherited.dropLast(1)) }.isFailure)
        GraphicsOptions.options.forEachIndexed { index, option ->
            for (invalid in listOf(-2,option.minimum+option.choices.size)) {
                val values=inherited.toMutableList(); values[index]=invalid
                assertTrue(option.field,runCatching { GraphicsOptions.command("performance",values) }.isFailure)
            }
        }
        val values=inherited.toMutableList(); values[16]=0
        assertTrue(runCatching { GraphicsOptions.command("performance",values) }.isFailure)
    }
}
