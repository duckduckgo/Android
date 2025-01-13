/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.common.utils

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest

/**
 * SwitcherFlow is a utility class that allows switching between multiple upstream flows.
 * It uses a MutableSharedFlow to emit the latest upstream flow and ensures that only distinct
 * values are emitted by using distinctUntilChanged.
 *
 * @param T the type of elements emitted by the upstream flows
 * @property flowOfSources a MutableSharedFlow that holds the current upstream flow
 *
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SwitcherFlow<T>(
    private val flowOfSources: MutableSharedFlow<Flow<T>> = MutableSharedFlow<Flow<T>>(),
) : Flow<T>by flowOfSources.flatMapLatest({ it }).distinctUntilChanged() {
    suspend fun switch(upstream: Flow<T>) {
        flowOfSources.emit(upstream)
    }
}
