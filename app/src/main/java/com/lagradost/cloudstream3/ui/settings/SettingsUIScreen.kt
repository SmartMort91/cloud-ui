package com.lagradost.cloudstream3.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.integerArrayResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.lagradost.cloudstream3.CommonActivity.activity
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.ui.UiPrefs
import com.lagradost.cloudstream3.ui.subtitles.SUBTITLE_AUTO_SELECT_KEY
import com.lagradost.cloudstream3.ui.subtitles.SubtitlesFragment
import com.lagradost.cloudstream3.CloudStreamApp.Companion.setKey
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.ui.clear
import com.lagradost.cloudstream3.ui.home.HomeChildItemAdapter
import com.lagradost.cloudstream3.ui.home.ParentItemAdapter
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.settings.Globals.updateTv
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import com.lagradost.cloudstream4.compose.TV
import com.lagradost.cloudstream4.compose.isLayout
import com.lagradost.cloudstream4.rememberAppSettings
import com.lagradost.cloudstream4.theme.CloudStreamPrimaryColor
import com.lagradost.cloudstream4.theme.modeToTheme
import com.lagradost.cloudstream4.theme.perfToColor
import com.lagradost.cloudstream4.theme.perfToMode
import com.mihon.presentation.settings.Preference
import com.mihon.presentation.settings.SearchableSettings
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentMap

object SettingsUIScreen : SearchableSettings {
    @Composable
    override fun getTitleRes(): String = stringResource(R.string.category_ui)

    fun SearchQuality.toStringRes() = when (this) {
        SearchQuality.BlueRay -> R.string.quality_blueray
        SearchQuality.Cam -> R.string.quality_cam
        SearchQuality.CamRip -> R.string.quality_cam_rip
        SearchQuality.DVD -> R.string.quality_dvd
        SearchQuality.HD -> R.string.quality_hd
        SearchQuality.HQ -> R.string.quality_hq
        SearchQuality.HdCam -> R.string.quality_cam_hd
        SearchQuality.Telecine -> R.string.quality_tc
        SearchQuality.Telesync -> R.string.quality_ts
        SearchQuality.WorkPrint -> R.string.quality_workprint
        SearchQuality.SD -> R.string.quality_sd
        SearchQuality.FourK -> R.string.quality_4k
        SearchQuality.UHD -> R.string.quality_uhd
        SearchQuality.SDR -> R.string.quality_sdr
        SearchQuality.HDR -> R.string.quality_hdr
        SearchQuality.WebRip -> R.string.quality_webrip
    }

    @Composable
    fun RoundColor(color: Color) {
        Box(
            modifier = Modifier
                .padding(start = 15.dp)
                .size(20.dp)
                .border(width = 1.5.dp, shape = CircleShape, color = MaterialTheme.colorScheme.onBackground)
                .background(color, CircleShape)
        )
    }

    /** Options saved per profile, see UiPrefs */
    @Composable
    private fun profileLayoutGroup(): Preference.PreferenceGroup {
        var tick by remember { mutableIntStateOf(0) }
        val refreshKey = tick // re-read so the subtitles refresh after a pick

        val offText = stringResource(R.string.ui_search_provider_off)
        val homeNames = listOf(
            stringResource(R.string.ui_home_style_classic),
            stringResource(R.string.ui_home_style_cinematic)
        )
        val homeValues = listOf(UiPrefs.HOME_CLASSIC, UiPrefs.HOME_CINEMATIC)
        val focusNames = listOf(
            stringResource(R.string.ui_focus_lift),
            stringResource(R.string.ui_focus_border)
        )
        val focusValues = listOf(UiPrefs.FOCUS_LIFT, UiPrefs.FOCUS_BORDER)
        val onOffNames = listOf(
            stringResource(R.string.ui_switcher_on),
            stringResource(R.string.ui_switcher_off)
        )
        val onOffValues = listOf("on", "off")

        fun label(names: List<String>, values: List<String>, current: String) =
            names[values.indexOf(current).coerceAtLeast(0)]

        fun pick(
            title: String,
            names: List<String>,
            values: List<String>,
            current: String,
            onPick: (String) -> Unit
        ) {
            activity?.showBottomDialog(
                names,
                values.indexOf(current).coerceAtLeast(0),
                title,
                true,
                {}
            ) {
                onPick(values[it])
                tick++
            }
        }

        val searchTitle = stringResource(R.string.ui_search_provider_settings)
        val homeTitle = stringResource(R.string.ui_home_style_settings)
        val tilesTitle = stringResource(R.string.ui_home_tiles_settings)
        val focusTitle = stringResource(R.string.ui_focus_style_settings)
        val switcherTitle = stringResource(R.string.ui_provider_switcher_settings)

        val subLangTitle = stringResource(R.string.subs_auto_select_language)
        val noneLabel = stringResource(R.string.none)
        val subLangs = listOf(Pair(noneLabel, noneLabel)) +
                SubtitleHelper.languages
                    .map { Pair(it.IETF_tag, it.nameNextToFlagEmoji()) }
                    .sortedBy { it.second.substringAfter("\u00a0").lowercase() }
        val subLangTags = subLangs.map { it.first }
        val subLangNames = subLangs.map { it.second }

        val navKey = stringResource(R.string.nav_labels_key)
        val startKey = stringResource(R.string.start_tab_key)
        val navTitle = stringResource(R.string.nav_labels_settings)
        val startTitle = stringResource(R.string.start_tab_settings)
        val navNames = listOf(
            stringResource(R.string.nav_label_selected),
            stringResource(R.string.nav_label_always),
            stringResource(R.string.nav_label_hidden)
        )
        val navValues = listOf("selected", "labeled", "unlabeled")
        val startNames = listOf(
            stringResource(R.string.title_home),
            stringResource(R.string.title_search),
            stringResource(R.string.library),
            stringResource(R.string.title_downloads)
        )
        val startValues = listOf("home", "search", "library", "downloads")
        fun sharedString(key: String, default: String): String =
            activity?.let { PreferenceManager.getDefaultSharedPreferences(it).getString(key, default) }
                ?: default

        return Preference.PreferenceGroup(
            title = stringResource(R.string.pref_category_profile_layout),
            preferenceItems = persistentListOf(
                Preference.PreferenceItem.TextPreference(
                    title = searchTitle,
                    subtitle = UiPrefs.searchProvider ?: offText,
                    onClick = {
                        val providers = (activity?.filterProviderByPreferredMedia(
                            hasHomePageIsRequired = false
                        ) ?: emptyList()).map { it.name }.distinct().sortedBy { it.lowercase() }
                        val before = UiPrefs.searchProvider
                        pick(
                            searchTitle,
                            listOf(offText) + providers,
                            listOf("") + providers,
                            before ?: ""
                        ) {
                            UiPrefs.searchProvider = it.ifBlank { null }
                            // Switching between normal and banner search changes the layout
                            if ((before == null) != (UiPrefs.searchProvider == null)) {
                                safe { activity?.recreate() }
                            }
                        }
                    }
                ),
                Preference.PreferenceItem.TextPreference(
                    title = homeTitle,
                    subtitle = label(
                        homeNames, homeValues,
                        UiPrefs.getOpt(UiPrefs.HOME_STYLE, UiPrefs.HOME_DEFAULT)
                    ),
                    onClick = {
                        pick(
                            homeTitle, homeNames, homeValues,
                            UiPrefs.getOpt(UiPrefs.HOME_STYLE, UiPrefs.HOME_DEFAULT)
                        ) { UiPrefs.putOpt(UiPrefs.HOME_STYLE, it) }
                    }
                ),
                Preference.PreferenceItem.TextPreference(
                    title = tilesTitle,
                    subtitle = label(
                        onOffNames, onOffValues, UiPrefs.getOpt(UiPrefs.HOME_TILES, "on")
                    ),
                    onClick = {
                        pick(
                            tilesTitle, onOffNames, onOffValues,
                            UiPrefs.getOpt(UiPrefs.HOME_TILES, "on")
                        ) { UiPrefs.putOpt(UiPrefs.HOME_TILES, it) }
                    }
                ),
                Preference.PreferenceItem.TextPreference(
                    title = focusTitle,
                    subtitle = label(
                        focusNames, focusValues,
                        UiPrefs.getOpt(UiPrefs.FOCUS_STYLE, UiPrefs.FOCUS_LIFT)
                    ),
                    onClick = {
                        pick(
                            focusTitle, focusNames, focusValues,
                            UiPrefs.getOpt(UiPrefs.FOCUS_STYLE, UiPrefs.FOCUS_LIFT)
                        ) { UiPrefs.putOpt(UiPrefs.FOCUS_STYLE, it) }
                    }
                ),
                Preference.PreferenceItem.TextPreference(
                    title = switcherTitle,
                    subtitle = label(
                        onOffNames, onOffValues, UiPrefs.getOpt(UiPrefs.PROVIDER_SWITCHER, "on")
                    ),
                    onClick = {
                        pick(
                            switcherTitle, onOffNames, onOffValues,
                            UiPrefs.getOpt(UiPrefs.PROVIDER_SWITCHER, "on")
                        ) { UiPrefs.putOpt(UiPrefs.PROVIDER_SWITCHER, it) }
                    }
                ),
                Preference.PreferenceItem.TextPreference(
                    title = subLangTitle,
                    subtitle = subLangNames.getOrNull(
                        subLangTags.indexOf(SubtitlesFragment.getAutoSelectLanguageTagIETF())
                    ) ?: noneLabel,
                    onClick = {
                        activity?.showBottomDialog(
                            subLangNames,
                            subLangTags.indexOf(SubtitlesFragment.getAutoSelectLanguageTagIETF()),
                            subLangTitle,
                            true,
                            {}
                        ) { index ->
                            setKey(SUBTITLE_AUTO_SELECT_KEY, subLangTags[index])
                            tick++
                        }
                    }
                ),
                Preference.PreferenceItem.TextPreference(
                    title = navTitle,
                    subtitle = label(navNames, navValues, sharedString(navKey, "selected")),
                    onClick = {
                        pick(navTitle, navNames, navValues, sharedString(navKey, "selected")) {
                            activity?.let { a ->
                                PreferenceManager.getDefaultSharedPreferences(a).edit { putString(navKey, it) }
                            }
                            safe { activity?.recreate() }
                        }
                    }
                ),
                Preference.PreferenceItem.TextPreference(
                    title = startTitle,
                    subtitle = label(startNames, startValues, sharedString(startKey, "home")),
                    onClick = {
                        pick(startTitle, startNames, startValues, sharedString(startKey, "home")) {
                            activity?.let { a ->
                                PreferenceManager.getDefaultSharedPreferences(a).edit { putString(startKey, it) }
                            }
                        }
                    }
                ),
            )
        )
    }

    @Composable
    override fun getPreferences(): List<Preference> {
        val settings = rememberAppSettings()

        return persistentListOf(
            profileLayoutGroup(),
            Preference.PreferenceGroup(
                title = stringResource(R.string.pref_category_looks),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.ListPreference(
                        preference = settings.ui.primaryColor,
                        icon = painterResource(R.drawable.colors_24px),
                        title = stringResource(R.string.primary_color_settings),
                        entries = stringArrayResource(R.array.themes_overlay_names_values).zip(
                            stringArrayResource(R.array.themes_overlay_names)
                        ).toMap().toPersistentMap().mutate { map ->
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { // remove monet on android 11 and less
                                map.remove("Monet")
                                map.remove("Monet2")
                            }
                        },
                        iconProvider = { k, _ ->
                            val color = perfToColor(k)
                            RoundColor(color.color)
                        },
                        onValueChanged = { newValue ->
                            settings.ui.primaryColor.set(newValue) // We need to set before we recreate
                            safe {
                                activity?.recreate()
                            }
                            return@ListPreference false
                        }),
                    Preference.PreferenceItem.ListPreference(
                        preference = settings.ui.theme,
                        icon = painterResource(R.drawable.palette_24px),
                        title = stringResource(R.string.app_theme_settings),
                        entries = stringArrayResource(R.array.themes_names_values).zip(
                            stringArrayResource(R.array.themes_names)
                        ).toMap().toPersistentMap().mutate { map ->
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) { // remove monet on android 11 and less
                                map.remove("Monet")
                            }
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) { // Remove system on android 9 and less
                                map.remove("System")
                            }
                        },
                        iconProvider = { k, _ ->
                            val theme = modeToTheme(perfToMode(k), CloudStreamPrimaryColor.NORMAL)
                            RoundColor(theme.background)
                        },
                        onValueChanged = { newValue ->
                            settings.ui.theme.set(newValue) // We need to set before we recreate
                            safe {
                                activity?.recreate()
                            }
                            return@ListPreference false
                        }),
                    Preference.PreferenceItem.ListPreference(
                        preference = settings.ui.layout,
                        icon = painterResource(R.drawable.responsive_layout_24px),
                        title = stringResource(R.string.app_layout),
                        entries = integerArrayResource(R.array.app_layout_values).zip(
                            stringArrayResource(R.array.app_layout)
                        ).toMap().toPersistentMap(),
                        onValueChanged = { newValue ->
                            settings.ui.layout.set(newValue) // We need to set before we recreate
                            safe {
                                activity?.updateTv()
                                activity?.recreate()
                            }
                            return@ListPreference false
                        }),
                )
            ),

            Preference.PreferenceGroup(
                title = stringResource(R.string.pref_category_ui_features),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SliderPreference(
                        preference = settings.ui.overscanDp,
                        title = stringResource(R.string.overscan_settings),
                        subtitle = stringResource(R.string.overscan_settings_des),
                        valueRange = 0..100,
                        icon = painterResource(R.drawable.arrows_input_24px),
                        enabled = isLayout(TV),
                        onValueChanged = { newValue ->
                            settings.ui.overscanDp.set(newValue)
                            val padding = newValue.toPx
                            (activity as? MainActivity)?.binding?.homeRoot?.setPadding(
                                padding, padding, padding, padding
                            )
                            return@SliderPreference false
                        }),

                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.advancedSearch,
                        title = stringResource(R.string.advanced_search),
                        subtitle = stringResource(R.string.advanced_search_des),
                        icon = painterResource(R.drawable.search_icon)
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.searchSuggestions,
                        title = stringResource(R.string.search_suggestions),
                        subtitle = stringResource(R.string.search_suggestions_des),
                        icon = painterResource(R.drawable.tooltip_24px)
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.trailersEnabled,
                        title = stringResource(R.string.show_trailers_settings),
                        icon = painterResource(R.drawable.baseline_theaters_24)
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.kitsuPostersEnabled,
                        title = stringResource(R.string.kitsu_settings),
                        icon = painterResource(R.drawable.kitsu_icon)
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.castEnabled,
                        title = stringResource(R.string.show_cast_in_details),
                        icon = painterResource(R.drawable.face_24px)
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.fillersEnabled,
                        title = stringResource(R.string.show_fillers_settings),
                        icon = painterResource(R.drawable.skip_next_24px)
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.showMetadataOverlay,
                        title = stringResource(R.string.show_player_metadata_overlay),
                        icon = painterResource(R.drawable.metadata_overlay_icon)
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.showClock,
                        title = stringResource(R.string.tv_layout_clock_settings),
                        subtitle = stringResource(R.string.tv_layout_clock_settings_des),
                        icon = painterResource(R.drawable.ic_baseline_clock_24),
                        enabled = isLayout(TV),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.randomButtonEnabled,
                        title = stringResource(R.string.random_button_settings),
                        subtitle = stringResource(R.string.random_button_settings_desc),
                        icon = painterResource(R.drawable.shuffle_24px)
                    ),
                    Preference.PreferenceItem.ListPreference(
                        preference = settings.ui.confirmExit,
                        title = stringResource(R.string.confirm_before_exiting_title),
                        subtitle = stringResource(R.string.confirm_before_exiting_desc),
                        icon = painterResource(R.drawable.ic_baseline_exit_24),
                        entries = integerArrayResource(R.array.confirm_exit_values).zip(
                            stringArrayResource(R.array.confirm_exit)
                        ).toMap()
                    ),
                )
            ),

            Preference.PreferenceGroup(
                title = stringResource(R.string.search_poster_img_des),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.MultiSelectListPreference(
                        preference = settings.ui.filterQuality,
                        title = stringResource(R.string.pref_filter_search_quality),
                        icon = painterResource(R.drawable.filter_alt_24px),
                        entries = SearchQuality.entries.associateWith { stringResource(it.toStringRes()) }),
                    Preference.PreferenceItem.MultiSelectListPreference(
                        title = stringResource(R.string.display_subbed_dubbed_settings),
                        icon = painterResource(R.drawable.audio_capture_24px),
                        preference = settings.provider.displayDubSub,
                        entries = mapOf(
                            DubStatus.None.name to stringResource(R.string.none),
                            DubStatus.Dubbed.name to stringResource(R.string.app_dubbed_text),
                            DubStatus.Subbed.name to stringResource(R.string.app_subbed_text),
                        )),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.bottomTitle,
                        title = stringResource(R.string.bottom_title_settings),
                        subtitle = stringResource(R.string.bottom_title_settings_des),
                        icon = painterResource(R.drawable.title_24px)
                    ),
                    Preference.PreferenceItem.SliderPreference(
                        preference = settings.ui.posterSize,
                        title = stringResource(R.string.poster_size_settings),
                        subtitle = stringResource(R.string.poster_size_settings_des),
                        valueRange = 0..15,
                        icon = painterResource(R.drawable.baseline_grid_view_24),
                        onValueChanged = { newValue ->
                            HomeChildItemAdapter.sharedPool.clear()
                            ParentItemAdapter.sharedPool.clear()
                            SearchAdapter.sharedPool.clear()
                            activity?.let { HomeChildItemAdapter.updatePosterSize(it, newValue) }
                            settings.ui.posterSize.set(newValue)
                            return@SliderPreference false
                        }),
                ),
            ),
            Preference.PreferenceGroup(
                title = stringResource(R.string.poster_ui_settings),
                preferenceItems = persistentListOf(
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.posterShowRating,
                        title = stringResource(R.string.show_rating),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.posterShowEpisode,
                        title = stringResource(R.string.show_episode_text),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.posterShowTitle,
                        title = stringResource(R.string.show_title),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.posterShowHd,
                        title = stringResource(R.string.show_hd),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.posterShowDub,
                        title = stringResource(R.string.show_dub),
                    ),
                    Preference.PreferenceItem.SwitchPreference(
                        preference = settings.ui.posterShowSub,
                        title = stringResource(R.string.show_sub),
                    ),
                )
            )
        )
    }
}