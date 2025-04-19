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

package com.duckduckgo.subscriptions.impl.rmf

import com.duckduckgo.remote.messaging.api.MatchingAttribute

internal interface FakeBooleanMatchingAttribute : MatchingAttribute {
    val value: Boolean
}

internal interface FakeStringMatchingAttribute : MatchingAttribute {
    val value: String
}

@Suppress("TestFunctionName")
internal fun FakeBooleanMatchingAttribute(block: () -> Boolean): MatchingAttribute = object : FakeBooleanMatchingAttribute {
    override val value: Boolean = block()
}

@Suppress("TestFunctionName")
internal fun FakeStringMatchingAttribute(block: () -> String): MatchingAttribute = object : FakeStringMatchingAttribute {
    override val value: String = block()
}
