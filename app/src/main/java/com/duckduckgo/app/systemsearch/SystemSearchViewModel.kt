/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.systemsearch

import android.annotation.SuppressLint
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.duckduckgo.app.autocomplete.api.AutoCompleteApi
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit

class SystemSearchViewModel(
    private val autoCompleteApi: AutoCompleteApi
) : ViewModel() {

    private val autoCompletePublishSubject = PublishRelay.create<String>()
    private val autoCompleteResults: MutableLiveData<AutoCompleteApi.AutoCompleteResult> = MutableLiveData()

    @SuppressLint("CheckResult")
    private fun configureAutoComplete() {
        autoCompleteResults.value = AutoCompleteApi.AutoCompleteResult("", emptyList())

        autoCompletePublishSubject
            .debounce(300, TimeUnit.MILLISECONDS)
            .switchMap { autoCompleteApi.autoComplete(it) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ result ->
                autoCompleteResults.value = result
            }, { t: Throwable? -> Timber.w(t, "Failed to get search results") })
    }


}
