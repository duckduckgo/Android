/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.autofill.impl.ui.credential.management.viewing

import android.content.Context
import android.content.res.Resources
import com.duckduckgo.autofill.impl.R
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.sync.api.DeviceSyncState
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface AutofillManagementStringBuilder {
    fun stringForDeletePasswordDialogConfirmationTitle(numberToDelete: Int): String
    suspend fun stringForDeletePasswordDialogConfirmationMessage(numberToDelete: Int): String
}

@ContributesBinding(FragmentScope::class)
class AutofillManagementStringBuilderImpl @Inject constructor(
    private val context: Context,
    private val deviceSyncState: DeviceSyncState,
    private val dispatchers: DispatcherProvider,
) : AutofillManagementStringBuilder {

    override fun stringForDeletePasswordDialogConfirmationTitle(numberToDelete: Int): String {
        return context.resources.getQuantityString(
            R.plurals.credentialManagementDeleteAllPasswordsDialogConfirmationTitle,
            numberToDelete,
            numberToDelete,
        )
    }

    override suspend fun stringForDeletePasswordDialogConfirmationMessage(numberToDelete: Int): String {
        val firstMessage = context.resources.deleteAllPasswordsWarning(numberToDelete)
        val secondMessage = context.resources.getQuantityString(R.plurals.credentialManagementDeleteAllSecondInstruction, numberToDelete)
        return "$firstMessage $secondMessage"
    }

    private suspend fun Resources.deleteAllPasswordsWarning(numberToDelete: Int): String {
        return withContext(dispatchers.io()) {
            val stringResId = if (deviceSyncState.isUserSignedInOnDevice()) {
                R.plurals.credentialManagementDeleteAllPasswordsFirstInstructionSynced
            } else {
                R.plurals.credentialManagementDeleteAllPasswordsDialogFirstInstructionNotSynced
            }
            return@withContext getQuantityString(stringResId, numberToDelete)
        }
    }
}
