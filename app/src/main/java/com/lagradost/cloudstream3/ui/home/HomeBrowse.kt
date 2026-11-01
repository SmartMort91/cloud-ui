package com.lagradost.cloudstream3.ui.home

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvSeriesSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.databinding.BrowseSheetBinding
import com.lagradost.cloudstream3.ui.UiPrefs
import com.lagradost.cloudstream3.ui.search.SEARCH_ACTION_LOAD
import com.lagradost.cloudstream3.ui.search.SearchAdapter
import com.lagradost.cloudstream3.ui.search.SearchHelper
import com.lagradost.cloudstream3.ui.settings.Globals.EMULATOR
import com.lagradost.cloudstream3.ui.settings.Globals.TV
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import com.lagradost.cloudstream3.utils.UIHelper.colorFromAttribute
import com.lagradost.cloudstream3.utils.UIHelper.getSpanCount
import com.lagradost.cloudstream3.utils.UIHelper.toPx

/**
 * Quick browsing on the home screen: a grid of category tiles plus a full browse sheet where
 * you can pick a category and sort by rating, release date or name.
 *
 * It works on the titles the selected provider already put on the home page, so it needs no
 * extra network requests and works with every provider. Rating and release year are only
 * available when the provider supplies them, titles without that data are listed last.
 */
object HomeBrowse {
    enum class Category(val labelRes: Int, val types: Set<TvType>?) {
        ALL(R.string.all, null),
        MOVIES(R.string.movies, setOf(TvType.Movie)),
        SHOWS(R.string.tv_series, setOf(TvType.TvSeries)),
        ANIME(R.string.anime, setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)),
        CARTOONS(R.string.cartoons, setOf(TvType.Cartoon)),
        DOCS(R.string.documentaries, setOf(TvType.Documentary)),
        ASIAN(R.string.asian_drama, setOf(TvType.AsianDrama)),
        LIVE(R.string.livestreams, setOf(TvType.Live)),
        OTHER(
            R.string.others,
            setOf(
                TvType.Torrent, TvType.Others, TvType.Music, TvType.AudioBook,
                TvType.CustomMedia, TvType.Audio, TvType.Podcast, TvType.Video
            )
        ),
    }

    enum class Sort(val labelRes: Int) {
        DEFAULT(R.string.browse_sort_default),
        RATING(R.string.sort_rating_desc),
        NEWEST(R.string.sort_release_date_new),
        NAME(R.string.sort_alphabetical_a),
    }

    private val tileCategories = listOf(
        Category.MOVIES, Category.SHOWS, Category.ANIME,
        Category.CARTOONS, Category.DOCS, Category.ASIAN
    )

    /** Every title of the current home page, without duplicates */
    fun collect(page: Map<String, HomeViewModel.ExpandableHomepageList>): List<SearchResponse> =
        page.values.flatMap { it.list.list }.distinctBy { it.url }

    fun filter(items: List<SearchResponse>, category: Category): List<SearchResponse> {
        val types = category.types ?: return items
        return items.filter { item ->
            val type = item.type
            // Titles without a type can only be found under "Others"
            if (type == null) category == Category.OTHER else types.contains(type)
        }
    }

    private fun yearOf(item: SearchResponse): Int? = when (item) {
        is MovieSearchResponse -> item.year
        is TvSeriesSearchResponse -> item.year
        is AnimeSearchResponse -> item.year
        else -> null
    }

    /** Stable sort, titles without the sorted value keep their order at the end */
    fun sorted(items: List<SearchResponse>, sort: Sort): List<SearchResponse> = when (sort) {
        Sort.DEFAULT -> items
        Sort.RATING -> items.sortedByDescending { it.score?.toDouble(10) ?: -1.0 }
        Sort.NEWEST -> items.sortedByDescending { yearOf(it) ?: -1 }
        Sort.NAME -> items.sortedBy { it.name.lowercase() }
    }

    private fun tileBackground(context: Context, accent: Boolean): StateListDrawable {
        val density = context.resources.displayMetrics.density
        val color = if (accent) context.colorFromAttribute(R.attr.colorPrimary)
        else context.colorFromAttribute(R.attr.boxItemBackground)
        fun shape(stroke: Boolean) = GradientDrawable().apply {
            setColor(color)
            cornerRadius = 24 * density
            if (stroke) setStroke((3 * density).toInt(), context.colorFromAttribute(R.attr.colorPrimary))
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), shape(true))
            addState(intArrayOf(android.R.attr.state_pressed), shape(true))
            addState(intArrayOf(), shape(false))
        }
    }

    private fun makeTile(
        context: Context,
        label: String,
        accent: Boolean = false,
        onClick: () -> Unit
    ): TextView =
        TextView(context).apply {
            text = label
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(if (accent) 0xFFFFFFFF.toInt() else context.colorFromAttribute(R.attr.textColor))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(22.toPx, 0, 22.toPx, 0)
            background = tileBackground(context, accent)
            isFocusable = true
            isClickable = true
            setOnClickListener { onClick() }
            setOnFocusChangeListener { view, hasFocus ->
                if (isLayout(TV or EMULATOR)) UiPrefs.applyCardFocus(view, hasFocus)
            }
        }

    /**
     * Fills [holder] with the category tiles (only categories that have titles) and a wide
     * "Browse all and sort" tile. Safe to call again whenever the home page changes.
     */
    fun bindTiles(
        holder: LinearLayout,
        items: List<SearchResponse>,
        getItems: () -> List<SearchResponse>,
        getProvider: () -> String?
    ) {
        val present = tileCategories.filter { filter(items, it).isNotEmpty() }
        val signature = present.joinToString(",") { it.name }
        if (holder.getTag(R.id.home_browse_holder) == signature && holder.childCount > 0) return
        holder.setTag(R.id.home_browse_holder, signature)
        holder.removeAllViews()

        val context = holder.context
        holder.setPadding(8.toPx, 10.toPx, 8.toPx, 6.toPx)

        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        fun add(tile: View) {
            row.addView(tile, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                46.toPx
            ).apply { setMargins(4.toPx, 2.toPx, 4.toPx, 2.toPx) })
        }

        add(makeTile(context, context.getString(R.string.browse_all_sort), accent = true) {
            show(context, getItems(), getProvider(), Category.ALL)
        })
        add(makeTile(context, context.getString(Sort.RATING.labelRes)) {
            show(context, getItems(), getProvider(), Category.ALL, Sort.RATING)
        })
        add(makeTile(context, context.getString(Sort.NEWEST.labelRes)) {
            show(context, getItems(), getProvider(), Category.ALL, Sort.NEWEST)
        })
        // Some providers mark every title with the same type, a filter for it would do nothing
        if (present.size > 1) {
            present.forEach { category ->
                add(makeTile(context, context.getString(category.labelRes)) {
                    show(context, getItems(), getProvider(), category)
                })
            }
        }

        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            addView(row)
        }
        holder.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        holder.isVisible = true
    }

    /** The full browse sheet: pick a category, sort by rating, release date or name */
    fun show(
        context: Context,
        items: List<SearchResponse>,
        providerName: String?,
        initial: Category,
        initialSort: Sort = Sort.DEFAULT
    ) {
        val dialog = BottomSheetDialog(context)
        val binding = BrowseSheetBinding.inflate(LayoutInflater.from(context))
        val height = (context.resources.displayMetrics.heightPixels * 0.92f).toInt()
        binding.root.layoutParams =
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
        dialog.setContentView(binding.root)
        dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        dialog.behavior.skipCollapsed = true

        binding.browseSubtitle.text =
            providerName?.let { context.getString(R.string.browse_from, it) } ?: ""

        var category = initial
        var sort = initialSort

        val adapter = SearchAdapter(binding.browseResults) { callback ->
            if (callback.action == SEARCH_ACTION_LOAD) dialog.dismiss()
            SearchHelper.handleSearchClickCallback(callback)
        }
        binding.browseResults.spanCount = context.getSpanCount()
        binding.browseResults.adapter = adapter

        fun refresh() {
            val list = sorted(filter(items, category), sort)
            adapter.submitList(list)
            binding.browseEmpty.isVisible = list.isEmpty()
            binding.browseResults.isVisible = list.isNotEmpty()
        }

        val inflater = LayoutInflater.from(context)
        fun addChip(group: ChipGroup, label: String, checked: Boolean, onSelected: () -> Unit) {
            val chip = inflater.inflate(R.layout.browse_chip, group, false) as Chip
            chip.text = label
            chip.id = View.generateViewId()
            group.addView(chip)
            chip.isChecked = checked
            chip.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) onSelected()
            }
        }

        // Only offer categories that actually have titles
        Category.values()
            .filter { it == Category.ALL || it == initial || filter(items, it).isNotEmpty() }
            .forEach { entry ->
                addChip(
                    binding.browseCategoryGroup,
                    context.getString(entry.labelRes),
                    entry == initial
                ) {
                    category = entry
                    refresh()
                }
            }

        if (binding.browseCategoryGroup.childCount <= 2) {
            // "All" plus at most one more kind, nothing to choose between
            (binding.browseCategoryGroup.parent as? View)?.isVisible = false
        }

        Sort.values().forEach { entry ->
            addChip(
                binding.browseSortGroup,
                context.getString(entry.labelRes),
                entry == sort
            ) {
                sort = entry
                refresh()
            }
        }

        refresh()
        dialog.show()
    }
}
