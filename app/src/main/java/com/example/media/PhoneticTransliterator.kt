package com.example.media

/**
 * Pure-Kotlin 100% offline phonetic transliterator for Indic scripts (Telugu, Devanagari/Hindi/Marathi,
 * Tamil, Kannada, Malayalam, Bengali) into readable English/Latin spelling that pronounces the exact
 * spoken words (e.g., Telugu "నమస్కారం" → "Namaskaram", "ఎలా ఉన్నారు" → "Ela unnaru").
 *
 * Used automatically when Offline AI or Offline Mic generates captions in English Phonetic mode,
 * or as an instant zero-network fallback when converting timeline captions phonetically.
 */
object PhoneticTransliterator {

    private data class ScriptRange(val start: Int, val end: Int)

    private val TELUGU = ScriptRange(0x0C00, 0x0C7F)
    private val DEVANAGARI = ScriptRange(0x0900, 0x097F)
    private val TAMIL = ScriptRange(0x0B80, 0x0BFF)
    private val KANNADA = ScriptRange(0x0C80, 0x0CFF)
    private val MALAYALAM = ScriptRange(0x0D00, 0x0D7F)
    private val BENGALI = ScriptRange(0x0980, 0x09FF)

    fun containsIndicScript(text: String): Boolean = text.any { ch ->
        val cp = ch.code
        cp in TELUGU.start..TELUGU.end ||
            cp in DEVANAGARI.start..DEVANAGARI.end ||
            cp in TAMIL.start..TAMIL.end ||
            cp in KANNADA.start..KANNADA.end ||
            cp in MALAYALAM.start..MALAYALAM.end ||
            cp in BENGALI.start..BENGALI.end
    }

    /**
     * Converts any supported Indic script phrase into readable phonetic English letters while
     * leaving already-Latin words, numbers, and punctuation untouched.
     */
    fun toPhoneticEnglish(input: String): String {
        if (input.isBlank() || !containsIndicScript(input)) return input
        val out = StringBuilder(input.length * 2)
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            val cp = ch.code
            val nextCp = if (i + 1 < input.length) input[i + 1].code else -1

            when {
                cp in TELUGU.start..TELUGU.end -> {
                    i += appendIndicChar(out, cp, nextCp, 0x0C00, isDravidian = true, isTamil = false)
                }
                cp in KANNADA.start..KANNADA.end -> {
                    i += appendIndicChar(out, cp, nextCp, 0x0C80, isDravidian = true, isTamil = false)
                }
                cp in MALAYALAM.start..MALAYALAM.end -> {
                    i += appendIndicChar(out, cp, nextCp, 0x0D00, isDravidian = true, isTamil = false)
                }
                cp in TAMIL.start..TAMIL.end -> {
                    i += appendIndicChar(out, cp, nextCp, 0x0B80, isDravidian = true, isTamil = true)
                }
                cp in DEVANAGARI.start..DEVANAGARI.end -> {
                    i += appendIndicChar(out, cp, nextCp, 0x0900, isDravidian = false, isTamil = false)
                }
                cp in BENGALI.start..BENGALI.end -> {
                    i += appendIndicChar(out, cp, nextCp, 0x0980, isDravidian = false, isTamil = false)
                }
                else -> {
                    out.append(ch)
                    i += 1
                }
            }
        }
        return polishSpacingAndCase(out.toString())
    }

    private fun appendIndicChar(
        out: StringBuilder,
        cp: Int,
        nextCp: Int,
        base: Int,
        isDravidian: Boolean,
        isTamil: Boolean
    ): Int {
        val offset = cp - base
        val nextOffset = if (nextCp in base..(base + 0x7F)) nextCp - base else -1

        // Independent vowels (0x05..0x14)
        independentVowel(offset, isDravidian)?.let { v ->
            out.append(v)
            return 1
        }

        // Signs: chandrabindu, anusvara (m/n), visarga (h)
        when (offset) {
            0x01, 0x02 -> {
                out.append("m")
                return 1
            }
            0x03 -> {
                out.append("h")
                return 1
            }
            0x4D -> return 1 // stray virama
        }

        // Consonants (0x15..0x39)
        val consonant = consonantSound(offset, isTamil)
        if (consonant != null) {
            out.append(consonant)
            // Check if followed by virama (halant = 0x4D)
            if (nextOffset == 0x4D) {
                return 2
            }
            // Check if followed by a dependent vowel matra (0x3E..0x4C)
            val matra = dependentMatra(nextOffset, isDravidian)
            if (matra != null) {
                out.append(matra)
                return 2
            }
            // Otherwise append inherent 'a' (with schwa drop at word end for Devanagari/Bengali)
            val isWordEnd = nextCp == -1 || !Character.isLetter(nextCp)
            if (isDravidian || !isWordEnd) {
                out.append('a')
            }
            return 1
        }

        // Standalone matra (fallback)
        dependentMatra(offset, isDravidian)?.let { m ->
            out.append(m)
            return 1
        }

        // Indic digits (0x66..0x6F)
        if (offset in 0x66..0x6F) {
            out.append(('0'.code + (offset - 0x66)).toChar())
            return 1
        }

        // Danda / punctuation
        if (offset == 0x64) {
            out.append('.')
            return 1
        }

        return 1
    }

    private fun independentVowel(offset: Int, isDravidian: Boolean): String? = when (offset) {
        0x05 -> "a"
        0x06 -> "aa"
        0x07 -> "i"
        0x08 -> "ee"
        0x09 -> "u"
        0x0A -> "oo"
        0x0B -> "ru"
        0x0E -> "e"
        0x0F -> if (isDravidian) "ee" else "e"
        0x10 -> "ai"
        0x12 -> "o"
        0x13 -> if (isDravidian) "oo" else "o"
        0x14 -> "au"
        else -> null
    }

    private fun dependentMatra(offset: Int, isDravidian: Boolean): String? = when (offset) {
        0x3E -> "a"
        0x3F -> "i"
        0x40 -> "ee"
        0x41 -> "u"
        0x42 -> "oo"
        0x43 -> "ru"
        0x44 -> "roo"
        0x46 -> "e"
        0x47 -> if (isDravidian) "e" else "e"
        0x48 -> "ai"
        0x4A -> "o"
        0x4B -> if (isDravidian) "o" else "o"
        0x4C -> "au"
        0x55, 0x56 -> "" // length marks
        else -> null
    }

    private fun consonantSound(offset: Int, isTamil: Boolean): String? = when (offset) {
        0x15 -> "k"
        0x16 -> "kh"
        0x17 -> "g"
        0x18 -> "gh"
        0x19 -> "ng"
        0x1A -> if (isTamil) "ch" else "ch"
        0x1B -> "chh"
        0x1C -> "j"
        0x1D -> "jh"
        0x1E -> "ny"
        0x1F -> "t"
        0x20 -> "th"
        0x21 -> "d"
        0x22 -> "dh"
        0x23 -> "n"
        0x24 -> "t"
        0x25 -> "th"
        0x26 -> "d"
        0x27 -> "dh"
        0x28 -> "n"
        0x29 -> "n"
        0x2A -> "p"
        0x2B -> "ph"
        0x2C -> "b"
        0x2D -> "bh"
        0x2E -> "m"
        0x2F -> "y"
        0x30 -> "r"
        0x31 -> "r"
        0x32 -> "l"
        0x33 -> "l"
        0x34 -> "zh"
        0x35 -> "v"
        0x36 -> "sh"
        0x37 -> "sh"
        0x38 -> "s"
        0x39 -> "h"
        else -> null
    }

    private fun polishSpacingAndCase(raw: String): String {
        val collapsed = raw.replace(Regex("\\s+"), " ").trim()
        if (collapsed.isEmpty()) return collapsed
        return collapsed.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
