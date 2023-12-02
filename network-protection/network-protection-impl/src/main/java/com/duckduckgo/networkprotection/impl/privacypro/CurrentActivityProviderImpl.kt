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

package com.duckduckgo.networkprotection.impl.privacypro

import android.app.Activity
import com.duckduckgo.browser.api.ActivityLifecycleCallbacks
import com.duckduckgo.common.ui.view.dialog.TextAlertDialogBuilder
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class CurrentActivityProviderImpl @Inject constructor() : ActivityLifecycleCallbacks {
    private val shown = AtomicBoolean(false)

    override fun onActivityResumed(activity: Activity) {
        if (!shown.get()) {
            TextAlertDialogBuilder(activity)
                .setTitle("VPN has been disconnected")
                .setMessage("Resubscribe to Privacy Pro to continue using DuckDuckGo VPN to secure your device's internet traffic")
                .setPositiveButton("Resubscribe")
                .setNegativeButton("Not Now")
                .setCancellable(false)
                .addEventListener(
                    object : TextAlertDialogBuilder.EventListener() {
                        override fun onPositiveButtonClicked() {
                            shown.set(true)
                        }

                        override fun onNegativeButtonClicked() {
                            shown.set(true)
                        }
                    },
                )
                .show()
        }
    }
}
