package com.lagradost.cloudstream3.ui.search

import android.content.Context
import android.graphics.Matrix
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.max

/**
 * An ImageView that crops like centerCrop, but can lean the visible part towards the top.
 * Tall posters shown in a wide banner then show their upper part (faces, titles) instead of
 * the middle. A [verticalBias] below zero keeps the normal centerCrop behaviour.
 *
 * 0 keeps the top edge, 1 keeps the bottom edge, 0.5 is the same as centerCrop.
 */
class TopCropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    var verticalBias: Float = -1f
        set(value) {
            if (field != value) {
                field = value
                applyCrop()
            }
        }

    override fun setFrame(l: Int, t: Int, r: Int, b: Int): Boolean {
        val changed = super.setFrame(l, t, r, b)
        if (changed) applyCrop()
        return changed
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        applyCrop()
    }

    private fun applyCrop() {
        if (verticalBias < 0f) {
            if (scaleType == ScaleType.MATRIX) scaleType = ScaleType.CENTER_CROP
            return
        }
        val d = drawable ?: return
        val dw = d.intrinsicWidth
        val dh = d.intrinsicHeight
        val vw = width - paddingLeft - paddingRight
        val vh = height - paddingTop - paddingBottom
        if (dw <= 0 || dh <= 0 || vw <= 0 || vh <= 0) return

        val scale = max(vw / dw.toFloat(), vh / dh.toFloat())
        val dx = (vw - dw * scale) * 0.5f
        val dy = (vh - dh * scale) * verticalBias.coerceIn(0f, 1f)
        val m = Matrix()
        m.setScale(scale, scale)
        m.postTranslate(dx + paddingLeft, dy + paddingTop)
        if (scaleType != ScaleType.MATRIX) scaleType = ScaleType.MATRIX
        imageMatrix = m
    }
}
