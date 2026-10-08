package com.example.streamingappzb.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.example.streamingappzb.R
import com.example.streamingappzb.ui.base.color
import com.example.streamingappzb.ui.base.dimen

/**
 * The design's radio (PRD §5 `MhRadio`): a 22dp ring with a 2dp border, ember when
 * selected, `night400` when not, filled with a 10dp ember dot when on.
 *
 * Non-clickable by default: in the quality and download sheets the whole row is the target
 * (44dp+), and a nested clickable would fight it for the tap.
 */
class MhRadio @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val size = context.dimen(R.dimen.radio_size)
    private val strokeWidth = context.dimen(R.dimen.radio_stroke).toFloat()
    private val dotSize = context.dimen(R.dimen.radio_dot)

    private val onColour = context.color(R.color.ember500)
    private val offColour = context.color(R.color.night400)

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.style = Paint.Style.STROKE
        this.strokeWidth = this@MhRadio.strokeWidth
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = onColour
    }

    var isChecked: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    init {
        // Announced as a platform RadioButton with a selected state, via the Compat node
        // because AccessibilityNodeInfo's own setters are deprecated.
        ViewCompat.setAccessibilityDelegate(
            this,
            object : AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfoCompat,
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = "android.widget.RadioButton"
                    info.isCheckable = true
                    info.isChecked = isChecked
                }
            },
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(size, widthMeasureSpec),
            resolveSize(size, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        ringPaint.color = if (isChecked) onColour else offColour
        canvas.drawCircle(cx, cy, size / 2f - strokeWidth / 2f, ringPaint)
        if (isChecked) {
            canvas.drawCircle(cx, cy, dotSize / 2f, dotPaint)
        }
    }
}
