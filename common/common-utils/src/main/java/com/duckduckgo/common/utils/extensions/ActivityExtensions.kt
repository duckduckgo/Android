/*
 * Copyright (c) 2023 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.common.utils.extensions

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.getSystemService
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Deep links to the application App Info settings
 * @return `true` if it was able to deep link, otherwise `false`
 */
fun AppCompatActivity.launchApplicationInfoSettings(): Boolean {
    val intent = Intent().apply {
        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        data = Uri.parse("package:$packageName")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    runCatching { startActivity(intent) }

    return true
}

@SuppressLint("InlinedApi")
fun AppCompatActivity.launchAlwaysOnSystemSettings() {
    val intent = Intent(Settings.ACTION_VPN_SETTINGS)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    startActivity(intent)
}

@SuppressLint("InlinedApi")
fun AppCompatActivity.launchSettings() {
    val intent = Intent(Settings.ACTION_SETTINGS)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    startActivity(intent)
}

/**
 * Deep links to the battery optimization settings
 * @return `true` if it was able to deep link, otherwise `false`
 */
fun AppCompatActivity.launchIgnoreBatteryOptimizationSettings(): Boolean {
    val intent = Intent().apply {
        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        data = Uri.parse("package:$packageName")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    runCatching { startActivity(intent) }.onFailure {
        val fallback = Intent().apply {
            action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { startActivity(fallback) }.onFailure { return false }
    }

    return true
}

@SuppressLint("InlinedApi")
fun AppCompatActivity.launchAutofillProviderSystemSettings(): Boolean {
    val intent = Intent().apply {
        action = Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE
        data = Uri.parse("package:$packageName")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }
    runCatching { startActivity(intent) }.onFailure { return false }

    return true
}

fun Activity.showKeyboard(editText: EditText) {
    editText.requestFocus()
    WindowInsetsControllerCompat(window, editText).show(WindowInsetsCompat.Type.ime())
}

fun Activity.hideKeyboard(editText: EditText) {
    WindowInsetsControllerCompat(window, editText).hide(WindowInsetsCompat.Type.ime())
}

fun Activity.hideKeyboard() {
    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    // Check if the view that has focus is part of this Fragment's view hierarchy
    var currentFocus = currentFocus
    if (currentFocus == null) {
        // If no view has focus, create a temporary one to ensure the window token is valid
        currentFocus = View(this)
    }
    imm?.hideSoftInputFromWindow(currentFocus.windowToken, 0)
}
