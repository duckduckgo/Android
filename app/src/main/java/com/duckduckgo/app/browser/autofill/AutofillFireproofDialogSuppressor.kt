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

package com.duckduckgo.app.browser.autofill

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import timber.log.Timber

interface AutofillFireproofDialogSuppressor {
    fun isAutofillPreventingFireproofPrompts(): Boolean
    fun autofillSaveOrUpdateDialogVisibilityChanged(visible: Boolean)
}

@ContributesBinding(FragmentScope::class)
@SingleInstanceIn(FragmentScope::class)
class RealAutofillFireproofDialogSuppressor @Inject constructor(private val timeProvider: TimeProvider) : AutofillFireproofDialogSuppressor {

    private var autofillDialogShowing = false
    private var lastTimeUserSawAutofillDialog = 0L

    override fun isAutofillPreventingFireproofPrompts(): Boolean {
        val timeSinceLastDismissed = timeProvider.currentTimeMillis() - lastTimeUserSawAutofillDialog
        val suppressing = autofillDialogShowing || (timeSinceLastDismissed <= TIME_PERIOD_TO_SUPPRESS_FIREPROOF_PROMPT)
        Timber.d(
            "isAutofillPreventingFireproofPrompts: %s (autofillDialogShowing=%s, timeSinceLastDismissed=%sms)",
            suppressing,
            autofillDialogShowing,
            timeSinceLastDismissed,
        )
        return suppressing
    }

    override fun autofillSaveOrUpdateDialogVisibilityChanged(visible: Boolean) {
        Timber.v("Autofill save/update dialog visibility changed to %s", visible)
        autofillDialogShowing = visible

        if (!visible) {
            lastTimeUserSawAutofillDialog = timeProvider.currentTimeMillis()
        }
    }

    companion object {
        private val TIME_PERIOD_TO_SUPPRESS_FIREPROOF_PROMPT = TimeUnit.SECONDS.toMillis(10)
    }
}

interface TimeProvider {
    fun currentTimeMillis(): Long
}

@ContributesBinding(AppScope::class)
class SystemCurrentTimeProvider @Inject constructor() : TimeProvider {
    override fun currentTimeMillis(): Long = System.currentTimeMillis()
}
