package com.duckduckgo.app.browser.safe_gaze

import android.content.Context
import android.content.SharedPreferences
import android.webkit.JavascriptInterface

class SafeGazeJsInterface(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences("safe_gaze_preferences", Context.MODE_PRIVATE)

    @JavascriptInterface
    fun sendMessage(message: String) {
        println("Message send from js side is -> $message")
        val currentCounter = getCounterValue()
        val newCounter = currentCounter + 1
        saveGlobalCounterValue(newCounter)
    }

    private fun saveGlobalCounterValue(value: Int) {
        preferences.edit().putInt("all_time_cencored_count", value).apply()
    }

    private fun getCounterValue(): Int {
        return preferences.getInt("all_time_cencored_count", 0)
    }
}
