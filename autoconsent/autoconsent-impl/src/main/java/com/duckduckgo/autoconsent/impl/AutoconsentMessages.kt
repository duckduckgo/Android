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

package com.duckduckgo.autoconsent.impl

data class CmpDetectedMessage(val type: String, val cmp: String, val url: String)

data class EvalMessage(val type: String, val id: String, val code: String)

data class PopupFoundMessage(val type: String, val cmp: String, val url: String)

data class OptOutResultMessage(val type: String, val cmp: String, val result: Boolean, val scheduleSelfTest: Boolean, val url: String)

data class OptInResultMessage(val type: String, val cmp: String, val result: Boolean, val scheduleSelfTest: Boolean, val url: String)

data class SelfTestResultMessage(val type: String, val cmp: String, val result: Boolean, val url: String)

data class AutoconsentDoneMessage(val type: String, val cmp: String, val url: String)
