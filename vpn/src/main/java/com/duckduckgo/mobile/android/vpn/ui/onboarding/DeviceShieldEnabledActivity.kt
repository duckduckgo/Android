/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.ui.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.SpannedString
import android.text.Annotation
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.duckduckgo.mobile.android.vpn.R
import com.duckduckgo.mobile.android.vpn.ui.tracker_activity.DeviceShieldTrackerActivity
import nl.dionsegijn.konfetti.KonfettiView
import nl.dionsegijn.konfetti.models.Shape
import nl.dionsegijn.konfetti.models.Size

class DeviceShieldEnabledActivity : AppCompatActivity(R.layout.activity_device_shield_enabled) {

    private lateinit var deviceShieldActivityCta: Button
    private lateinit var settingsCta: TextView
    private lateinit var viewKonfetti: KonfettiView
    private lateinit var close: View

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bindViews()
        launchKonfetti()
    }

    private fun bindViews() {
        close = findViewById(R.id.onboarding_close)
        close.setOnClickListener {
            close()
        }
        deviceShieldActivityCta = findViewById(R.id.onboarding_enabled_activity_cta)
        deviceShieldActivityCta.setOnClickListener {
            startActivity(DeviceShieldTrackerActivity.intent(this))
            finish()
        }

        settingsCta = findViewById(R.id.onboarding_enabled_settings_text)
        val fullText = getText(R.string.deviceShieldEnabledSettings) as SpannedString
        val spannableString = SpannableString(fullText)
        val annotations = fullText.getSpans(0, fullText.length, Annotation::class.java)
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                finish()
            }
        }

        annotations?.find { it.value == "settings_link" }?.let {
            spannableString.apply {
                setSpan(
                    clickableSpan,
                    fullText.getSpanStart(it),
                    fullText.getSpanEnd(it),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                setSpan(
                    ForegroundColorSpan(
                        ContextCompat.getColor(baseContext, R.color.cornflowerBlue)
                    ),
                    fullText.getSpanStart(it),
                    fullText.getSpanEnd(it),
                    0
                )
            }
        }
        settingsCta.apply {
            text = spannableString
            movementMethod = LinkMovementMethod.getInstance()
        }

        viewKonfetti = findViewById(R.id.deviceShieldKonfetti)
    }

    private fun launchKonfetti() {

        val magenta = ResourcesCompat.getColor(getResources(), R.color.magenta, null)
        val blue = ResourcesCompat.getColor(getResources(), R.color.accentBlue, null)
        val purple = ResourcesCompat.getColor(getResources(), R.color.purple, null)
        val green = ResourcesCompat.getColor(getResources(), R.color.green, null)
        val yellow = ResourcesCompat.getColor(getResources(), R.color.yellow, null)

        val displayWidth = resources.displayMetrics.widthPixels

        viewKonfetti.build()
            .addColors(magenta, blue, purple, green, yellow)
            .setDirection(0.0, 359.0)
            .setSpeed(1f, 5f)
            .setFadeOutEnabled(true)
            .setTimeToLive(2000L)
            .addShapes(Shape.Rectangle(1f))
            .addSizes(Size(8))
            .setPosition(displayWidth / 2f, displayWidth / 2f, -50f, -50f)
            .streamFor(50, 4000L)
    }

    override fun onBackPressed() {
        // go back to previous screen or get out if first page
        onSupportNavigateUp()
    }

    override fun onSupportNavigateUp(): Boolean {
        close()
        return true
    }

    private fun close() {
        setResult(RESULT_CANCELED)
        finish()
    }

    companion object {
        fun intent(context: Context): Intent {
            return Intent(context, DeviceShieldEnabledActivity::class.java)
        }
    }
}
