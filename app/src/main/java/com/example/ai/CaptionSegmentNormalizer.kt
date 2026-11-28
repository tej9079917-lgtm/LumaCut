package com.example.ai

import com.example.model.CaptionAnimationPreset
import com.example.model.CaptionClip

data class RawCaptionSegment(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

/**
 * Normalizes raw subtitle segments from Gemini, Groq/OpenAI Whisper, or Android Offline
 * SpeechRecognizer into clean, non-overlapping, karaoke-ready CaptionClip blocks.
 */
object CaptionSegmentNormalizer {

    fun normalize(
        rawSegments: List<RawCaptionSegment>,
        timelineOffsetMs: Long,
        clipDurationMs: Long,
        maxWordsPerLine: Int = 6
    ): List<CaptionClip> {
        val safeOffset = timelineOffsetMs.coerceAtLeast(0L)
        val safeClipDur = clipDurationMs.coerceAtLeast(1_000L)
        val wordLimit = maxWordsPerLine.coerceIn(3, 10)

        // 1. Clean text, clamp timestamps, and split long utterances into punchy short-form blocks
        val expanded = mutableListOf<RawCaptionSegment>()
        for (seg in rawSegments) {
            val cleaned = seg.text
                .replace(Regex("\\s+"), " ")
                .trim()
            if (cleaned.isEmpty()) continue

            val relStart = seg.startMs.coerceIn(0L, (safeClipDur - 250L).coerceAtLeast(0L))
            val relEnd = seg.endMs
                .coerceAtLeast(relStart + 450L)
                .coerceAtMost(safeClipDur.coerceAtLeast(relStart + 450L))

            val words = cleaned.split(" ").filter { it.isNotBlank() }
            if (words.size <= wordLimit) {
                expanded.add(RawCaptionSegment(relStart, relEnd, cleaned))
            } else {
                val chunks = words.chunked(wordLimit)
                val totalWordCount = words.size.coerceAtLeast(1)
                val spanMs = (relEnd - relStart).coerceAtLeast(chunks.size * 450L)
                var cursor = relStart
                chunks.forEachIndexed { idx, chunkWords ->
                    val chunkDur = if (idx == chunks.lastIndex) {
                        (relEnd - cursor).coerceAtLeast(450L)
                    } else {
                        ((spanMs * chunkWords.size) / totalWordCount).coerceAtLeast(450L)
                    }
                    val chunkEnd = (cursor + chunkDur).coerceAtMost(safeClipDur.coerceAtLeast(cursor + 450L))
                    expanded.add(
                        RawCaptionSegment(
                            startMs = cursor,
                            endMs = chunkEnd,
                            text = chunkWords.joinToString(" ")
                        )
                    )
                    cursor = chunkEnd
                }
            }
        }

        if (expanded.isEmpty()) return emptyList()

        // 2. Sort chronologically and eliminate overlaps cleanly
        val sorted = expanded.sortedWith(compareBy<RawCaptionSegment> { it.startMs }.thenBy { it.endMs })
        val result = mutableListOf<CaptionClip>()
        for (i in sorted.indices) {
            val current = sorted[i]
            val prevEnd = result.lastOrNull()?.endTimeMs?.minus(safeOffset) ?: -1L
            val adjustedStart = if (prevEnd > current.startMs) prevEnd + 40L else current.startMs
            val nextStart = sorted.getOrNull(i + 1)?.startMs
            var adjustedEnd = current.endMs.coerceAtLeast(adjustedStart + 420L)
            if (nextStart != null && nextStart > adjustedStart + 420L && adjustedEnd > nextStart - 20L) {
                adjustedEnd = nextStart - 20L
            }
            result.add(
                CaptionClip(
                    text = current.text,
                    startTimeMs = safeOffset + adjustedStart,
                    endTimeMs = safeOffset + adjustedEnd.coerceAtLeast(adjustedStart + 350L),
                    animationPreset = CaptionAnimationPreset.ACTIVE_WORD_HIGHLIGHT
                )
            )
        }
        return result
    }

    /**
     * Strips markdown code fences (```json ... ```) if a model wraps JSON output.
     */
    fun stripJsonMarkdownFences(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("```")) return trimmed
        val withoutStart = trimmed.removePrefix("```json").removePrefix("```JSON").removePrefix("```").trimStart()
        return withoutStart.removeSuffix("```").trim()
    }
}
