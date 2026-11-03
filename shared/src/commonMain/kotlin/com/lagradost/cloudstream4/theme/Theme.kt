package com.lagradost.cloudstream4.theme

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class CloudStreamThemeMode {
    /** "Black" standard dark, #111111 backgrounds */
    Dark,

    /** "Amoled" / "AmoledLight" pure black (#000000) */
    Amoled,

    /** "AmoledLight" pure black (#000000) */
    AmoledLight,

    /** "Light" white/gray backgrounds, dark text */
    Light,

    /** "Dracula" */
    Dracula,

    /** "Lavender" */
    Lavender,

    /** "SilentBlue" */
    SilentBlue,

    /** "Glass" Liquid Glass, translucent panels over a gradient backdrop */
    Glass,

    /** "GlassAurora" Liquid Glass with a green and teal backdrop */
    GlassAurora,

    /** "GlassSunset" Liquid Glass with a magenta and orange backdrop */
    GlassSunset,

    /** "Ocean" deep teal */
    Ocean,

    /** "Ember" warm dark brown */
    Ember,

    /** "Crimson" dark red */
    Crimson,

    /** "Graphite" neutral dark gray */
    Graphite,

    /** "System" resolved on each platform via [isSystemInDarkTheme] */
    FollowSystem,

    /**
     * Uses platform dynamic color system, Material You on Android 12+,
     * falls back to [Dark] on unsupported platforms.
     */
    Dynamic,
}

@Composable
fun modeToTheme(mode : CloudStreamThemeMode, primaryColor: CloudStreamPrimaryColor) : CloudStreamColorScheme {
    val dynamicTheme = DeviceTheme.resolveDynamicTheme()
    val dynamicPrimary = DeviceTheme.resolveDynamicPrimaryColor()
    val dynamicSecondary = DeviceTheme.resolveDynamicSecondaryColor()
    val systemDark = isSystemInDarkTheme()
    val color = remember(mode, primaryColor, systemDark, dynamicTheme, dynamicPrimary, dynamicSecondary) {
        val base = when (mode) {
            CloudStreamThemeMode.Dark -> darkScheme()
            CloudStreamThemeMode.Amoled -> amoledScheme()
            CloudStreamThemeMode.AmoledLight -> amoledLightScheme()
            CloudStreamThemeMode.Light -> lightScheme()
            CloudStreamThemeMode.Dracula -> draculaScheme()
            CloudStreamThemeMode.Lavender -> lavenderScheme()
            CloudStreamThemeMode.SilentBlue -> silentBlueScheme()
            CloudStreamThemeMode.Glass -> glassScheme()
            CloudStreamThemeMode.GlassAurora -> glassScheme(Color(0xFF062A2A))
            CloudStreamThemeMode.GlassSunset -> glassScheme(Color(0xFF220B2B))
            CloudStreamThemeMode.Ocean -> solidScheme(
                0xFF08202A, 0xFF10303B, 0xFF17404E, 0xFF123541, 0xFFE4F5F8, 0xFF7FB0BC, 0xFF4FD1E0
            )
            CloudStreamThemeMode.Ember -> solidScheme(
                0xFF1D100C, 0xFF2D1A14, 0xFF3A2219, 0xFF33201A, 0xFFFBEADF, 0xFFC09A86, 0xFFFF9B5E
            )
            CloudStreamThemeMode.Crimson -> solidScheme(
                0xFF170909, 0xFF261112, 0xFF331818, 0xFF2C1415, 0xFFF8E8E8, 0xFFBC8F90, 0xFFFF6B6B
            )
            CloudStreamThemeMode.Graphite -> solidScheme(
                0xFF14171A, 0xFF1F2327, 0xFF2A3035, 0xFF252A2F, 0xFFE8EAED, 0xFF8E959C, 0xFFAEB7C0
            )
            CloudStreamThemeMode.FollowSystem -> if (systemDark) darkScheme() else lightScheme()
            CloudStreamThemeMode.Dynamic -> dynamicTheme
        }
        when {
            mode == CloudStreamThemeMode.Dynamic -> base
            primaryColor == CloudStreamPrimaryColor.DYNAMIC -> base.copy(primary = dynamicPrimary)
            primaryColor == CloudStreamPrimaryColor.DYNAMIC_TWO -> base.copy(primary = dynamicSecondary)
            else -> base.copy(primary = primaryColor.color)
        }
    }
    return color
}

private fun CloudStreamColorScheme.toMaterial3ColorScheme() = if (isLight) {
    lightColorScheme(
        primary = primary,
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        surfaceContainer = surfaceContainer,
        onBackground = onBackground,
        onSurface = onBackground,
        onSurfaceVariant = onSurfaceVariant,
        onPrimary = Color.White,
    )
} else {
    darkColorScheme(
        primary = primary,
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        surfaceContainer = surfaceContainer,
        onBackground = onBackground,
        onSurface = onBackground,
        onSurfaceVariant = onSurfaceVariant,
        onPrimary = Color.White,
    )
}

/** True when screens should let the window backdrop show through (Liquid Glass) */
val LocalTransparentScreens = staticCompositionLocalOf { false }

internal val LocalSharedInfiniteTransition = staticCompositionLocalOf<InfiniteTransition> { throw NotImplementedError() }

/**
 * Global synchronized animation, so many items in e.g. a LazyList can animate at the same time,
 * even if they appeared at different times
 * */
@Composable
@ReadOnlyComposable
fun MaterialTheme.infiniteSharedTransition() = LocalSharedInfiniteTransition.current

@Composable
fun CloudStreamPreviewTheme(content: @Composable () -> Unit) {
    CloudStreamTheme(content = content)
}

@Composable
fun CloudStreamTheme(
    mode: CloudStreamThemeMode = CloudStreamThemeMode.FollowSystem,
    primaryColor: CloudStreamPrimaryColor = CloudStreamPrimaryColor.NORMAL,
    content: @Composable () -> Unit,
) {
    val csColors = modeToTheme(mode, primaryColor)
    val globalTransition = rememberInfiniteTransition(label = "GlobalSharedTransition")
    // We do not provide csColors as a global, because people should use MaterialTheme directly instead
    CompositionLocalProvider(
        LocalSharedInfiniteTransition provides globalTransition,
        LocalTransparentScreens provides (
            mode == CloudStreamThemeMode.Glass ||
                mode == CloudStreamThemeMode.GlassAurora ||
                mode == CloudStreamThemeMode.GlassSunset
        ),
    ) {
        MaterialTheme(
            colorScheme = csColors.toMaterial3ColorScheme(),
            content = content,
            typography = AppFont.typography
        )
    }
}