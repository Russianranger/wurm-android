package io.github.russianranger.wurmlauncher

/** Read only identity, signal, abort/cause and crashing-thread frames from Android 13's proto.
 * Schema: AOSP system/core/debuggerd/proto/tombstone.proto. Memory/log/FD dumps are skipped.
 */
object ClientTombstone {
    const val LIMIT = 1024 * 1024
    private data class Field(val number: Int, val value: Long = 0, val bytes: ByteArray? = null)
    private fun fields(data: ByteArray, wanted: Set<Int>): List<Field> {
        require(data.size <= LIMIT) { "Tombstone exceeds size limit" }
        var position = 0
        fun integer(): Long {
            var value = 0L
            for (index in 0..9) {
                require(position < data.size) { "Truncated protobuf integer" }
                val byte = data[position++].toInt() and 255
                require(index < 9 || byte <= 1) { "Overflowing protobuf integer" }
                value = value or ((byte and 127).toLong() shl (7 * index))
                if (byte and 128 == 0) return value
            }
            error("Invalid protobuf integer")
        }
        val result = arrayListOf<Field>()
        var count = 0
        while (position < data.size) {
            require(++count <= 20000) { "Too many protobuf fields" }
            val tag = integer()
            require(tag > 0 && (tag ushr 3) in 1..0x1fffffffL) { "Invalid protobuf tag" }
            val number = (tag ushr 3).toInt()
            when ((tag and 7).toInt()) {
                0 -> { val value = integer(); if (number in wanted) result += Field(number, value) }
                1, 5 -> {
                    val size = if ((tag and 7) == 1L) 8 else 4
                    require(size <= data.size - position) { "Truncated fixed field" }; position += size
                }
                2 -> {
                    val length = integer()
                    require(length >= 0 && length <= data.size - position) { "Truncated protobuf bytes" }
                    val end = position + length.toInt()
                    if (number in wanted) result += Field(number, bytes = data.copyOfRange(position, end))
                    position = end
                }
                else -> error("Unsupported protobuf wire type")
            }
        }
        return result
    }
    private fun List<Field>.number(id: Int): Long = lastOrNull { it.number == id && it.bytes == null }?.value ?: 0
    private fun List<Field>.text(id: Int, limit: Int = 600): String = lastOrNull { it.number == id }?.bytes
        ?.toString(Charsets.UTF_8)?.take(limit)?.map { if (it.isISOControl()) ' ' else it }?.joinToString("").orEmpty()
    fun summarize(data: ByteArray, pid: Int, uid: Int): List<String> {
        require(pid > 0 && uid >= 0)
        val top = fields(data, setOf(5, 6, 7, 10, 14, 15, 16))
        require(top.number(5) == pid.toLong() && top.number(7) == uid.toLong()) { "Tombstone identity mismatch" }
        val tid = top.number(6)
        val lines = arrayListOf("TOMBSTONE_ID pid=$pid uid=$uid tid=$tid")
        top.firstOrNull { it.number == 10 }?.bytes?.let {
            val signal = fields(it, setOf(1, 2, 3, 4, 8, 9))
            lines += "TOMBSTONE_SIGNAL number=${signal.number(1)} name=${signal.text(2)} code=${signal.number(3).toInt()} codeName=${signal.text(4)}" +
                if (signal.number(8) != 0L) " address=0x${signal.number(9).toULong().toString(16)}" else ""
        }
        top.text(14, 1200).takeIf { it.isNotEmpty() }?.let { lines += "TOMBSTONE_ABORT $it" }
        top.filter { it.number == 15 }.take(4).forEach { cause -> cause.bytes?.let {
            lines += "TOMBSTONE_CAUSE " + fields(it, setOf(1)).text(1)
        } }
        for (entry in top.filter { it.number == 16 }) {
            val map = fields(entry.bytes ?: continue, setOf(1, 2))
            if (map.number(1) != tid) continue
            val thread = fields(map.firstOrNull { it.number == 2 }?.bytes ?: continue, setOf(1, 2, 4))
            require(thread.number(1) == tid) { "Tombstone thread identity mismatch" }
            lines += "TOMBSTONE_THREAD tid=$tid name=${thread.text(2)}"
            thread.filter { it.number == 4 }.take(32).forEachIndexed { index, field ->
                val frame = fields(field.bytes ?: return@forEachIndexed, setOf(1, 4, 5, 6, 8))
                lines += "TOMBSTONE_FRAME #$index pc=0x${frame.number(1).toULong().toString(16)} ${frame.text(6)} " +
                    "${frame.text(4)}+${frame.number(5)} buildId=${frame.text(8, 128)}"
            }
            break
        }
        return lines
    }
}
