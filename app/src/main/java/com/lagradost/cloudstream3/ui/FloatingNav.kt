package com.lagradost.cloudstream3.ui

import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.compose.ui.platform.ComposeView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.databinding.ActivityMainBinding
import com.lagradost.cloudstream3.ui.settings.Globals.PHONE
import com.lagradost.cloudstream3.ui.settings.Globals.isLayout
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/**
 * Makes the phone nav bar truly float: the page is laid out behind the pill, so content shows
 * around and under it, and the pill is no longer sitting on a separate strip.
 *
 * To keep the last items reachable, every vertical list that reaches the bottom of the page gets
 * extra bottom padding (and stops clipping to it), and views anchored to the bottom of the page
 * (FABs, tab bars, bottom bars) are lifted by the same amount. All of it is undone when the
 * option is off, in landscape, or on pages without the nav bar.
 *
 * Turning it off in Settings > Layout restores the old behavior.
 */
object FloatingNav {
    const val OPTION = "floating_nav"

    val isEnabled: Boolean get() = UiPrefs.getOpt(OPTION, "on") == "on"

    /** Height of the system navigation bar in px, set from the window insets */
    private var systemBottom = 0
    private var inset = 0
    var isFloating = false
        private set

    private var hostRef: WeakReference<ViewGroup>? = null

    private class Padded(val origPadding: Int, val origClip: Boolean, var applied: Int)
    private class Lifted(val origMargin: Int, var applied: Int)

    private val padded = WeakHashMap<View, Padded>()
    private val lifted = WeakHashMap<View, Lifted>()

    private var scanPending = false
    private val scanRunnable = Runnable {
        scanPending = false
        hostRef?.get()?.let { scan(it) }
    }

    /** Call once, after the main layout exists */
    fun attach(host: ViewGroup) {
        hostRef = WeakReference(host)
        // New list items, view pager pages and fragments show up after this fires
        host.viewTreeObserver.addOnGlobalLayoutListener { schedule() }
    }

    fun setSystemBottom(px: Int) {
        if (px == systemBottom) return
        systemBottom = px
        recomputeInset()
        schedule()
    }

    /** Call whenever the current page, rotation or option changes */
    fun configure(binding: ActivityMainBinding, navVisible: Boolean, landscape: Boolean) {
        val host = binding.navHostFragment
        hostRef = WeakReference(host)
        isFloating = isLayout(PHONE) && isEnabled && navVisible && !landscape
        recomputeInset()

        (host.layoutParams as? ConstraintLayout.LayoutParams)?.let { lp ->
            if (isFloating) {
                lp.bottomToTop = ConstraintLayout.LayoutParams.UNSET
                lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            } else {
                lp.bottomToBottom = ConstraintLayout.LayoutParams.UNSET
                lp.bottomToTop = R.id.cast_mini_controller_holder
            }
            host.layoutParams = lp
        }
        schedule()
    }

    private fun recomputeInset() {
        val density = hostRef?.get()?.resources?.displayMetrics?.density ?: return
        // pill height + its bottom margin + a little breathing room
        inset = if (isFloating) ((84 + 12 + 8) * density).toInt() + systemBottom else 0
    }

    private fun schedule() {
        // Throttled, not debounced, so constantly animating pages still get scanned
        if (scanPending) return
        val host = hostRef?.get() ?: return
        scanPending = true
        host.postDelayed(scanRunnable, 100)
    }

    private fun scan(host: ViewGroup) {
        if (!isFloating) {
            restoreAll()
            return
        }
        if (host.height <= 0) return
        val hostTop = IntArray(2).also { host.getLocationInWindow(it) }[1]
        walk(host, host.height, hostTop)
    }

    private fun bottomOf(view: View, hostTop: Int): Int {
        val loc = IntArray(2)
        view.getLocationInWindow(loc)
        return loc[1] + view.height - hostTop
    }

    private fun walk(group: ViewGroup, hostHeight: Int, hostTop: Int) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child.visibility == View.GONE) continue

            when (child) {
                is RecyclerView -> {
                    val vertical = child.layoutManager?.canScrollVertically() == true
                    if (vertical) {
                        pad(child, hostHeight, hostTop)
                    } else {
                        // horizontal lists can host view pager pages, look inside
                        walk(child, hostHeight, hostTop)
                    }
                }

                is ScrollView, is NestedScrollView, is ComposeView -> pad(child, hostHeight, hostTop)

                is ViewGroup -> {
                    lift(child, hostHeight, hostTop)
                    walk(child, hostHeight, hostTop)
                }

                else -> lift(child, hostHeight, hostTop)
            }
        }
    }

    private fun nearBottom(view: View, hostHeight: Int, hostTop: Int) =
        bottomOf(view, hostTop) >= hostHeight - inset

    private fun pad(view: View, hostHeight: Int, hostTop: Int) {
        var state = padded[view]
        if (state != null && view.paddingBottom != state.applied) {
            // Someone else changed the padding, treat it as the new original
            state = null
            padded.remove(view)
        }
        if (state == null) {
            if (!nearBottom(view, hostHeight, hostTop)) return
            state = Padded(view.paddingBottom, (view as? ViewGroup)?.clipToPadding ?: true, 0)
            padded[view] = state
        }

        val target = state.origPadding + inset
        if (view.paddingBottom != target) {
            view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, target)
        }
        (view as? ViewGroup)?.clipToPadding = false
        state.applied = target
    }

    private fun gravityOf(view: View): Int? =
        when (val lp = view.layoutParams) {
            is FrameLayout.LayoutParams -> lp.gravity
            is CoordinatorLayout.LayoutParams -> lp.gravity
            else -> null
        }

    private fun lift(view: View, hostHeight: Int, hostTop: Int) {
        val lp = view.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val gravity = gravityOf(view) ?: return
        if (gravity == -1 || (gravity and Gravity.VERTICAL_GRAVITY_MASK) != Gravity.BOTTOM) return

        var state = lifted[view]
        if (state != null && lp.bottomMargin != state.applied) {
            state = null
            lifted.remove(view)
        }
        if (state == null) {
            if (!nearBottom(view, hostHeight, hostTop)) return
            state = Lifted(lp.bottomMargin, 0)
            lifted[view] = state
        }

        val target = state.origMargin + inset
        if (lp.bottomMargin != target) {
            lp.bottomMargin = target
            view.layoutParams = lp
        }
        state.applied = target
    }

    private fun restoreAll() {
        for ((view, state) in padded.entries.toList()) {
            if (state.applied != 0 && view.paddingBottom == state.applied) {
                view.setPadding(
                    view.paddingLeft, view.paddingTop, view.paddingRight, state.origPadding
                )
                (view as? ViewGroup)?.clipToPadding = state.origClip
            }
        }
        padded.clear()

        for ((view, state) in lifted.entries.toList()) {
            val lp = view.layoutParams as? ViewGroup.MarginLayoutParams ?: continue
            if (state.applied != 0 && lp.bottomMargin == state.applied) {
                lp.bottomMargin = state.origMargin
                view.layoutParams = lp
            }
        }
        lifted.clear()
    }
}
