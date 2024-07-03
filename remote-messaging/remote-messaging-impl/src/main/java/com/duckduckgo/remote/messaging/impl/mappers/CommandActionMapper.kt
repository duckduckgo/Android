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

package com.duckduckgo.remote.messaging.impl.mappers

import com.duckduckgo.remote.messaging.api.Action
import com.duckduckgo.remote.messaging.api.Action.*
import com.duckduckgo.remote.messaging.impl.newtab.RemoteMessageViewModel

fun Action.asNewTabCommand(): RemoteMessageViewModel.Command {
    return when (this) {
        is Dismiss -> RemoteMessageViewModel.Command.DismissMessage
        is PlayStore -> RemoteMessageViewModel.Command.LaunchPlayStore(this.value)
        is Url -> RemoteMessageViewModel.Command.SubmitUrl(this.value)
        is DefaultBrowser -> RemoteMessageViewModel.Command.LaunchDefaultBrowser
        is AppTpOnboarding -> RemoteMessageViewModel.Command.LaunchAppTPOnboarding
        is Share -> RemoteMessageViewModel.Command.SharePromoLinkRMF(this.value, this.title)
        is Navigation -> { RemoteMessageViewModel.Command.LaunchScreen(this.value, this.additionalParameters?.get("payload").orEmpty()) }
        is Survey -> RemoteMessageViewModel.Command.SubmitUrl(this.value)
    }
}
