package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.media.FontResolver
import com.example.model.LowerThirdTemplate
import com.example.model.StudioFont
import com.example.ui.theme.BorderHairline
import com.example.ui.theme.ChalkWhite
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.InspectorCharcoal
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SilverMuted

@Composable
fun LowerThirdTemplatesInspector(
    availableFonts: List<StudioFont>,
    onInsertTemplate: (LowerThirdTemplate) -> Unit,
    onClose: () -> Unit
) {
    Surface(
        color = InspectorCharcoal,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("lower_third_templates_inspector")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 310.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 12.dp)
        ) {
            InspectorHeader(
                title = "Animated Lower Thirds & Titles",
                onClose = onClose
            )

            val rows = remember { LowerThirdTemplate.entries.chunked(2) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rows.forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pair.forEach { template ->
                            LowerThirdTemplateCard(
                                template = template,
                                availableFonts = availableFonts,
                                onClick = { onInsertTemplate(template) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LowerThirdTemplateCard(
    template: LowerThirdTemplate,
    availableFonts: List<StudioFont>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fontFamily = remember(template.fontId, availableFonts) {
        FontResolver.resolveComposeFont(template.fontId, availableFonts)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(ObsidianBg)
            .border(1.dp, BorderHairline, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(10.dp)
            .testTag("template_${template.name.lowercase()}")
    ) {
        // Mini styled preview pill
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(
                    if (template.hasBackground) Color(template.backgroundColorArgb)
                    else Color(template.previewSwatchArgb).copy(alpha = 0.16f)
                )
                .border(
                    1.dp,
                    Color(template.previewSwatchArgb).copy(alpha = 0.7f),
                    RoundedCornerShape(7.dp)
                )
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = template.defaultText.replace("\n", " • "),
                fontFamily = fontFamily,
                style = MaterialTheme.typography.labelMedium,
                color = Color(template.textColorArgb),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.displayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = ChalkWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${template.subtitle} • ${template.motionPreset.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SilverMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.Default.AddCircleOutline,
                contentDescription = "Insert ${template.displayName}",
                tint = CinemaAmber,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
