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

import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModel
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageCategory.CallsCategory
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageCategory.ConnectionCategory
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageCategory.ContentCategory
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageCategory.CrashesCategory
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageCategory.DownloadsCategory
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageCategory.IotCategory
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageCategory.MessagesCategory
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageCategory.OtherCategory
import com.duckduckgo.mobile.android.vpn.breakage.ReportBreakageCategory.UploadsCategory
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@ContributesViewModel(ActivityScope::class)
class ReportBreakageCategorySingleChoiceViewModel
@Inject
constructor(private val dispatcherProvider: DispatcherProvider) : ViewModel() {

  data class ViewState(
    val indexSelected: Int = -1,
    val categorySelected: ReportBreakageCategory? = null,
    val submitAllowed: Boolean = false,
  )

  sealed class Command {
      object ConfirmAndFinish : Command()
  }

    val viewState: MutableLiveData<ViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()
    var indexSelected = -1
    val categories: List<ReportBreakageCategory> = listOf(
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

    fun onCategoryIndexChanged(newIndex: Int) {
        indexSelected = newIndex
    }

    fun onCategorySelectionCancelled() {
        indexSelected = viewState.value?.indexSelected ?: -1
    }

    fun onCategoryAccepted() {
        viewState.value = viewState.value?.copy (
            indexSelected = indexSelected,
            categorySelected = categories.elementAtOrNull(indexSelected),
            submitAllowed = canSubmit()
        )
    }

    fun onSubmitPressed() {
        command.value = Command.ConfirmAndFinish
    }

    private fun canSubmit(): Boolean = categories.elementAtOrNull(indexSelected) != null

    class SingleLiveEvent<T> : MutableLiveData<T>() {

        private val pending = AtomicBoolean(false)

        @MainThread
        override fun observe(
            owner: LifecycleOwner,
            observer: Observer<in T>
        ) {

            if (hasActiveObservers()) {
                Timber.w("Multiple observers registered but only one will be notified of changes.")
            }

            // Observe the internal MutableLiveData
            super.observe(owner) { t ->
                if (pending.compareAndSet(true, false)) {
                    observer.onChanged(t)
                }
            }
        }

        @MainThread
        override fun setValue(t: T?) {
            pending.set(true)
            super.setValue(t)
        }

        /**
         * Used for cases where T is Void, to make calls cleaner.
         */
        @MainThread
        fun call() {
            value = null
        }
    }

}
  // private var selectedChoice: Choice? = null

  // private val choices = MutableStateFlow(CHOICES)
  // private var refreshTickerChannel = MutableStateFlow(System.currentTimeMillis())
  //
  // internal fun commands(): Flow<Command> =
  //     command.receiveAsFlow()

  // internal fun getChoices(): Flow<ReportBreakageCategorySingleChoiceView.State> {
  //   return choices
  //       .asStateFlow()
  //       .combine(refreshTickerChannel) { choices, _ -> choices }
  //       .map { it.update(selectedChoice) }
  //       .map {
  //         ReportBreakageCategorySingleChoiceView.State(
  //             it, canSubmit = it.any { choice -> choice.isSelected })
  //       }
  // }

  // internal fun getAllChoices(): List<Choice> {
  //     return CHOICES
  // }
  //
  // internal fun getSelectedChoice(): Choice? {
  //     return selectedChoice
  // }
//
//   internal fun onChoiceSelected(choice: Choice) {
//     selectedChoice = choice.copy(isSelected = true)
//     viewModelScope.launch(dispatcherProvider.main()) {
//       refreshTickerChannel.emit(System.currentTimeMillis())
//     }
//   }
//
//   private fun List<Choice>.update(newValue: Choice?): List<Choice> {
//     return toMutableList().map {
//       if (it.categoryStringRes == newValue?.categoryStringRes) newValue else it
//     }
//   }
//
//   fun onSubmitChoices() {
//     viewModelScope.launch(dispatcherProvider.main()) {
//       selectedChoice?.let {
//         command.send(ReportBreakageCategorySingleChoiceView.Command.SubmitChoice(it))
//       }
//     }
//   }
//
//   companion object {
//     @VisibleForTesting
//     val CHOICES =
//         listOf(
//             Choice(R.string.atp_ReportBreakageCategoryCrashes),
//             Choice(R.string.atp_ReportBreakageCategoryMessages),
//             Choice(R.string.atp_ReportBreakageCategoryCalls),
//             Choice(R.string.atp_ReportBreakageCategoryUploads),
//             Choice(R.string.atp_ReportBreakageCategoryDownloads),
//             Choice(R.string.atp_ReportBreakageCategoryContent),
//             Choice(R.string.atp_ReportBreakageCategoryConnection),
//             Choice(R.string.atp_ReportBreakageCategoryIot),
//             Choice(R.string.atp_ReportBreakageCategoryOther),
//         )
//   }
// }
