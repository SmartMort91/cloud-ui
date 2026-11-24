package com.mihon.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream4.theme.LocalTransparentScreens

/**
 * The card look used by settings rows and the extension screens.
 *
 * On the Liquid Glass themes this is a frosted panel: a light translucent fill that is a little
 * brighter at the top, plus a thin light edge. On every other theme it is the same soft tint as
 * before, just a touch stronger so it reads on both dark and light backgrounds.
 */
@Composable
fun Modifier.settingsCard(shape: Shape): Modifier {
    return if (LocalTransparentScreens.current) {
        this
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.17f), Color.White.copy(alpha = 0.08f))
                ),
                shape,
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.07f))
                ),
                shape,
            )
    } else {
        this
            .clip(shape)
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f), shape)
    }
}
