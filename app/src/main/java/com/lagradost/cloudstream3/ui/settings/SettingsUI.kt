package com.lagradost.cloudstream3.ui.settings

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference
import com.lagradost.cloudstream3.CloudStreamApp.Companion.getActivity
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.ui.BasePreferenceFragmentCompat
import com.lagradost.cloudstream3.ui.UiPrefs
import com.lagradost.cloudstream3.ui.clear
import com.lagradost.cloudstream3.ui.home.HomeChildItemAdapter
import com.lagradost.cloudstream3.ui.home.ParentItemAdapter
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.search.SearchResultBuilder
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.updateTv
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.getPref
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.hideOn
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setPaddingBottom
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setToolBarScrollFlags
import com.lagradost.cloudstream3.ui.settings.SettingsFragment.Companion.setUpToolbar
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showDialog
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showMultiDialog
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.toPx

class SettingsUI : BasePreferenceFragmentCompat() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setUpToolbar(R.string.category_ui)
        setPaddingBottom()
        setToolBarScrollFlags()
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        hideKeyboard()
        setPreferencesFromResource(R.xml.settings_ui, rootKey)
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(requireContext())

        (getPref(R.string.overscan_key)?.hideOn(PHONE or EMULATOR) as? SeekBarPreference)?.setOnPreferenceChangeListener { pref, newValue ->
            val padding = (newValue as? Int)?.toPx ?: return@setOnPreferenceChangeListener true
            (pref.context.getActivity() as? MainActivity)?.binding?.homeRoot?.setPadding(padding, padding, padding, padding)
            return@setOnPreferenceChangeListener true
        }

        getPref(R.string.bottom_title_key)?.setOnPreferenceChangeListener { _, _ ->
            HomeChildItemAdapter.sharedPool.clear()
            ParentItemAdapter.sharedPool.clear()
            SearchAdapter.sharedPool.clear()
            true
        }

        getPref(R.string.poster_size_key)?.setOnPreferenceChangeListener { _, newValue ->
            HomeChildItemAdapter.sharedPool.clear()
            ParentItemAdapter.sharedPool.clear()
            SearchAdapter.sharedPool.clear()
            context?.let { HomeChildItemAdapter.updatePosterSize(it, newValue as? Int) }
            true
        }

        getPref(R.string.poster_ui_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.poster_ui_options)
            val keys = resources.getStringArray(R.array.poster_ui_options_values)
            val prefValues = keys.map {
                settingsManager.getBoolean(it, true)
            }.mapIndexedNotNull { index, b ->
                if (b) {
                    index
                } else null
            }

            activity?.showMultiDialog(
                prefNames.toList(),
                prefValues,
                getString(R.string.poster_ui_settings),
                {}
            ) { list ->
                settingsManager.edit {
                    for ((i, key) in keys.withIndex()) {
                        putBoolean(key, list.contains(i))
                    }
                }
                SearchResultBuilder.updateCache(it.context)
            }

            return@setOnPreferenceClickListener true
        }

        getPref(R.string.app_layout_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.app_layout)
            val prefValues = resources.getIntArray(R.array.app_layout_values)

            val currentLayout =
                settingsManager.getInt(getString(R.string.app_layout_key), -1)

            activity?.showBottomDialog(
                items = prefNames.toList(),
                selectedIndex = prefValues.indexOf(currentLayout),
                name = getString(R.string.app_layout),
                showApply = true,
                dismissCallback = {},
                callback = {
                    try {
                        settingsManager.edit {
                            putInt(getString(R.string.app_layout_key), prefValues[it])
                        }
                        context?.updateTv()
                        activity?.recreate()
                    } catch (e: Exception) {
                        logError(e)
                    }
                }
            )
            return@setOnPreferenceClickListener true
        }

        // Options below are saved per profile, see UiPrefs
        val offText = getString(R.string.ui_search_provider_off)
        val homeStyleNames = listOf(
            getString(R.string.ui_home_style_classic),
            getString(R.string.ui_home_style_cinematic)
        )
        val homeStyleValues = listOf(UiPrefs.HOME_CLASSIC, UiPrefs.HOME_CINEMATIC)
        val focusNames = listOf(
            getString(R.string.ui_focus_lift),
            getString(R.string.ui_focus_border)
        )
        val focusValues = listOf(UiPrefs.FOCUS_LIFT, UiPrefs.FOCUS_BORDER)
        val onOffNames = listOf(
            getString(R.string.ui_switcher_on),
            getString(R.string.ui_switcher_off)
        )
        val onOffValues = listOf("on", "off")

        fun refreshProfileSummaries() {
            getPref(R.string.ui_search_provider_key)?.summary = UiPrefs.searchProvider ?: offText
            getPref(R.string.ui_home_style_key)?.summary =
                homeStyleNames[homeStyleValues.indexOf(
                    UiPrefs.getOpt(UiPrefs.HOME_STYLE, UiPrefs.HOME_DEFAULT)
                ).coerceAtLeast(0)]
            getPref(R.string.ui_home_tiles_key)?.summary =
                onOffNames[onOffValues.indexOf(
                    UiPrefs.getOpt(UiPrefs.HOME_TILES, "on")
                ).coerceAtLeast(0)]
            getPref(R.string.ui_focus_style_key)?.summary =
                focusNames[focusValues.indexOf(
                    UiPrefs.getOpt(UiPrefs.FOCUS_STYLE, UiPrefs.FOCUS_LIFT)
                ).coerceAtLeast(0)]
            getPref(R.string.ui_provider_switcher_key)?.summary =
                onOffNames[onOffValues.indexOf(
                    UiPrefs.getOpt(UiPrefs.PROVIDER_SWITCHER, "on")
                ).coerceAtLeast(0)]
        }
        refreshProfileSummaries()

        fun pickProfileOption(
            title: Int,
            names: List<String>,
            values: List<String>,
            current: String,
            onPick: (String) -> Unit
        ) {
            activity?.showBottomDialog(
                names,
                values.indexOf(current).coerceAtLeast(0),
                getString(title),
                true,
                {}
            ) {
                onPick(values[it])
                refreshProfileSummaries()
            }
        }

        getPref(R.string.ui_search_provider_key)?.setOnPreferenceClickListener {
            val providers = (context?.filterProviderByPreferredMedia(hasHomePageIsRequired = false)
                ?: emptyList()).map { it.name }.distinct().sortedBy { it.lowercase() }
            pickProfileOption(
                R.string.ui_search_provider_settings,
                listOf(offText) + providers,
                listOf("") + providers,
                UiPrefs.searchProvider ?: ""
            ) { UiPrefs.searchProvider = it.ifBlank { null } }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.ui_home_style_key)?.setOnPreferenceClickListener {
            pickProfileOption(
                R.string.ui_home_style_settings,
                homeStyleNames,
                homeStyleValues,
                UiPrefs.getOpt(UiPrefs.HOME_STYLE, UiPrefs.HOME_DEFAULT)
            ) { UiPrefs.putOpt(UiPrefs.HOME_STYLE, it) }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.ui_home_tiles_key)?.setOnPreferenceClickListener {
            pickProfileOption(
                R.string.ui_home_tiles_settings,
                onOffNames,
                onOffValues,
                UiPrefs.getOpt(UiPrefs.HOME_TILES, "on")
            ) { UiPrefs.putOpt(UiPrefs.HOME_TILES, it) }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.ui_focus_style_key)?.setOnPreferenceClickListener {
            pickProfileOption(
                R.string.ui_focus_style_settings,
                focusNames,
                focusValues,
                UiPrefs.getOpt(UiPrefs.FOCUS_STYLE, UiPrefs.FOCUS_LIFT)
            ) { UiPrefs.putOpt(UiPrefs.FOCUS_STYLE, it) }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.ui_provider_switcher_key)?.setOnPreferenceClickListener {
            pickProfileOption(
                R.string.ui_provider_switcher_settings,
                onOffNames,
                onOffValues,
                UiPrefs.getOpt(UiPrefs.PROVIDER_SWITCHER, "on")
            ) { UiPrefs.putOpt(UiPrefs.PROVIDER_SWITCHER, it) }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.nav_labels_key)?.setOnPreferenceClickListener {
            val names = listOf(
                getString(R.string.nav_label_selected),
                getString(R.string.nav_label_always),
                getString(R.string.nav_label_hidden)
            )
            val values = listOf("selected", "labeled", "unlabeled")
            val current = settingsManager.getString(getString(R.string.nav_labels_key), "selected")
            activity?.showBottomDialog(
                names,
                values.indexOf(current).coerceAtLeast(0),
                getString(R.string.nav_labels_settings),
                true,
                {}
            ) {
                settingsManager.edit {
                    putString(getString(R.string.nav_labels_key), values[it])
                }
                activity?.recreate()
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.start_tab_key)?.setOnPreferenceClickListener {
            val names = listOf(
                getString(R.string.title_home),
                getString(R.string.title_search),
                getString(R.string.library),
                getString(R.string.title_downloads)
            )
            val values = listOf("home", "search", "library", "downloads")
            val current = settingsManager.getString(getString(R.string.start_tab_key), "home")
            activity?.showBottomDialog(
                names,
                values.indexOf(current).coerceAtLeast(0),
                getString(R.string.start_tab_settings),
                true,
                {}
            ) {
                settingsManager.edit {
                    putString(getString(R.string.start_tab_key), values[it])
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.app_theme_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.themes_names).toMutableList()
            val prefValues = resources.getStringArray(R.array.themes_names_values).toMutableList()
            val removeIncompatible = { text: String ->
                val toRemove = prefValues
                    .mapIndexed { idx, s -> if (s.startsWith(text)) idx else null }
                    .filterNotNull()
                var offset = 0
                toRemove.forEach { idx ->
                    prefNames.removeAt(idx - offset)
                    prefValues.removeAt(idx - offset)
                    offset += 1
                }
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { // remove monet on android 11 and less
                removeIncompatible("Monet")
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { // Remove system on android 9 and less
                removeIncompatible("System")
            }

            val currentLayout =
                settingsManager.getString(getString(R.string.app_theme_key), prefValues.first())

            activity?.showBottomDialog(
                prefNames.toList(),
                prefValues.indexOf(currentLayout),
                getString(R.string.app_theme_settings),
                true,
                {}
            ) {
                try {
                    settingsManager.edit {
                        putString(getString(R.string.app_theme_key), prefValues[it])
                    }
                    activity?.recreate()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }
        getPref(R.string.primary_color_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.themes_overlay_names).toMutableList()
            val prefValues =
                resources.getStringArray(R.array.themes_overlay_names_values).toMutableList()

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { // remove monet on android 11 and less
                val toRemove = prefValues
                    .mapIndexed { idx, s -> if (s.startsWith("Monet")) idx else null }
                    .filterNotNull()
                var offset = 0
                toRemove.forEach { idx ->
                    prefNames.removeAt(idx - offset)
                    prefValues.removeAt(idx - offset)
                    offset += 1
                }
            }

            val currentLayout =
                settingsManager.getString(getString(R.string.primary_color_key), prefValues.first())

            activity?.showDialog(
                prefNames.toList(),
                prefValues.indexOf(currentLayout),
                getString(R.string.primary_color_settings),
                true,
                {}
            ) {
                try {
                    settingsManager.edit {
                        putString(getString(R.string.primary_color_key), prefValues[it])
                    }
                    activity?.recreate()
                } catch (e: Exception) {
                    logError(e)
                }
            }
            return@setOnPreferenceClickListener true
        }

        getPref(R.string.pref_filter_search_quality_key)?.setOnPreferenceClickListener {
            val names = enumValues<SearchQuality>().sorted().map { it.name }
            val currentList = settingsManager.getStringSet(
                getString(R.string.pref_filter_search_quality_key),
                setOf()
            )?.map {
                it.toInt()
            } ?: listOf()

            activity?.showMultiDialog(
                names,
                currentList,
                getString(R.string.pref_filter_search_quality),
                {}
            ) { selectedList ->
                settingsManager.edit {
                    putStringSet(
                        getString(R.string.pref_filter_search_quality_key),
                        selectedList.map { it.toString() }.toMutableSet()
                    )
                }
            }

            return@setOnPreferenceClickListener true
        }

        getPref(R.string.tv_layout_clock_key)?.hideOn(PHONE or EMULATOR)

        getPref(R.string.confirm_exit_key)?.setOnPreferenceClickListener {
            val prefNames = resources.getStringArray(R.array.confirm_exit)
            val prefValues = resources.getIntArray(R.array.confirm_exit_values)
            val confirmExit = settingsManager.getInt(getString(R.string.confirm_exit_key), -1)

            activity?.showBottomDialog(
                items = prefNames.toList(),
                selectedIndex = prefValues.indexOf(confirmExit),
                name = getString(R.string.confirm_before_exiting_title),
                showApply = true,
                dismissCallback = {},
                callback = { selectedOption ->
                    settingsManager.edit {
                        putInt(getString(R.string.confirm_exit_key), prefValues[selectedOption])
                    }
                }
            )
            return@setOnPreferenceClickListener true
        }
    }
}