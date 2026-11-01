package com.lagradost.cloudstream3.ui

import android.view.View
import android.view.ViewGroup
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.utils.DataStoreHelper

/**
 * Look and layout options that are stored per profile (account), so every profile
 * keeps its own search provider, home style and so on.
 *
 * Watch history, resume watching, bookmarks, search history and the selected home provider
 * are already stored per profile by [DataStoreHelper]; this keeps the new layout options
 * consistent with that.
 */
object UiPrefs {
    const val SEARCH_PROVIDER = "search_provider"
    const val HOME_STYLE = "home_style"
    // The streaming-style home is the default
    const val HOME_DEFAULT = "classic"
    const val FOCUS_STYLE = "focus_style"
    const val HOME_TILES = "home_tiles"
    const val PROVIDER_SWITCHER = "provider_switcher"

    const val HOME_CLASSIC = "classic"
    const val HOME_CINEMATIC = "cinematic"

    const val FOCUS_LIFT = "lift"
    const val FOCUS_BORDER = "border"

    private fun path(name: String) = "${DataStoreHelper.currentAccount}/ui_$name"

    fun getOpt(name: String, default: String): String =
        getKey<String>(path(name)) ?: default

    fun putOpt(name: String, value: String) = setKey(path(name), value)

    /** Name of the single provider to search in, null if search uses all selected providers */
    var searchProvider: String?
        get() = getOpt(SEARCH_PROVIDER, "").ifBlank { null }
        set(value) = putOpt(SEARCH_PROVIDER, value ?: "")

    val isCinematicHome: Boolean get() = getOpt(HOME_STYLE, HOME_DEFAULT) == HOME_CINEMATIC

    val areBrowseTilesEnabled: Boolean get() = getOpt(HOME_TILES, "on") == "on"

    val isProviderSwitcherEnabled: Boolean get() = getOpt(PROVIDER_SWITCHER, "on") == "on"

    val isLiftFocus: Boolean get() = getOpt(FOCUS_STYLE, FOCUS_LIFT) == FOCUS_LIFT

    /**
     * Slightly raise and enlarge a card while it is focused (TV and emulator layouts).
     * The colored outline itself comes from the card's focus foreground drawable.
     */
    fun applyCardFocus(view: View, hasFocus: Boolean) {
        val lift = isLiftFocus
        val scale = if (hasFocus && lift) 1.06f else 1f
        val lifted = if (hasFocus && lift) 12f else 0f
        if (hasFocus && lift) {
            // Make sure the enlarged card is not cut off by its parents
            (view.parent as? ViewGroup)?.apply {
                clipChildren = false
                clipToPadding = false
            }
        }
        view.animate()
            .scaleX(scale)
            .scaleY(scale)
            .translationZ(lifted)
            .setDuration(120)
            .start()
    }
}
