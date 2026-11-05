package com.lagradost.cloudstream3.ui.result

import android.content.Context
import android.view.View
import android.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.UiPrefs
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.UIHelper.navigate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * When the same movie or show exists on several providers, this turns the provider badge on
 * the result page into a small dropdown that jumps to the same title on another provider.
 *
 * Other providers are searched by title in the background, only exact title matches are shown.
 * Everything lives in this file, the result pages only call [bind].
 */
object ProviderSwitcher {
    data class Alternative(val apiName: String, val url: String, val title: String)

    private const val SEARCH_TIMEOUT_MS = 8000L
    // Every title you open is searched on this many other providers, so keep it small
    private const val MAX_PROVIDERS = 6

    private val cache = HashMap<String, List<Alternative>>()

    private fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("\\(\\d{4}\\)"), " ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()

    private suspend fun findAlternatives(
        context: Context,
        currentApi: String,
        title: String
    ): List<Alternative> {
        val key = normalize(title)
        if (key.length < 2) return emptyList()
        val cacheKey = "$key|$currentApi"
        synchronized(cache) { cache[cacheKey] }?.let { return it }

        val providers = context.filterProviderByPreferredMedia(hasHomePageIsRequired = false)
            .filter { it.name != currentApi }
            .take(MAX_PROVIDERS)

        val found = ArrayList<Alternative>()
        withContext(Dispatchers.IO) {
            providers.amap { api ->
                val result = withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
                    APIRepository(api).search(title, 1)
                }
                if (result is Resource.Success) {
                    result.value.items.firstOrNull { normalize(it.name) == key }?.let { match ->
                        synchronized(found) {
                            found.add(Alternative(api.name, match.url, match.name))
                        }
                    }
                }
            }
        }

        val sorted = synchronized(found) { found.sortedBy { it.apiName.lowercase() } }
        synchronized(cache) { cache[cacheKey] = sorted }
        return sorted
    }

    private fun open(fragment: Fragment, alternative: Alternative) {
        val activity = fragment.activity ?: return
        val currentDestination: Int? = try {
            fragment.findNavController().currentDestination?.id
        } catch (_: Throwable) {
            null
        }
        if (currentDestination == null) return
        val resultsId = if (isLayout(TV or EMULATOR)) {
            R.id.global_to_navigation_results_tv
        } else {
            R.id.global_to_navigation_results_phone
        }
        // Replace the current result page, so back goes to where you came from
        activity.navigate(
            resultsId,
            ResultFragment.newInstance(alternative.url, alternative.apiName, alternative.title),
            NavOptions.Builder().setPopUpTo(currentDestination, true).build()
        )
    }

    /**
     * @param button the provider badge, it becomes a dropdown if the title exists elsewhere
     * @param title title of the currently opened result
     * @param currentApi name of the provider the result is currently loaded from
     * @param openInBrowser what a long press does, the badge used to open the site on click
     */
    fun bind(
        fragment: Fragment,
        button: MaterialButton,
        title: String,
        currentApi: String,
        openInBrowser: () -> Unit
    ) {
        if (!UiPrefs.isProviderSwitcherEnabled) return
        val context = fragment.context?.applicationContext ?: return
        val key = "$currentApi|${normalize(title)}"
        if (button.getTag(R.id.result_meta_site) == key) return
        button.setTag(R.id.result_meta_site, key)

        ioSafe {
            val alternatives = findAlternatives(context, currentApi, title)
            if (alternatives.isEmpty()) return@ioSafe
            main {
                if (fragment.view == null || button.getTag(R.id.result_meta_site) != key) {
                    return@main
                }
                button.setIconResource(R.drawable.ic_baseline_keyboard_arrow_down_24)
                button.iconGravity = MaterialButton.ICON_GRAVITY_TEXT_END
                button.isFocusable = true
                button.setOnClickListener { anchor: View ->
                    val menu = PopupMenu(anchor.context, anchor)
                    menu.menu.add(0, 0, 0, currentApi).apply {
                        isCheckable = true
                        isChecked = true
                    }
                    alternatives.forEachIndexed { index, alternative ->
                        menu.menu.add(0, index + 1, index + 1, alternative.apiName)
                    }
                    menu.setOnMenuItemClickListener { item ->
                        alternatives.getOrNull(item.itemId - 1)?.let { open(fragment, it) }
                        true
                    }
                    menu.show()
                }
                button.setOnLongClickListener {
                    openInBrowser()
                    true
                }
            }
        }
    }
}
