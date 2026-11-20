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

package com.duckduckgo.duckchat.impl.responding

interface DuckAiRespondingServiceController {

    /**
     * While enabled, the app is kept alive each time it leaves the foreground, until disabled or the service times out.
     * Enable it before [com.duckduckgo.browser.api.BrowserLifecycleObserver.onClose], otherwise Android may refuse to start the service.
     * Disabling it stops a running service, also while the app is in the background.
     */
    fun setKeepAliveOnLeave(enabled: Boolean)
}
