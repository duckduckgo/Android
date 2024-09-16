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

package com.duckduckgo.autofill.sync

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.autofill.store.AutofillDatabase

fun inMemoryAutofillDatabase(): AutofillDatabase {
    return Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AutofillDatabase::class.java)
        .allowMainThreadQueries()
        .build()
}
