/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.sync.impl.sendtodevices

import android.app.Activity
import android.content.Intent
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.duckduckgo.browser.api.ActivityLifecycleCallbacks
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ViewSendToDevicesPromptBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import java.lang.ref.WeakReference
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
@SingleIn(AppScope::class)
class SendLinksInAppPrompt @Inject constructor() : ActivityLifecycleCallbacks {

    @Volatile
    private var resumed = WeakReference<Activity>(null)

    override fun onActivityResumed(activity: Activity) {
        resumed = WeakReference(activity)
    }

    override fun onActivityPaused(activity: Activity) {
        if (resumed.get() === activity) resumed.clear()
    }

    /**
     * @return false if no activity is in the foreground, so the caller should fall back to a notification.
     */
    fun show(intent: Intent, senderName: String, subtitle: String): Boolean {
        val activity = resumed.get() ?: return false
        activity.runOnUiThread {
            val content = activity.findViewById<FrameLayout>(android.R.id.content) ?: return@runOnUiThread
            val binding = ViewSendToDevicesPromptBinding.inflate(LayoutInflater.from(activity), content, false)
            binding.sendToDevicesPromptTitle.text = activity.getString(R.string.send_to_devices_notification_title, senderName)
            binding.sendToDevicesPromptSubtitle.text = subtitle
            val card = binding.root
            val dismiss = { content.removeView(card) }
            binding.sendToDevicesPromptOpen.setOnClickListener {
                dismiss()
                activity.startActivity(intent)
            }
            val statusBar = ViewCompat.getRootWindowInsets(content)?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
            // ponytail: fixed offset assumes a top omnibar; below a bottom omnibar it just floats over the page
            card.layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP).apply {
                setMargins(12.toPx(), statusBar + 64.toPx(), 12.toPx(), 0)
            }
            card.alpha = 0f
            content.addView(card)
            card.animate().alpha(1f)
        }
        return true
    }
}
