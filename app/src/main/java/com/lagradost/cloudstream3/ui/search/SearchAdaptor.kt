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
    /** Fixed banner height in px, used instead of anything measured. */
    private val fixedCoverHeight: (() -> Int)? = null,
    private val clickCallback: (SearchClickCallback) -> Unit,
) : NoStateAdapter<SearchResponse>(diffCallback = BaseDiffCallback(itemSame = { a, b ->
    if (a.id != null || b.id != null) {
        a.id == b.id
    } else {
        a.name == b.name
    }
})) {
    companion object {
        val sharedPool =
            newSharedPool { setMaxRecycledViews(CONTENT, 10) }
    }

    var hasNext: Boolean = false

    private val coverRatio = if(isHorizontal) 1.6 else 0.68

    private val coverHeight: Int
        get() = (if (isHorizontal) fixedCoverHeight?.invoke()?.takeIf { it > 0 } else null)
            ?: (resView.itemWidth / coverRatio).roundToInt()

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
        val imageView = when (val binding = holder.view) {
            is SearchResultGridExpandedBinding -> binding.imageView
            is SearchResultGridBinding -> binding.imageView
            else -> null
        }

        val card = holder.view.root.findViewById<View>(R.id.background_card)
        val height = coverHeight

        if (imageView != null) {
            val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)
            if (imageView.layoutParams.width != params.width || imageView.layoutParams.height != params.height) {
                imageView.layoutParams = params
            }
        }

        if (card != null) {
            val lp = card.layoutParams
            if (lp != null) {
                if (isHorizontal) {
                    // Banner card: one fixed height, no ratios and nothing measured
                    if (lp is androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) {
                        lp.dimensionRatio = null
                    }
                    if (lp.height != height) {
                        lp.height = height
                        card.layoutParams = lp
                    }
                } else if (lp is androidx.constraintlayout.widget.ConstraintLayout.LayoutParams &&
                    lp.dimensionRatio == null
                ) {
                    // A recycled banner card is reused as a poster card, restore the poster shape
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    lp.dimensionRatio = "1:1.5"
                    card.layoutParams = lp
                } else if (lp !is androidx.constraintlayout.widget.ConstraintLayout.LayoutParams &&
                    lp.height > 0
                ) {
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    card.layoutParams = lp
                }
            }
        }

        (imageView as? TopCropImageView)?.verticalBias = if (isHorizontal) 0.2f else -1f
        SearchResultBuilder.bind(
            clickCallback, item, position, holder.view.root,
            forceTitle = forceTitle
        )
    }
}