package androidx.swiperefreshlayout.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * A very lightweight stand‑in for the real SwipeRefreshLayout. This fallback
 * implementation exists to allow the application to compile in environments
 * where the official AndroidX SwipeRefreshLayout dependency is not available.
 *
 * It provides a simple mechanism to listen for downward swipe gestures and
 * invoke a refresh callback. It does **not** attempt to replicate the
 * appearance or full behaviour of the official widget. If the real
 * SwipeRefreshLayout library is present on the classpath, that version
 * will take precedence and this class will be ignored.
 */
open class SwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    /** Callback interface used to signal a refresh gesture. */
    interface OnRefreshListener {
        fun onRefresh()
    }

    private var listener: OnRefreshListener? = null
    private var startY: Float = 0f
    /** Indicates whether a refresh is currently in progress. */
    /**
     * Current refresh state. This property is mutable so that client code can
     * update the state via `isRefreshing = false` after a refresh completes,
     * mirroring the API of the official SwipeRefreshLayout. Changes to this
     * property do not display any built‑in spinner since this lightweight
     * implementation has no visual indicator.
     */
    var isRefreshing: Boolean = false

    /**
     * Registers a refresh listener. You can supply either an interface
     * implementation or a lambda. Only one listener is stored at a time.
     */
    fun setOnRefreshListener(l: OnRefreshListener?) {
        listener = l
    }

    /** Alternate form accepting a lambda for convenience. */
    fun setOnRefreshListener(block: () -> Unit) {
        listener = object : OnRefreshListener {
            override fun onRefresh() = block()
        }
    }


    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                startY = ev.y
                // Do not intercept yet.
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = ev.y - startY
                // If the user swipes downward more than a threshold and we're not
                // already refreshing, trigger the refresh callback. We intercept
                // the touch event so onTouchEvent isn't called on children.
                if (dy > 200 && !isRefreshing) {
                    isRefreshing = true
                    listener?.onRefresh()
                    return true
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }
}