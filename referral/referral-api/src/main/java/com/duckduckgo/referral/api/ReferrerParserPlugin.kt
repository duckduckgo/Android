/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.referral.api

/**
 * Contract for parsers that extract a single piece of data from the Play Store install referrer.
 * Implementations are contributed as multibindings and run on a best-effort, fire-and-forget basis
 * during referrer parsing. Each implementation persists its own result; ordering is irrelevant.
 *
 * @param referrerParams the referrer's `key=value` parameters, already split on `&` and parsed.
 */
interface ReferrerParserPlugin {
    fun process(referrerParams: Map<String, String>)
}
