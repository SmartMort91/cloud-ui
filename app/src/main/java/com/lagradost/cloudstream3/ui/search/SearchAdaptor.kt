package com.lagradost.cloudstream3.ui.search

import android.view.LayoutInflater
import com.lagradost.cloudstream3.R
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.databinding.SearchResultGridBinding
import com.lagradost.cloudstream3.databinding.SearchResultGridExpandedBinding
import com.lagradost.cloudstream3.ui.AutofitRecyclerView
import com.lagradost.cloudstream3.ui.BaseDiffCallback
import com.lagradost.cloudstream3.ui.NoStateAdapter
import com.lagradost.cloudstream3.ui.ViewHolderState
import com.lagradost.cloudstream3.ui.newSharedPool
import com.lagradost.cloudstream3.utils.UIHelper.isBottomLayout
import kotlin.math.roundToInt

/** Click */
const val SEARCH_ACTION_LOAD = 0

/** Long press */
const val SEARCH_ACTION_SHOW_METADATA = 1
const val SEARCH_ACTION_PLAY_FILE = 2
const val SEARCH_ACTION_FOCUSED = 4

class SearchClickCallback(
    val action: Int,
    val view: View,
    val position: Int,
    val card: SearchResponse
)

class SearchAdapter(
    private val resView: AutofitRecyclerView,
    private val isHorizontal:Boolean = false,
    /** Always show the title on the card, used by the banner search layout */
    private val forceTitle: Boolean = false,
    private val clickCallback: (SearchClickCallback) -> Unit,
) : NoStateAdapter<SearchResponse>(diffCallback = BaseDiffCallback(itemSame = { a, b ->
    if (a.id != null || b.id != null) {
        a.id == b.id
    } else {
        a.name == b.name
    }
})) {
    companion object {
        /** width:height of a banner card, written so the height is derived from the width */
        private const val BANNER_RATIO = "H,16:10"

        val sharedPool =
            newSharedPool { setMaxRecycledViews(CONTENT, 10) }
    }

    var hasNext: Boolean = false

    private val coverRatio = if(isHorizontal) 1.6 else 0.68

    private val coverHeight: Int get() = (resView.itemWidth / coverRatio).roundToInt()

    /** The card width the current image heights were calculated from */
    private var boundItemWidth = -1

    init {
        // The grid can be measured after the first cards are bound (it starts hidden and the
        // column count changes), which left the first banners with a wrong height. Rebind once
        // the real card width is known.
        resView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val width = resView.itemWidth
            if (width > 0 && width != boundItemWidth && itemCount > 0) {
                boundItemWidth = width
                resView.post { notifyDataSetChanged() }
            }
        }
    }

    override fun onCreateContent(parent: ViewGroup): ViewHolderState<Any> {
        val inflater = LayoutInflater.from(parent.context)

        val layout =
            if (parent.context.isBottomLayout()) SearchResultGridExpandedBinding.inflate(
                inflater,
                parent,
                false
            ) else SearchResultGridBinding.inflate(
                inflater,
                parent,
                false
            )
        return ViewHolderState(layout)
    }

    override fun onClearView(holder: ViewHolderState<Any>) {
        clearImage(
            when (val binding = holder.view) {
                is SearchResultGridExpandedBinding -> binding.imageView
                is SearchResultGridBinding -> binding.imageView
                else -> null
            }
        )
    }

    override fun onBindContent(holder: ViewHolderState<Any>, item: SearchResponse, position: Int) {
        boundItemWidth = resView.itemWidth
        val imageView = when (val binding = holder.view) {
            is SearchResultGridExpandedBinding -> binding.imageView
            is SearchResultGridBinding -> binding.imageView
            else -> null
        }

        val card = holder.view.root.findViewById<View>(R.id.background_card)
        val constraintLp =
            card?.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams

        if (isHorizontal && card != null && constraintLp != null) {
            // Banner card inside a ConstraintLayout: let the layout derive the height from the
            // real card width. This does not depend on when the grid was measured, which is
            // what left the first banners squashed until something forced a new layout.
            if (constraintLp.height != 0 || constraintLp.dimensionRatio != BANNER_RATIO) {
                constraintLp.height = 0
                constraintLp.dimensionRatio = BANNER_RATIO
                card.layoutParams = constraintLp
            }
            imageView?.let {
                val p = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                if (it.layoutParams.height != p.height || it.layoutParams.width != p.width) {
                    it.layoutParams = p
                }
            }
        } else {
            if (!isHorizontal && card != null && constraintLp != null &&
                constraintLp.dimensionRatio == BANNER_RATIO
            ) {
                // A recycled banner card is being reused as a poster card, restore the poster shape
                constraintLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                constraintLp.dimensionRatio = "1:1.5"
                card.layoutParams = constraintLp
            }
            if (imageView != null) {
                val params = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    coverHeight
                )
                if (imageView.layoutParams.width != params.width || imageView.layoutParams.height != params.height) {
                    imageView.layoutParams = params
                }
            }
            if (isHorizontal && card != null) {
                // Plain (non constraint) card: give it the exact height as well
                val lp = card.layoutParams
                if (lp != null && lp.height != coverHeight) {
                    lp.height = coverHeight
                    card.layoutParams = lp
                }
            }
        }
        SearchResultBuilder.bind(
            clickCallback, item, position, holder.view.root,
            forceTitle = forceTitle
        )
    }
}