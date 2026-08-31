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

package com.duckduckgo.autofill.impl.importing

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.autofill.impl.importing.CredentialImporter.ImportResult.Finished
import com.duckduckgo.autofill.impl.ui.credential.management.importpassword.ImportPasswordsPixelSender
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Reports the outcome of a successful password import.
 *
 * The web flow returns as soon as the credentials are handed over, but they are still being written and
 * counted after that, so the counts outlive the screen that started the import. Observing app-wide from
 * process start keeps this independent of whichever caller launched the flow, and means the replay cache
 * is always empty when collection begins — so every import is reported exactly once.
 */
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class ImportPasswordsResultPixelObserver @Inject constructor(
    private val credentialImporter: CredentialImporter,
    private val importPasswordsPixelSender: ImportPasswordsPixelSender,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)

        appCoroutineScope.launch(dispatchers.io()) {
            credentialImporter.getImportStatus()
                .filterIsInstance<Finished>()
                .collect {
                    importPasswordsPixelSender.onImportSuccessful(
                        savedCredentials = it.savedCredentials,
                        numberSkipped = it.numberSkipped,
                        source = it.source,
                    )
                }
        }
    }
}
