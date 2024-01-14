package com.duckduckgo.app.browser.safe_gaze

import android.content.Context
import android.content.SharedPreferences
import android.webkit.JavascriptInterface

class SafeGazeJsInterface(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences("safe_gaze_prefs", Context.MODE_PRIVATE)

    @JavascriptInterface
    fun sendMessage(message: String) {
        println("Message send from js side is -> $message")
    }

    @JavascriptInterface
    fun safeGazeImageCounter() {
        val currentCounter = getCounterValue()
        val newCounter = currentCounter + 1
        saveGlobalCounterValue(newCounter)
    }

    private fun saveGlobalCounterValue(value: Int) {
        preferences.edit().putInt("safe_gaze_global_counter", value).apply()
    }

    private fun getCounterValue(): Int {
        return preferences.getInt("safe_gaze_global_counter", 0)
    }
}
