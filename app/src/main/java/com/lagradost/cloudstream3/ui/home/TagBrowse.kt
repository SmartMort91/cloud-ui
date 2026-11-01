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
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.mvvm.Resource
import com.lagradost.cloudstream3.ui.APIRepository
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.Coroutines.main
import com.lagradost.cloudstream3.utils.UIHelper.toPx
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tags (genres) are not part of the shared provider API, so there is no request for "titles with
 * this tag". Instead this checks real tags: it takes the titles the provider shows on its home page
 * plus the provider's search results for the tag word, loads each title's details and keeps only
 * the ones that really carry the tag. The browse sheet opens at once and fills in as titles are
 * checked, so it works with every provider but only covers a limited number of titles.
 */
object TagBrowse {
    private const val MAX_CANDIDATES = 60
    private const val BATCH_SIZE = 8
    private const val LOAD_TIMEOUT_MS = 12000L

    /** Tags offered as quick filters in the search tab */
    val commonTags = listOf(
        "Action", "Adventure", "Animation", "Comedy", "Crime", "Documentary", "Drama",
        "Family", "Fantasy", "Horror", "Mystery", "Romance", "Sci-Fi", "Thriller", "War"
    )

    private val aliases = mapOf(
        "sci fi" to listOf("science fiction", "sci fi", "scifi"),
        "documentary" to listOf("documentary", "docu"),
        "animation" to listOf("animation", "animated"),
    )

    private fun norm(text: String): String =
        text.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    private fun hasTag(tags: List<String>?, tag: String): Boolean {
        val wanted = aliases[norm(tag)] ?: listOf(norm(tag))
        return tags.orEmpty().any { t ->
            val n = norm(t)
            wanted.any { w -> n == w || n.contains(w) }
        }
    }

    fun open(context: Context, tag: String, apiName: String?) {
        val api = apiName?.let { getApiFromNameNull(it) }
        if (api == null) {
            Toast.makeText(context, R.string.tag_browse_no_provider, Toast.LENGTH_SHORT).show()
            return
        }

        val handle = HomeBrowse.show(
            context, emptyList(), api.name, HomeBrowse.Category.ALL, title = tag, loading = true
        )
        handle.update(emptyList(), context.getString(R.string.tag_browse_loading, tag), false)

        context.ioSafe {
            val repo = APIRepository(api)
            val candidates = LinkedHashMap<String, SearchResponse>()

            val home = repo.getMainPage(1, null)
            if (home is Resource.Success) {
                home.value.filterNotNull().forEach { page ->
                    page.items.forEach { list ->
                        list.list.forEach { candidates.putIfAbsent(it.url, it) }
                    }
                }
            }
            val found = repo.search(tag, 1)
            if (found is Resource.Success) {
                found.value.items.forEach { candidates.putIfAbsent(it.url, it) }
            }

            val pool = candidates.values.take(MAX_CANDIDATES)
            if (pool.isEmpty()) {
                main {
                    handle.update(
                        emptyList(),
                        context.getString(R.string.tag_browse_empty, tag),
                        true
                    )
                }
            }

            val matched = ArrayList<SearchResponse>()
            var checked = 0
            for (batch in pool.chunked(BATCH_SIZE)) {
                if (!handle.isShowing()) break
                val results = batch.amap { item ->
                    item to withTimeoutOrNull(LOAD_TIMEOUT_MS) { repo.load(item.url) }
                }
                for ((item, res) in results) {
                    if (res is Resource.Success && hasTag(res.value.tags, tag)) matched.add(item)
                }
                checked += batch.size

                val snapshot = matched.toList()
                val done = checked >= pool.size
                val checkedNow = checked
                main {
                    handle.update(
                        snapshot,
                        context.getString(
                            R.string.tag_browse_progress,
                            tag, snapshot.size, checkedNow, pool.size
                        ),
                        done
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
