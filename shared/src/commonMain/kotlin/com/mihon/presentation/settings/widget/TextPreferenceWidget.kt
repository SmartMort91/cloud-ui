package com.mihon.presentation.settings.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.preview
import com.lagradost.cloudstream4.theme.CloudStreamPreviewTheme
import org.jetbrains.compose.resources.painterResource
import com.mihon.presentation.secondaryItemAlpha

@Composable
fun TextPreferenceWidget(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    icon: Painter? = null,
    iconTint: Color = MaterialTheme.colorScheme.onBackground,
    /** Draw the icon inside a soft tinted circle (used on the main settings page) */
    iconInCircle: Boolean = false,
    widget: @Composable (() -> Unit)? = null,
    onPreferenceClick: (() -> Unit)? = null,
) {
    BasePreferenceWidget(
        modifier = modifier,
        title = title,
        subcomponent = if (!subtitle.isNullOrBlank()) {
            {
                Text(
                    text = subtitle,
                    modifier = Modifier
                        .padding(horizontal = PrefsHorizontalPadding)
                        .secondaryItemAlpha(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 10,
                )
            }
        } else {
            null
        },
        icon = if (icon != null) {
            {
                if (iconInCircle) {
                    val accent = MaterialTheme.colorScheme.primary
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = icon,
                            modifier = Modifier.size(22.dp),
                            tint = accent,
                            contentDescription = null,
                        )
                    }
                } else {
                    Icon(
                        painter = icon,
                        modifier = Modifier.size(24.dp),
                        tint = iconTint,
                        contentDescription = null,
                    )
                }
            }
        } else {
            null
        },
        onClick = onPreferenceClick,
        widget = widget,
    )
}

@PreviewLightDark
@Composable
private fun TextPreferenceWidgetPreview() {
    CloudStreamPreviewTheme {
        Surface {
            Column {
                TextPreferenceWidget(
                    title = "Text preference with icon",
                    subtitle = "Text preference summary",
                    icon = painterResource(Res.drawable.preview),
                    onPreferenceClick = {},
                )
                TextPreferenceWidget(
                    title = "Text preference",
                    subtitle = "Text preference summary",
                    onPreferenceClick = {},
                )
            }
        }
    }
}
