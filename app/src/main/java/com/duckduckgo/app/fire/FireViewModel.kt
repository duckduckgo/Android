/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.fire

import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.ViewModel
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import java.util.concurrent.TimeUnit


class FireViewModel : ViewModel() {

    private var disposable: Disposable? = null

    val viewState: MutableLiveData<ViewState> = MutableLiveData<ViewState>().apply {
        value = ViewState()
    }

    fun startDeathClock() {
        disposable = Completable.complete()
            .delay(ANIMATION_FINISH_DELAY_MS, TimeUnit.MILLISECONDS)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe {
                viewState.value = currentViewState().copy(animate = false)
            }
    }

    fun onViewStopped() {
        viewState.value = currentViewState().copy(autoStart = false)
    }

    fun onViewRestarted() {
        viewState.value = currentViewState().copy(autoStart = true)
    }

    private fun currentViewState(): ViewState {
        return viewState.value!!
    }

    data class ViewState(
        val animate: Boolean = true,
        val autoStart: Boolean = true
    )

    companion object {
        private const val ANIMATION_FINISH_DELAY_MS = 1500L
    }
}