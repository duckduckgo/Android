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

package com.duckduckgo.sync.impl.exchange.v2

/**
 * Spec-defined protocol actions that accompany a state transition. The state machine attaches
 * these to its [TransitionResult]; the runner executes them. Keeping side effects declared at
 * the transition (rather than scattered across the runner's post-trigger hooks) means each
 * spec rule lives in exactly one place and is easy to audit against the Unified Algorithm.
 *
 * Spec source: Asana Unified Algorithm 1214739740392701, §"Exchange Confirmations" and
 * §"Exchange Share Recovery Code".
 */
sealed interface SideEffect {

    data object SendAwaitingConfirmation : SideEffect
    data object SendConfirmed : SideEffect
    data object SendDenied : SideEffect
    data object RequestRecoveryCodeShare : SideEffect
}
