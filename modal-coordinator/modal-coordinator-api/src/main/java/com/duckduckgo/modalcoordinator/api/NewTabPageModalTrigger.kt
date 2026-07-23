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
 * Lets New Tab Page hosts notify the coordinator that the NTP has just been rendered, so that
 * evaluators declaring [ModalTrigger.NTP_RENDER] can be coordinated.
 *
 * This is the counterpart to the process-level onResume trigger the coordinator observes itself:
 * mid-session NTP renders (e.g. opening a fresh tab while foregrounded) do not produce an
 * [ModalTrigger.APP_RESUME], so hosts must surface them explicitly.
 */
interface NewTabPageModalTrigger {

    /**
     * Notifies the coordinator that the New Tab Page has been rendered. Safe to call on every
     * render; the coordinator applies its own mutex, cooldown and priority rules.
     */
    fun onNewTabPageShown()
}
