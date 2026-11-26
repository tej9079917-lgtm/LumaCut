package com.example.media

import android.content.Context
import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.res.ResourcesCompat
import com.example.model.StudioFont
import com.example.ui.theme.BundledStudioFonts
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object FontResolver {
    private val composeCache = ConcurrentHashMap<String, FontFamily>()
    private val typefaceCache = ConcurrentHashMap<String, Typeface>()

    fun resolveComposeFont(fontId: String, availableFonts: List<StudioFont>): FontFamily {
        composeCache[fontId]?.let { return it }

        BundledStudioFonts.firstOrNull { it.id == fontId }?.let { bundled ->
            composeCache[fontId] = bundled.composeFamily
            return bundled.composeFamily
        }

        val custom = availableFonts.firstOrNull { it.id == fontId && it.isCustom && !it.filePath.isNullOrBlank() }
        if (custom?.filePath != null) {
            val file = File(custom.filePath)
            if (file.exists()) {
                val loaded = runCatching {
                    val tf = Typeface.createFromFile(file)
                    typefaceCache[fontId] = tf
                    FontFamily(tf)
                }.getOrNull()
                if (loaded != null) {
                    composeCache[fontId] = loaded
                    return loaded
                }
            }
        }

        return BundledStudioFonts.first().composeFamily
    }

    fun resolveTypeface(context: Context, fontId: String, availableFonts: List<StudioFont>): Typeface {
        typefaceCache[fontId]?.let { return it }

        BundledStudioFonts.firstOrNull { it.id == fontId }?.let { bundled ->
            val tf = runCatching { ResourcesCompat.getFont(context, bundled.resId) }.getOrNull()
                ?: Typeface.DEFAULT_BOLD
            typefaceCache[fontId] = tf
            return tf
        }

        val custom = availableFonts.firstOrNull { it.id == fontId && it.isCustom && !it.filePath.isNullOrBlank() }
        if (custom?.filePath != null) {
            val file = File(custom.filePath)
            if (file.exists()) {
                val tf = runCatching { Typeface.createFromFile(file) }.getOrNull()
                if (tf != null) {
                    typefaceCache[fontId] = tf
                    return tf
                }
            }
        }

        return Typeface.DEFAULT_BOLD
    }

    fun clearEntry(fontId: String) {
        composeCache.remove(fontId)
        typefaceCache.remove(fontId)
    }
}
