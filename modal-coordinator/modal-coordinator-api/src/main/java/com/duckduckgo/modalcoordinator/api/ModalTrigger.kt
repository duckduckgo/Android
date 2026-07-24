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
 * Declares which lifecycle event should cause the coordinator to evaluate a [ModalEvaluator].
 *
 * A single coordinated evaluation only considers evaluators matching the trigger that started it,
 * so evaluators opting into different triggers never compete within the same pass.
 */
enum class ModalTrigger {

    /**
     * Evaluated when the app process comes to the foreground (process-level onResume).
     * This is the default and matches the historical behaviour of every evaluator.
     */
    APP_RESUME,

    /**
     * Evaluated when the New Tab Page is rendered, including mid-session renders (e.g. opening a
     * fresh tab while the app is already foregrounded) that do not produce an [APP_RESUME].
     */
    NTP_RENDER,
}
