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

package com.duckduckgo.app

import androidx.annotation.UiThread
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.di.TestAppComponent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// from https://stackoverflow.com/a/44991770/73479
@UiThread
fun <T> LiveData<T>.blockingObserve(): T? {
    var value: T? = null
    val latch = CountDownLatch(1)
    val innerObserver = Observer<T> {
        value = it
        latch.countDown()
    }
    observeForever(innerObserver)
    latch.await(2, TimeUnit.SECONDS)
    return value
}

fun getApp(): TestApplication {
    return InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as TestApplication
}

fun getDaggerComponent(): TestAppComponent {
    return getApp().daggerAppComponent as TestAppComponent
}
