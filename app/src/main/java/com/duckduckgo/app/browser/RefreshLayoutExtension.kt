package com.duckduckgo.app.browser

import android.content.Context
import android.support.v4.widget.SwipeRefreshLayout
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView

class RefreshLayoutExtension(context: Context, attrs: AttributeSet) : SwipeRefreshLayout(context, attrs) {

    private val mTouchSlop: Int
    private var mPrevX: Float = 0.toFloat()
    private var webView: WebView? = null
    private var isAppBarDocked:Boolean = false

    init {
        mTouchSlop = ViewConfiguration.get(context).scaledTouchSlop
    }

    fun setWebView(webViewSet: WebView?){
        webView = webViewSet
    }
    fun setIsAppBarDocked(isAppBarDockedSet: Boolean){
        isAppBarDocked = isAppBarDockedSet
    }

    override fun canChildScrollUp(): Boolean {
        if (webView!!.scrollY == 0 && isAppBarDocked){
            return false
        }
        return true
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> mPrevX = MotionEvent.obtain(event).x
            MotionEvent.ACTION_MOVE -> {
                val eventX = event.x
                val xDiff = Math.abs(eventX - mPrevX)
                if (xDiff > mTouchSlop) {
                    return false
                }
            }
        }
        return super.onInterceptTouchEvent(event)
    }
}