package com.example.ai

/**
 * Controls how Auto Captions format the recognized speech on the timeline:
 * - [ORIGINAL_SCRIPT]: Transcribe in the speaker's native language & script (e.g., Telugu → తెలుగు).
 * - [TRANSLATE]: Translate speech from ANY spoken language into ANY chosen target language.
 * - [PHONETIC_ENGLISH]: Write spoken words using English (Latin) letters so an English reader
 *   pronounces the exact words spoken in the video/audio (e.g., Telugu "ఎలా ఉన్నారు" → "Ela unnaru").
 */
enum class CaptionOutputMode(
    val shortLabel: String,
    val description: String
) {
    ORIGINAL_SCRIPT(
        shortLabel = "Original",
        description = "Keep native script of the spoken language"
    ),
    TRANSLATE(
        shortLabel = "Translate",
        description = "Translate spoken audio into any target language"
    ),
    PHONETIC_ENGLISH(
        shortLabel = "English Phonetic",
        description = "English letters pronounced exactly like the spoken audio (e.g., Tenglish/Hinglish)"
    )
}
