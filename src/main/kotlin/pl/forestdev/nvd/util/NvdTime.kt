package pl.forestdev.nvd.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

object NvdTime {
    private val fmt = DateTimeFormatter
        .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS")
        .withZone(ZoneOffset.UTC)

    fun now(): String = fmt.format(Instant.now())
    private fun parse(s: String): Instant =
        LocalDateTime.parse(s, fmt).toInstant(ZoneOffset.UTC)

    fun windows(start: String, end: String, maxDays: Long = 120): List<Pair<String, String>> {
        val s = parse(start)
        val e = parse(end)
        if (!s.isBefore(e)) return emptyList()
        val out = mutableListOf<Pair<String, String>>()
        var cur = s
        while (cur.isBefore(e)) {
            val next = minOf(cur.plus(Duration.ofDays(maxDays)), e)
            out += fmt.format(cur) to fmt.format(next)
            cur = next
        }
        return out
    }

    fun format(i: Instant): String = fmt.format(i)
    fun iso(i: Instant = Instant.now()): String =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withZone(ZoneOffset.UTC).format(i)
}
