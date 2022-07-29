/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.breakage

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.di.scopes.VpnScope
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageCategory.CallsCategory
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageCategory.ConnectionCategory
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageCategory.ContentCategory
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageCategory.CrashesCategory
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageCategory.DownloadsCategory
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageCategory.IotCategory
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageCategory.MessagesCategory
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageCategory.OtherCategory
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageCategory.UploadsCategory
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(VpnScope::class)
class ReportBreakageCategorySingleChoiceViewModel
@Inject
constructor() : ViewModel() {

    data class ViewState(
        val indexSelected: Int = -1,
        val categorySelected: ReportBreakageCategory? = null,
        val submitAllowed: Boolean = false,
    )

    sealed class Command {
        object ConfirmAndFinish : Command()
    }

    val viewState = MutableStateFlow(ViewState())
    val command = Channel<Command>(1, DROP_OLDEST)
    var indexSelected = -1
    val categories: List<ReportBreakageCategory> =
        listOf(
            ConnectionCategory,
            DownloadsCategory,
            CallsCategory,
            IotCategory,
            MessagesCategory,
            CrashesCategory,
            UploadsCategory,
            ContentCategory,
            OtherCategory
        )

    init {
        viewState.value = ViewState()
    }

    fun viewState(): Flow<ViewState> {
        return viewState
    }

    fun commands(): Flow<Command> {
        return command.receiveAsFlow()
    }

    fun onCategoryIndexChanged(newIndex: Int) {
        indexSelected = newIndex
    }

    fun onCategorySelectionCancelled() {
        indexSelected = viewState.value.indexSelected
    }

    fun onCategoryAccepted() {
        viewState.value =
            viewState.value.copy(
                indexSelected = indexSelected,
                categorySelected = categories.elementAtOrNull(indexSelected),
                submitAllowed = canSubmit()
            )
    }

    fun onSubmitPressed() {
        viewModelScope.launch { command.send(Command.ConfirmAndFinish) }
    }

    private fun canSubmit(): Boolean = categories.elementAtOrNull(indexSelected) != null
}
