/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.global.view

import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Provider for creating Fire dialog instances.
 * Returns the appropriate dialog variant based on feature flag (Simple or Granular).
 */
interface FireDialogProvider {
    /**
     * Creates a Fire dialog instance.
     * @param onShowListener Optional callback invoked when dialog is shown
     * @param onCancelListener Optional callback invoked when dialog is canceled
     * @param onClearStartedListener Optional callback invoked when data clearing starts
     * @return Instance of FireDialog (either Simple or Granular variant)
     */
    suspend fun createFireDialog(
        onShowListener: (() -> Unit)? = null,
        onCancelListener: (() -> Unit)? = null,
        onClearStartedListener: (() -> Unit)? = null,
    ): FireDialog
}

@ContributesBinding(scope = AppScope::class)
@SingleInstanceIn(scope = AppScope::class)
class FireDialogProviderImpl @Inject constructor(
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val dispatcherProvider: DispatcherProvider,
) : FireDialogProvider {

    override suspend fun createFireDialog(
        onShowListener: (() -> Unit)?,
        onCancelListener: (() -> Unit)?,
        onClearStartedListener: (() -> Unit)?,
    ): FireDialog {
        val isGranularMode = withContext(dispatcherProvider.io()) {
            androidBrowserConfigFeature.moreGranularDataClearingOptions().isEnabled()
        }

        return if (isGranularMode) {
            GranularFireDialog.newInstance(
                onShowListener = onShowListener,
                onCancelListener = onCancelListener,
                onClearStartedListener = onClearStartedListener,
            )
        } else {
            LegacyFireDialog.newInstance(
                onShowListener = onShowListener,
                onCancelListener = onCancelListener,
                onClearStartedListener = onClearStartedListener,
            )
        }
    }
}
