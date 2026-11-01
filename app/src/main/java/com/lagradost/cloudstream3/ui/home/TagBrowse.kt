package com.lagradost.cloudstream3.ui.home

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.lagradost.cloudstream3.APIHolder.getApiFromNameNull
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.UIHelper.toPx

/**
 * Tags (genres) are not part of the shared provider API, so there is no exact "titles with this
 * tag" request. This does the best that every provider allows:
 *  1. if the provider has a home page list named like the tag (many have genre lists) that list is used
 *  2. otherwise the provider is searched for the tag text
 * The result opens in the browse sheet, so it can be sorted by rating, date or name.
 */
object TagBrowse {
    /** Tags offered as quick filters in the search tab */
    val commonTags = listOf(
        "Action", "Adventure", "Animation", "Comedy", "Crime", "Documentary", "Drama",
        "Family", "Fantasy", "Horror", "Mystery", "Romance", "Sci-Fi", "Thriller", "War"
    )

    fun open(context: Context, tag: String, apiName: String?) {
        val api = apiName?.let { getApiFromNameNull(it) }
        if (api == null) {
            Toast.makeText(context, R.string.tag_browse_no_provider, Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(context, context.getString(R.string.tag_browse_loading, tag), Toast.LENGTH_SHORT).show()

        context.ioSafe {
            val repo = APIRepository(api)
            var items: List<SearchResponse> = emptyList()

            val listIndex = api.mainPage.indexOfFirst {
                it.name.isNotBlank() && it.name.contains(tag, ignoreCase = true)
            }
            if (listIndex >= 0) {
                val res = repo.getMainPage(1, listIndex)
                if (res is Resource.Success) {
                    items = res.value.filterNotNull().flatMap { page ->
                        page.items.flatMap { it.list }
                    }
                }
            }
            if (items.isEmpty()) {
                val res = repo.search(tag, 1)
                if (res is Resource.Success) items = res.value.items
            }

            val unique = items.distinctBy { it.url }
            main {
                if (unique.isEmpty()) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.tag_browse_empty, tag),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    HomeBrowse.show(
                        context, unique, api.name, HomeBrowse.Category.ALL, title = tag
                    )
                }
            }
        }
    }

    /** A scrolling row of tag chips, used in the search tab */
    fun buildTagRow(context: Context, apiNameProvider: () -> String?): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(10.toPx, 0, 10.toPx, 4.toPx)
        }
        commonTags.forEach { tag ->
            val chip = TextView(context).apply {
                text = tag
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setPadding(16.toPx, 0, 16.toPx, 0)
                setTextColor(0xFFFFFFFF.toInt())
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0x33FFFFFF)
                    cornerRadius = 20.toPx.toFloat()
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { open(context, tag, apiNameProvider()) }
            }
            row.addView(chip, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, 38.toPx
            ).apply { setMargins(4.toPx, 2.toPx, 4.toPx, 2.toPx) })
        }
        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }
}
