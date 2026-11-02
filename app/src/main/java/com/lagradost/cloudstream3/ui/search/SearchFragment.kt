package com.lagradost.cloudstream3.ui.search

import android.app.Activity
import android.content.Intent
import android.content.DialogInterface
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.AbsListView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.doOnLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.AllLanguagesName
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.CloudStreamApp.Companion.removeKey
import com.lagradost.cloudstream3.CloudStreamApp.Companion.removeKeys
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.MainActivity.Companion.afterPluginsLoadedEvent
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.databinding.FragmentSearchBinding
import com.lagradost.cloudstream3.databinding.HomeSelectMainpageBinding
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.observe
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.ui.BaseAdapter
import com.lagradost.cloudstream3.ui.BaseFragment
import com.lagradost.cloudstream3.ui.home.HomeFragment
import com.lagradost.cloudstream3.ui.home.TagBrowse
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.bindChips
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.currentSpan
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.loadHomepageList
import com.lagradost.cloudstream3.ui.home.HomeFragment.Companion.updateChips
import com.lagradost.cloudstream3.ui.home.HomeViewModel
import com.lagradost.cloudstream3.ui.home.ParentItemAdapter
import com.lagradost.cloudstream3.ui.result.FOCUS_SELF
import com.lagradost.cloudstream3.ui.result.setLinearListLayout
import com.lagradost.cloudstream3.ui.UiPrefs
import com.lagradost.cloudstream3.ui.setRecycledViewPool
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLandscape
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.AppContextUtils.filterProviderByPreferredMedia
import com.lagradost.cloudstream3.utils.AppContextUtils.filterSearchResultByFilmQuality
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiProviderLangSettings
import com.lagradost.cloudstream3.utils.AppContextUtils.getApiSettings
import com.lagradost.cloudstream3.utils.AppContextUtils.ownHide
import com.lagradost.cloudstream3.utils.AppContextUtils.ownShow
import com.lagradost.cloudstream3.utils.AppContextUtils.setDefaultFocus
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.lagradost.cloudstream3.utils.DataStoreHelper.currentAccount
import com.lagradost.cloudstream3.utils.SingleSelectionHelper.showBottomDialog
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.attachBackPressedCallback
import com.lagradost.cloudstream3.utils.BackPressedCallbackHelper.detachBackPressedCallback
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.dismissSafe
import com.lagradost.cloudstream3.utils.UIHelper.fixSystemBarsPadding
import com.lagradost.cloudstream3.utils.UIHelper.getSpanCount
import com.lagradost.cloudstream3.utils.UIHelper.hideKeyboard
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock

class SearchFragment : BaseFragment<FragmentSearchBinding>(
    BaseFragment.BindingCreator.Bind(FragmentSearchBinding::bind)
) {
    companion object {
        fun List<SearchResponse>.filterSearchResponse(): List<SearchResponse> {
            return this.filter { response ->
                if (response is AnimeSearchResponse) {
                    val status = response.dubStatus
                    (status.isNullOrEmpty()) || (status.any {
                        APIRepository.dubStatusActive.contains(it)
                    })
                } else {
                    true
                }
            }
        }

        const val SEARCH_QUERY = "search_query"

        fun newInstance(query: String): Bundle {
            return Bundle().apply {
                if (query.isNotBlank()) putString(SEARCH_QUERY, query)
            }
        }
    }

    private val searchViewModel: SearchViewModel by activityViewModels()
    private var bottomSheetDialog: BottomSheetDialog? = null

    /** Name of the one provider to search in (banner search layout), null if the mode is off */
    private val bannerProvider: String? get() = UiPrefs.searchProvider

    /** True when the on-screen keyboard (TV and emulator, banner search) is in use */
    private var keyboardMode = false
    private val liveHandler = Handler(Looper.getMainLooper())
    private var liveRunnable: Runnable? = null

    private val speechRecognizerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                if (!matches.isNullOrEmpty()) {
                    val recognizedText = matches[0]
                    binding?.mainSearch?.setQuery(recognizedText, true)
                }
            }
        }

    override fun pickLayout(): Int? =
        if (isLayout(TV or EMULATOR)) R.layout.fragment_search_tv else R.layout.fragment_search

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        activity?.window?.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )
        bottomSheetDialog?.ownShow()
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onDestroyView() {
        liveRunnable?.let { liveHandler.removeCallbacks(it) }
        keyboardMode = false
        hideKeyboard()
        bottomSheetDialog?.ownHide()
        activity?.detachBackPressedCallback("SearchFragment")
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()
        searchViewModel.clearSuggestions()
        afterPluginsLoadedEvent += ::reloadRepos
    }

    override fun onStop() {
        super.onStop()
        afterPluginsLoadedEvent -= ::reloadRepos
    }

    var selectedSearchTypes = mutableListOf<TvType>()
    var selectedApis = mutableSetOf<String>()

    /**
     * Will filter all providers by preferred media and selectedSearchTypes.
     * If that results in no available providers then only filter
     * providers by preferred media
     **/
    fun search(query: String?, saveToHistory: Boolean = true) {
        if (query == null) return
        searchViewModel.clearSuggestions()
        // don't resume state from prev search
        (binding?.searchMasterRecycler?.adapter as? BaseAdapter<*, *>)?.clearState()

        // Single-provider mode: only that provider is searched, results are shown as banners
        bannerProvider?.let { provider ->
            searchViewModel.searchAndCancel(
                query = query,
                providersActive = setOf(provider),
                saveToHistory = saveToHistory
            )
            return
        }
        context?.let { ctx ->
            val default = enumValues<TvType>().sorted().filter { it != TvType.NSFW }
                .map { it.ordinal.toString() }.toSet()
            val preferredTypes = (PreferenceManager.getDefaultSharedPreferences(ctx)
                .getStringSet(this.getString(R.string.prefer_media_type_key), default)
                ?.ifEmpty { default } ?: default)
                .mapNotNull { it.toIntOrNull() ?: return@mapNotNull null }

            val settings = ctx.getApiSettings()

            val notFilteredBySelectedTypes = selectedApis.filter { name ->
                settings.contains(name)
            }.map { name ->
                name to getApiFromNameNull(name)?.supportedTypes
            }.filter { (_, types) ->
                types?.any { preferredTypes.contains(it.ordinal) } == true
            }

            searchViewModel.searchAndCancel(
                query = query,
                providersActive = notFilteredBySelectedTypes.filter { (_, types) ->
                    types?.any { selectedSearchTypes.contains(it) } == true
                }.ifEmpty { notFilteredBySelectedTypes }.map { it.first }.toSet()
            )
        }
    }

    // Null if defined as a variable
    // This needs to be run after view created

    private fun reloadRepos(success: Boolean = false) = main {
        searchViewModel.reloadRepos()
        context?.filterProviderByPreferredMedia()?.let { validAPIs ->
            bindChips(
                binding?.tvtypesChipsScroll?.tvtypesChips,
                selectedSearchTypes,
                validAPIs.flatMap { api -> api.supportedTypes }.distinct()
            ) { list ->
                if (selectedSearchTypes.toSet() != list.toSet()) {
                    DataStoreHelper.searchPreferenceTags = list
                    selectedSearchTypes.clear()
                    selectedSearchTypes.addAll(list)
                    search(binding?.mainSearch?.query?.toString())
                }
            }
        }
    }

    private fun scheduleLiveSearch(text: String) {
        liveRunnable?.let { liveHandler.removeCallbacks(it) }
        if (text.length < 2) return
        val runnable = Runnable { search(text, saveToHistory = false) }
        liveRunnable = runnable
        liveHandler.postDelayed(runnable, 450)
    }

    /** Pick the single provider used by the banner search, or turn the mode off */
    private fun pickBannerProvider(binding: FragmentSearchBinding) {
        val ctx = context ?: return
        val previous = UiPrefs.searchProvider
        val providers = ctx.filterProviderByPreferredMedia(hasHomePageIsRequired = false)
            .map { it.name }.distinct().sortedBy { it.lowercase() }
        val names = listOf(getString(R.string.ui_search_provider_off)) + providers
        val values = listOf("") + providers
        activity?.showBottomDialog(
            names,
            values.indexOf(previous ?: "").coerceAtLeast(0),
            getString(R.string.ui_search_provider_settings),
            true,
            {}
        ) { index ->
            val picked = values[index].ifBlank { null }
            UiPrefs.searchProvider = picked
            if ((picked == null) != (previous == null)) {
                // Layout changes between banners and the normal search, so rebuild the screen
                activity?.recreate()
            } else if (picked != previous) {
                search(binding.mainSearch.query.toString())
            }
        }
    }

    /**
     * Builds an on-screen keyboard on the left and puts the results on the right.
     * Done in code so the layout files (and their shared view binding) stay untouched.
     */
    private fun setupTvKeyboard(binding: FragmentSearchBinding) {
        val ctx = binding.root.context
        val results = binding.searchAutofitResults
        val history = binding.searchHistoryRecycler
        val parent = results.parent as? ViewGroup ?: return
        if (history.parent != parent) return
        keyboardMode = true

        val startMargin = (results.layoutParams as? ViewGroup.MarginLayoutParams)?.marginStart ?: 0
        val index = parent.indexOfChild(results)
        parent.removeView(results)
        parent.removeView(history)

        val matchParent = ViewGroup.LayoutParams.MATCH_PARENT

        // Results replace the history list as soon as something is typed
        val rightPane = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(0, matchParent, 1f)
        }
        rightPane.addView(history, FrameLayout.LayoutParams(matchParent, matchParent))
        rightPane.addView(results, FrameLayout.LayoutParams(matchParent, matchParent))

        val keyboard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(320.toPx, matchParent)
            setPadding(12.toPx, 12.toPx, 12.toPx, 12.toPx)
            setBackgroundColor(ctx.colorFromAttribute(R.attr.primaryBlackBackground))
        }

        val keyColor = ctx.colorFromAttribute(R.attr.boxItemBackground)
        val keyText = ctx.colorFromAttribute(R.attr.textColor)
        val activeColor = ctx.colorFromAttribute(R.attr.colorPrimary)
        // Readable text on whatever accent color is selected
        val activeText =
            if (ColorUtils.calculateLuminance(activeColor) > 0.5) Color.BLACK else Color.WHITE

        fun keyBackground(color: Int) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = 8.toPx.toFloat()
        }

        fun makeKey(label: String, textSizeSp: Float, onClick: () -> Unit): TextView =
            TextView(ctx).apply {
                text = label
                textSize = textSizeSp
                gravity = Gravity.CENTER
                isFocusable = true
                isClickable = true
                id = View.generateViewId()
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), keyBackground(activeColor))
                    addState(intArrayOf(android.R.attr.state_pressed), keyBackground(activeColor))
                    addState(intArrayOf(), keyBackground(keyColor))
                }
                setTextColor(
                    ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_focused),
                            intArrayOf(android.R.attr.state_pressed),
                            intArrayOf()
                        ),
                        intArrayOf(activeText, activeText, keyText)
                    )
                )
                layoutParams = LinearLayout.LayoutParams(0, matchParent, 1f).apply {
                    setMargins(3.toPx, 3.toPx, 3.toPx, 3.toPx)
                }
                setOnClickListener { onClick() }
            }

        fun addRow(vararg keys: TextView) {
            // Rows share the available height so every key fits on short screens
            keyboard.addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(matchParent, 0, 1f)
                keys.forEach { addView(it) }
            })
        }

        fun currentQuery() = binding.mainSearch.query.toString()

        addRow(
            makeKey("Space", 14f) { binding.mainSearch.setQuery(currentQuery() + " ", false) },
            makeKey("Delete", 14f) { binding.mainSearch.setQuery(currentQuery().dropLast(1), false) },
            makeKey("Search", 14f) { binding.mainSearch.setQuery(currentQuery(), true) },
        )

        var sideKey: TextView? = null
        var firstKey: TextView? = null
        for (rowChars in listOf("abcdef", "ghijkl", "mnopqr", "stuvwx", "yz1234", "567890")) {
            val keys = rowChars.map { ch ->
                makeKey(ch.toString(), 18f) {
                    binding.mainSearch.setQuery(currentQuery() + ch, false)
                }.also {
                    if (ch == 'a') firstKey = it
                    if (ch == 'l') sideKey = it
                }
            }
            addRow(*keys.toTypedArray())
        }

        // Moving left from the banners goes to the keyboard instead of the navigation rail
        sideKey?.let {
            results.nextFocusLeftId = it.id
            history.nextFocusLeftId = it.id
        }

        val body = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(matchParent, matchParent).apply {
                marginStart = startMargin
            }
            addView(keyboard)
            addView(rightPane)
        }
        parent.addView(body, index)

        // No system keyboard popping up on top of ours
        activity?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        binding.searchRoot.findViewById<TextView>(androidx.appcompat.R.id.search_src_text)
            ?.setShowSoftInputOnFocus(false)

        body.post { firstKey?.requestFocus() }
    }

    override fun fixLayout(view: View) {
        fixSystemBarsPadding(
            view,
            padBottom = isLandscape(),
            padLeft = isLayout(TV or EMULATOR)
        )

        // Fix grid
        currentSpan = view.context.getSpanCount()
        binding?.searchAutofitResults?.spanCount =
            if (useBannerCards(view.context)) bannerSpan(view.context) else currentSpan
        HomeFragment.configEvent.invoke()
    }

    /** Number of banner columns, sized so a banner is never smaller than ~190dp wide */
    private fun bannerSpan(context: android.content.Context): Int {
        val dm = context.resources.displayMetrics
        val screenDp = dm.widthPixels / dm.density
        // the on-screen keyboard and navigation rail take a fixed part of the width
        val paneDp = if (keyboardMode) screenDp - 320f - 90f else screenDp - 90f
        return (paneDp / 190f).toInt().coerceIn(2, 4)
    }

    @Suppress("UNUSED_PARAMETER")
    private fun useBannerCards(context: android.content.Context): Boolean =
        bannerProvider != null &&
                (isLayout(TV or EMULATOR) || isLandscape())

    override fun onBindingCreated(
        binding: FragmentSearchBinding,
        savedInstanceState: Bundle?
    ) {
        reloadRepos()
        binding.apply {
            val isBanner = bannerProvider != null
            // Wide banners suit TV and big screens, on a phone posters crop badly as banners
            val wideCards = useBannerCards(root.context)
            if (!isLayout(TV or EMULATOR)) {
                // Keep the last row clear of the floating navigation bar
                searchAutofitResults.clipToPadding = false
                searchAutofitResults.setPadding(
                    searchAutofitResults.paddingLeft,
                    searchAutofitResults.paddingTop,
                    searchAutofitResults.paddingRight,
                    (96 * root.context.resources.displayMetrics.density).toInt()
                )
            }
            val adapter =
                SearchAdapter(
                    searchAutofitResults,
                    isHorizontal = wideCards,
                    forceTitle = isBanner,
                ) { callback ->
                    SearchHelper.handleSearchClickCallback(callback)
                }

            if (!isLayout(TV or EMULATOR)) {
                // Quick tag (genre) filters under the search bar
                var header: android.view.ViewParent? = mainSearch.parent
                while (header != null &&
                    !(header is LinearLayout && header.orientation == LinearLayout.VERTICAL)
                ) header = header.parent
                (header as? LinearLayout)?.addView(
                    TagBrowse.buildTagRow(root.context) {
                        bannerProvider
                            ?: DataStoreHelper.currentHomePage
                            ?: root.context.filterProviderByPreferredMedia(false).firstOrNull()?.name
                    }
                )
            }

            searchRoot.findViewById<TextView>(androidx.appcompat.R.id.search_src_text)?.tag =
                "tv_no_focus_tag"
            searchAutofitResults.setRecycledViewPool(SearchAdapter.sharedPool)
            searchAutofitResults.adapter = adapter
            searchLoadingBar.alpha = 0f
        }

        if (bannerProvider != null) {
            // Provider filter chips make no sense when only one provider is searched
            binding.tvtypesChipsScroll.root.isVisible = false
            if (isLayout(TV or EMULATOR)) setupTvKeyboard(binding)
        }

        binding.voiceSearch.setOnClickListener { searchView ->
            searchView?.context?.let { ctx ->
                try {
                    if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
                        showToast(R.string.speech_recognition_unavailable)
                    } else {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(
                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                            )
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                            putExtra(
                                RecognizerIntent.EXTRA_PROMPT,
                                ctx.getString(R.string.begin_speaking)
                            )
                        }
                        speechRecognizerLauncher.launch(intent)
                    }
                } catch (_: Throwable) {
                    // launch may throw
                    showToast(R.string.speech_recognition_unavailable)
                }
            }
        }

        val searchExitIcon =
            binding.mainSearch.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)

        selectedApis = DataStoreHelper.searchPreferenceProviders.toMutableSet()

        binding.searchFilter.setOnClickListener { searchView ->
            if (bannerProvider != null) {
                pickBannerProvider(binding)
                return@setOnClickListener
            }
            searchView?.context?.let { ctx ->
                val validAPIs = ctx.filterProviderByPreferredMedia(hasHomePageIsRequired = false)
                var currentValidApis = listOf<MainAPI>()
                val currentSelectedApis = if (selectedApis.isEmpty()) validAPIs.map { it.name }
                    .toMutableSet() else selectedApis

                val builder =
                    BottomSheetDialog(ctx)

                builder.behavior.state = BottomSheetBehavior.STATE_EXPANDED

                val selectMainpageBinding: HomeSelectMainpageBinding =
                    HomeSelectMainpageBinding.inflate(
                        builder.layoutInflater,
                        null,
                        false
                    )
                builder.setContentView(selectMainpageBinding.root)
                builder.show()
                builder.let { dialog ->
                    val previousSelectedApis = selectedApis.toSet()
                    val previousSelectedSearchTypes = selectedSearchTypes.toSet()

                    val isMultiLang = ctx.getApiProviderLangSettings().let { set ->
                        set.size > 1 || set.contains(AllLanguagesName)
                    }

                    val cancelBtt = dialog.findViewById<MaterialButton>(R.id.cancel_btt)
                    val applyBtt = dialog.findViewById<MaterialButton>(R.id.apply_btt)

                    val listView = dialog.findViewById<ListView>(R.id.listview1)
                    val arrayAdapter = ArrayAdapter<String>(ctx, R.layout.sort_bottom_single_choice)
                    listView?.adapter = arrayAdapter
                    listView?.choiceMode = AbsListView.CHOICE_MODE_MULTIPLE

                    listView?.setOnItemClickListener { _, _, i, _ ->
                        if (currentValidApis.isNotEmpty()) {
                            val api = currentValidApis[i].name
                            if (currentSelectedApis.contains(api)) {
                                listView.setItemChecked(i, false)
                                currentSelectedApis -= api
                            } else {
                                listView.setItemChecked(i, true)
                                currentSelectedApis += api
                            }
                        }
                    }

                    fun updateList(types: List<TvType>) {
                        DataStoreHelper.searchPreferenceTags = types

                        arrayAdapter.clear()
                        currentValidApis = validAPIs.filter { api ->
                            api.supportedTypes.any {
                                types.contains(it)
                            }
                        }.sortedBy { it.name.lowercase() }

                        val names = currentValidApis.map {
                            if (isMultiLang) "${
                                SubtitleHelper.getFlagFromIso(
                                    it.lang
                                )?.plus(" ") ?: ""
                            }${it.name}" else it.name
                        }
                        for ((index, api) in currentValidApis.map { it.name }.withIndex()) {
                            listView?.setItemChecked(index, currentSelectedApis.contains(api))
                        }

                        //arrayAdapter.notifyDataSetChanged()
                        arrayAdapter.addAll(names)
                        arrayAdapter.notifyDataSetChanged()
                    }

                    bindChips(
                        selectMainpageBinding.tvtypesChipsScroll.tvtypesChips,
                        selectedSearchTypes,
                        validAPIs.flatMap { api -> api.supportedTypes }.distinct()
                    ) { list ->
                        updateList(list)

                        // refresh selected chips in main chips
                        if (selectedSearchTypes.toSet() != list.toSet()) {
                            selectedSearchTypes.clear()
                            selectedSearchTypes.addAll(list)
                            updateChips(
                                binding.tvtypesChipsScroll.tvtypesChips,
                                selectedSearchTypes
                            )

                        }
                    }


                    cancelBtt?.setOnClickListener {
                        dialog.dismissSafe()
                    }

                    cancelBtt?.setOnClickListener {
                        dialog.dismissSafe()
                    }

                    applyBtt?.setOnClickListener {
                        //if (currentApiName != selectedApiName) {
                        //    currentApiName?.let(callback)
                        //}
                        dialog.dismissSafe()
                    }

                    dialog.setOnDismissListener {
                        DataStoreHelper.searchPreferenceProviders = currentSelectedApis.toList()
                        selectedApis = currentSelectedApis

                        // run search when dialog is close
                        if (previousSelectedApis != selectedApis.toSet() || previousSelectedSearchTypes != selectedSearchTypes.toSet()) {
                            search(binding.mainSearch.query.toString())
                        }
                    }
                    updateList(selectedSearchTypes.toList())
                }
            }
        }

        val settingsManager = context?.let { PreferenceManager.getDefaultSharedPreferences(it) }
        val isAdvancedSearch = settingsManager?.getBoolean("advanced_search", true) ?: true
        val isSearchSuggestionsEnabled = settingsManager?.getBoolean("search_suggestions_enabled", true) ?: true

        selectedSearchTypes = DataStoreHelper.searchPreferenceTags.toMutableList()

        if (!isLayout(PHONE)) {
            binding.searchFilter.isFocusable = true
            binding.searchFilter.isFocusableInTouchMode = true
        }

        // Hide suggestions when search view loses focus (phone only)
        if (isLayout(PHONE)) {
            binding.mainSearch.setOnQueryTextFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    searchViewModel.clearSuggestions()
                }
            }
        }


        binding.mainSearch.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                search(query)
                searchViewModel.clearSuggestions()

                binding.mainSearch.let {
                    hideKeyboard(it)
                }

                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                //searchViewModel.quickSearch(newText)
                val showHistory = newText.isBlank()
                if (showHistory) {
                    searchViewModel.clearSearch()
                    searchViewModel.updateHistory()
                    searchViewModel.clearSuggestions()
                } else {
                    // Fetch suggestions when user is typing (if enabled)
                    if (isSearchSuggestionsEnabled && !keyboardMode) {
                        searchViewModel.fetchSuggestions(newText)
                    }
                    // The on-screen keyboard searches while you type
                    if (keyboardMode) scheduleLiveSearch(newText)
                }
                val isBanner = bannerProvider != null
                binding.apply {
                    searchHistoryRecycler.isVisible = showHistory
                    searchMasterRecycler.isVisible =
                        !showHistory && isAdvancedSearch && !isBanner
                    searchAutofitResults.isVisible =
                        !showHistory && (!isAdvancedSearch || isBanner)
                    // Hide suggestions when showing history or showing search results
                    searchSuggestionsRecycler.isVisible =
                        !showHistory && isSearchSuggestionsEnabled && !keyboardMode
                }

                return true
            }
        })

        observe(searchViewModel.searchResponse) {
            when (it) {
                is Resource.Success -> {
                    it.value.let { data ->
                        val list = data.list
                        if (list.isNotEmpty() || bannerProvider != null) {
                            (binding.searchAutofitResults.adapter as? SearchAdapter)?.submitList(
                                list
                            )
                        }
                    }
                    searchExitIcon?.alpha = 1f
                    binding.searchLoadingBar.alpha = 0f
                }

                is Resource.Failure -> {
                    // Toast.makeText(activity, "Server error", Toast.LENGTH_LONG).show()
                    searchExitIcon?.alpha = 1f
                    binding.searchLoadingBar.alpha = 0f
                }

                is Resource.Loading -> {
                    searchExitIcon?.alpha = 0f
                    binding.searchLoadingBar.alpha = 1f
                }
            }
        }

        val listLock = ReentrantLock()
        observe(searchViewModel.currentSearch) { list ->
            try {
                // https://stackoverflow.com/questions/6866238/concurrent-modification-exception-adding-to-an-arraylist
                listLock.lock()

                val pinnedOrder = DataStoreHelper.pinnedProviders.reversedArray()

                val sortedList = list.toList().sortedWith(compareBy { (providerName, _) ->
                    val index = pinnedOrder.indexOf(providerName)
                    if (index == -1) Int.MAX_VALUE else index
                })

                (binding.searchMasterRecycler.adapter as? ParentItemAdapter)?.apply {
                    val newItems = sortedList.map { (providerName, providerData) ->
                        val dataList = providerData.list
                        val dataListFiltered =
                            context?.filterSearchResultByFilmQuality(dataList) ?: dataList

                        val homePageList = HomePageList(
                            providerName,
                            dataListFiltered
                        )

                        HomeViewModel.ExpandableHomepageList(
                            homePageList,
                            providerData.currentPage,
                            providerData.hasNext
                        )
                    }

                    submitList(newItems)
                    //notifyDataSetChanged()
                }
            } catch (e: Exception) {
                logError(e)
            } finally {
                listLock.unlock()
            }
        }


        /*main_search.setOnQueryTextFocusChangeListener { _, b ->
            if (b) {
                // https://stackoverflow.com/questions/12022715/unable-to-show-keyboard-automatically-in-the-searchview
                showInputMethod(view.findFocus())
            }
        }*/
        //main_search.onActionViewExpanded()*/

        val masterAdapter =
            ParentItemAdapter(id = "masterAdapter".hashCode(), { callback ->
                SearchHelper.handleSearchClickCallback(callback)
            }, { item ->
                bottomSheetDialog = activity?.loadHomepageList(item, dismissCallback = {
                    bottomSheetDialog = null
                }, expandCallback = { name -> searchViewModel.expandAndReturn(name) })
            }, expandCallback = { name ->
                ioSafe {
                    searchViewModel.expandAndReturn(name)
                }
            })

        val historyAdapter = SearchHistoryAdaptor { click ->
            val searchItem = click.item
            when (click.clickAction) {
                SEARCH_HISTORY_OPEN -> {
                    if (searchItem == null) return@SearchHistoryAdaptor
                    searchViewModel.clearSearch()
                    if (searchItem.type.isNotEmpty())
                        updateChips(
                            binding.tvtypesChipsScroll.tvtypesChips,
                            searchItem.type.toMutableList()
                        )
                    binding.mainSearch.setQuery(searchItem.searchText, true)
                }

                SEARCH_HISTORY_REMOVE -> {
                    if (searchItem == null) return@SearchHistoryAdaptor
                    removeKey("$currentAccount/$SEARCH_HISTORY_KEY", searchItem.key)
                    searchViewModel.updateHistory()
                }

                SEARCH_HISTORY_CLEAR -> {
                    // Show confirmation dialog (from footer button)
                    activity?.let { ctx ->
                        val builder: AlertDialog.Builder = AlertDialog.Builder(ctx)
                        val dialogClickListener =
                            DialogInterface.OnClickListener { _, which ->
                                when (which) {
                                    DialogInterface.BUTTON_POSITIVE -> {
                                        removeKeys("$currentAccount/$SEARCH_HISTORY_KEY")
                                        searchViewModel.updateHistory()
                                    }

                                    DialogInterface.BUTTON_NEGATIVE -> {
                                    }
                                }
                            }

                        try {
                            builder.setTitle(R.string.clear_history).setMessage(
                                ctx.getString(R.string.delete_message).format(
                                    ctx.getString(R.string.history)
                                )
                            )
                                .setPositiveButton(R.string.sort_clear, dialogClickListener)
                                .setNegativeButton(R.string.cancel, dialogClickListener)
                                .show().setDefaultFocus()
                        } catch (e: Exception) {
                            logError(e)
                        }
                    }
                }

                else -> {
                    // wth are you doing???
                }
            }
        }

        val suggestionAdapter = SearchSuggestionAdapter { callback ->
            when (callback.clickAction) {
                SEARCH_SUGGESTION_CLICK -> {
                    // Search directly
                    binding.mainSearch.setQuery(callback.suggestion, true)
                    searchViewModel.clearSuggestions()
                }
                SEARCH_SUGGESTION_FILL -> {
                    // Fill the search box without searching
                    binding.mainSearch.setQuery(callback.suggestion, false)
                }
                SEARCH_SUGGESTION_CLEAR -> {
                    // Clear suggestions (from footer button)
                    searchViewModel.clearSuggestions()
                }
            }
        }

        binding.apply {
            searchHistoryRecycler.adapter = historyAdapter
            searchHistoryRecycler.setLinearListLayout(isHorizontal = false, nextRight = FOCUS_SELF)
            //searchHistoryRecycler.layoutManager = GridLayoutManager(context, 1)

            // Setup suggestions RecyclerView
            searchSuggestionsRecycler.adapter = suggestionAdapter
            searchSuggestionsRecycler.layoutManager = LinearLayoutManager(context)

            searchMasterRecycler.setRecycledViewPool(ParentItemAdapter.sharedPool)
            searchMasterRecycler.adapter = masterAdapter
            //searchMasterRecycler.setLinearListLayout(isHorizontal = false, nextRight = FOCUS_SELF)

            searchMasterRecycler.layoutManager = GridLayoutManager(context, 1)

            // Automatically search the specified query, this allows the app search to launch from intent
            var sq =
                arguments?.getString(SEARCH_QUERY) ?: savedInstanceState?.getString(SEARCH_QUERY)
            if (sq.isNullOrBlank()) {
                sq = MainActivity.nextSearchQuery
            }

            sq?.let { query ->
                if (query.isBlank()) return@let

                // Queries are dropped if you are submitted before layout finishes
                mainSearch.doOnLayout {
                    mainSearch.setQuery(query, true)
                }
                // Clear the query as to not make it request the same query every time the page is opened
                arguments?.remove(SEARCH_QUERY)
                savedInstanceState?.remove(SEARCH_QUERY)
                MainActivity.nextSearchQuery = null
            }
        }

        observe(searchViewModel.currentHistory) { list ->
            (binding.searchHistoryRecycler.adapter as? SearchHistoryAdaptor?)?.submitList(list)
             // Scroll to top to show newest items (list is sorted by newest first)
            if (list.isNotEmpty()) {
                binding.searchHistoryRecycler.scrollToPosition(0)
            }
        }

        // Observe search suggestions
        observe(searchViewModel.searchSuggestions) { suggestions ->
            val hasSuggestions = suggestions.isNotEmpty()
            binding.searchSuggestionsRecycler.isVisible = hasSuggestions
            (binding.searchSuggestionsRecycler.adapter as? SearchSuggestionAdapter?)?.submitList(suggestions)

            // On non-phone layouts, redirect focus and handle back button
            if (!isLayout(PHONE)) {
                if (hasSuggestions) {
                    binding.tvtypesChipsScroll.tvtypesChips.root.nextFocusDownId = R.id.search_suggestions_recycler
                    // Attach back button callback to clear suggestions
                    activity?.attachBackPressedCallback("SearchFragment") {
                        searchViewModel.clearSuggestions()
                    }
                } else {
                    // Reset to default focus target (history)
                    binding.tvtypesChipsScroll.tvtypesChips.root.nextFocusDownId = R.id.search_history_recycler
                    // Detach back button callback when no suggestions
                    activity?.detachBackPressedCallback("SearchFragment")
                }
            }
        }

        searchViewModel.updateHistory()
    }
}
