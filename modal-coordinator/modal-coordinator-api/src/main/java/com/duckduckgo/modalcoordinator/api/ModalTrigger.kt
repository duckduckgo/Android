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

package com.duckduckgo.modalcoordinator.api

/**
 * Which lifecycle event causes the coordinator to evaluate a [ModalEvaluator]. A pass only considers
 * evaluators matching the trigger that started it, so different triggers never compete.
 */
enum class ModalTrigger {

    /** The app process came to the foreground (process-level onResume). */
    APP_RESUME,

    /** The New Tab Page was rendered, including mid-session renders that produce no [APP_RESUME]. */
    NTP_RENDER,
}
