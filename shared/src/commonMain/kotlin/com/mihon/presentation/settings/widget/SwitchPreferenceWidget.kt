package com.mihon.presentation.settings.widget

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.lagradost.cloudstream4.generated.resources.Res
import com.lagradost.cloudstream4.generated.resources.preview
import com.lagradost.cloudstream4.theme.CloudStreamPreviewTheme
import org.jetbrains.compose.resources.painterResource

@Composable
fun SwitchPreferenceWidget(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    icon: Painter? = null,
    checked: Boolean = false,
    onCheckedChanged: (Boolean) -> Unit,
) {
    TextPreferenceWidget(
        modifier = modifier,
        title = title,
        subtitle = subtitle,
        icon = icon,
        widget = {
            val onBg = MaterialTheme.colorScheme.onBackground
            Switch(
                checked = checked,
                onCheckedChange = null,
                modifier = Modifier.padding(start = TrailingWidgetBuffer),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = onBg.copy(alpha = 0.85f),
                    uncheckedTrackColor = onBg.copy(alpha = 0.14f),
                    uncheckedBorderColor = onBg.copy(alpha = 0.40f),
                ),
            )
        },
        onPreferenceClick = { onCheckedChanged(!checked) },
    )
}

@PreviewLightDark
@Composable
private fun SwitchPreferenceWidgetPreview() {
    CloudStreamPreviewTheme {
        Surface {
            Column {
                SwitchPreferenceWidget(
                    title = "Text preference with icon",
                    subtitle = "Text preference summary",
                    icon = painterResource(Res.drawable.preview) ,
                    checked = true,
                    onCheckedChanged = {},
                )
                SwitchPreferenceWidget(
                    title = "Text preference",
                    subtitle = "Text preference summary",
                    checked = false,
                    onCheckedChanged = {},
                )
                SwitchPreferenceWidget(
                    title = "Text preference no summary",
                    checked = false,
                    onCheckedChanged = {},
                )
                SwitchPreferenceWidget(
                    title = "Another text preference no summary",
                    checked = false,
                    onCheckedChanged = {},
                )
            }
        }
    }
}
