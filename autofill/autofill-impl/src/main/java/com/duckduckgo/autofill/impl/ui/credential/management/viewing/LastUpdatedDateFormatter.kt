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

package com.duckduckgo.autofill.impl.ui.credential.management.viewing

import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

interface LastUpdatedDateFormatter {
    fun format(timeInMillis: Long): String
}

@ContributesBinding(FragmentScope::class)
class RealLastUpdatedDateFormatter @Inject constructor() : LastUpdatedDateFormatter {

    override fun format(timeInMillis: Long): String = formatter.format(Date(timeInMillis))

    companion object {
        private val formatter by lazy {
            SimpleDateFormat("MMM dd, yyyy HH:mm aaa", Locale.getDefault())
        }
    }
}
