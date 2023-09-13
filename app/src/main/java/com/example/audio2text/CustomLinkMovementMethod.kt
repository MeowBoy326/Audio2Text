package com.example.audio2text

import android.text.Selection
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.view.MotionEvent
import android.view.VelocityTracker
import android.widget.Scroller
import android.widget.TextView

class CustomLinkMovementMethod : LinkMovementMethod() {
    private var scroller: Scroller? = null
    private var initialY = 0f
    private var lastY = 0f
    private var velocityTracker: VelocityTracker? = null

    override fun onTouchEvent(widget: TextView, buffer: Spannable, event: MotionEvent): Boolean {
        // Initialize scroller and velocity tracker if needed
        if (scroller == null) {
            scroller = Scroller(widget.context)
        }
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (!scroller!!.isFinished) {
                    scroller!!.abortAnimation()
                }
                lastY = event.y
                initialY = lastY
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = lastY - event.y
                lastY = event.y

                // Scroll the view
                if (widget.canScrollVertically(1) || widget.canScrollVertically(-1)) {
                    widget.scrollBy(0, deltaY.toInt())
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                velocityTracker?.computeCurrentVelocity(1000)
                val initialVelocity = velocityTracker?.yVelocity?.toInt() ?: 0

                // Use the velocity to perform a fling scroll action
                if (widget.canScrollVertically(1) || widget.canScrollVertically(-1)) {
                    scroller!!.fling(
                        0, widget.scrollY, 0, -initialVelocity,
                        0, 0, Integer.MIN_VALUE, Integer.MAX_VALUE
                    )
                    widget.postInvalidateOnAnimation()
                }

                // Reset and recycle the velocity tracker
                velocityTracker?.clear()
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }

        // Handle clickable spans if needed
        if (super.onTouchEvent(widget, buffer, event)) {
            return true
        }

        return true
    }
}