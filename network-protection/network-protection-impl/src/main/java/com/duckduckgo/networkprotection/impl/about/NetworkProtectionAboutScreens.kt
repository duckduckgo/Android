/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.about

import com.duckduckgo.navigation.api.GlobalActivityStarter.ActivityParams

sealed class NetworkProtectionAboutScreens {
    /**
     * Use this model to launch the "Network Protection FAQs" screen
     */
    object NetPFaqsScreenNoParams : ActivityParams

    /**
     * Use this model to launch the "Network Protection" terms and conditions screen
     */
    @Deprecated("This is the old terms and conditions for VPN waitlist beta")
    object NetPTermsScreenNoParams : ActivityParams

    /**
     * Use this model to launch the "Network Protection" terms and conditions screen
     */
    object VpnTermsScreenNoParams : ActivityParams {
        private fun readResolve(): Any = VpnTermsScreenNoParams
    }
}
