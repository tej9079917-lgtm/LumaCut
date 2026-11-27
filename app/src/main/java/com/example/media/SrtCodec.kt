package com.example.media

import com.example.model.CaptionAnimationPreset
import com.example.model.CaptionClip

/**
 * Pure Kotlin SubRip (.srt) parser and serializer with tolerance for BOM, CRLF,
 * dot or comma millisecond separators, and multi-line caption cues.
 */
object SrtCodec {

    private val TIME_LINE_REGEX = Regex(
        """(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})\s*-->\s*(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})"""
    )

    fun parseSrt(rawContent: String): List<CaptionClip> {
        val cleaned = rawContent
            .removePrefix("\uFEFF")
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .trim()
        if (cleaned.isEmpty()) return emptyList()

        val blocks = cleaned.split(Regex("\n\\s*\n+"))
        val out = mutableListOf<CaptionClip>()
        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.isEmpty()) continue
            val timeLineIdx = lines.indexOfFirst { TIME_LINE_REGEX.containsMatchIn(it) }
            if (timeLineIdx < 0) continue
            val match = TIME_LINE_REGEX.find(lines[timeLineIdx]) ?: continue
            val startMs = toMillis(
                match.groupValues[1],
                match.groupValues[2],
                match.groupValues[3],
                match.groupValues[4]
            )
            val endMs = toMillis(
                match.groupValues[5],
                match.groupValues[6],
                match.groupValues[7],
                match.groupValues[8]
            ).coerceAtLeast(startMs + 300L)

            val textLines = lines.drop(timeLineIdx + 1)
            val text = textLines.joinToString("\n").replace(Regex("<[^>]+>"), "").trim()
            if (text.isEmpty()) continue

            out.add(
                CaptionClip(
                    text = text,
                    startTimeMs = startMs,
                    endTimeMs = endMs,
                    animationPreset = CaptionAnimationPreset.ACTIVE_WORD_HIGHLIGHT
                )
            )
        }
        return out.sortedBy { it.startTimeMs }
    }

    fun formatSrt(captions: List<CaptionClip>): String {
        if (captions.isEmpty()) return ""
        return captions
            .sortedBy { it.startTimeMs }
            .mapIndexed { idx, cap ->
                val start = formatSrtTimestamp(cap.startTimeMs)
                val end = formatSrtTimestamp(cap.endTimeMs.coerceAtLeast(cap.startTimeMs + 250L))
                "${idx + 1}\n$start --> $end\n${cap.text.trim()}"
            }
            .joinToString("\n\n") + "\n"
    }

    fun formatSrtTimestamp(ms: Long): String {
        val clamped = ms.coerceAtLeast(0L)
        val hours = clamped / 3_600_000L
        val minutes = (clamped % 3_600_000L) / 60_000L
        val seconds = (clamped % 60_000L) / 1000L
        val millis = clamped % 1000L
        return String.format("%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
    }

    private fun toMillis(h: String, m: String, s: String, msRaw: String): Long {
        val hours = h.toLongOrNull() ?: 0L
        val mins = m.toLongOrNull() ?: 0L
        val secs = s.toLongOrNull() ?: 0L
        val msPadded = msRaw.padEnd(3, '0').take(3).toLongOrNull() ?: 0L
        return hours * 3_600_000L + mins * 60_000L + secs * 1000L + msPadded
    }
}
