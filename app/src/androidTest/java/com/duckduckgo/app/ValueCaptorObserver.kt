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

package com.duckduckgo.app

import androidx.lifecycle.Observer

/**
 * Concrete class of Observer<T> to validate if any value has been received when observing a
 * LiveData
 *
 * @param skipFirst: skip first value emitted by LiveData stream. SkipFirst is usually: Set to False
 * to observe a SingleLiveEvent or any LiveData that starts emitting after subscription. Set to True
 * when observing any LiveData that emits its lastValue on subscription.
 */
class ValueCaptorObserver<T>(private var skipFirst: Boolean = true) : Observer<T> {
    var hasReceivedValue = false
        private set

    var lastValueReceived: T? = null
        private set

    override fun onChanged(t: T) {
        if (skipFirst) {
            skipFirst = false
            return
        }
        lastValueReceived = t
        hasReceivedValue = true
    }
}
