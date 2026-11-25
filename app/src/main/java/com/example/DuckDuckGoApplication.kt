package com.example

import android.app.Application
import com.example.data.local.BrowserDatabase
import com.example.data.preferences.BrowserPreferences

class DuckDuckGoApplication : Application() {
    val database: BrowserDatabase by lazy { BrowserDatabase.getInstance(this) }
    val preferences: BrowserPreferences by lazy { BrowserPreferences(this) }
}
