/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.sync.impl.pixels

import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class SyncPixelParamRemovalPlugin @Inject constructor() : PixelParamRemovalPlugin {
    override fun names(): List<Pair<String, Set<PixelParameter>>> {
        return listOf(
            SyncPixelName.SYNC_PATCH_COMPRESS_FAILED.pixelName to PixelParameter.removeAtb(),

            SyncPixelName.SYNC_FEATURE_PROMOTION_DISPLAYED.pixelName to PixelParameter.removeAtb(),
            SyncPixelName.SYNC_FEATURE_PROMOTION_CONFIRMED.pixelName to PixelParameter.removeAtb(),
            SyncPixelName.SYNC_FEATURE_PROMOTION_DISMISSED.pixelName to PixelParameter.removeAtb(),

            SyncPixelName.SYNC_GET_OTHER_DEVICES_SCREEN_SHOWN.pixelName to PixelParameter.removeAtb(),
            SyncPixelName.SYNC_GET_OTHER_DEVICES_LINK_COPIED.pixelName to PixelParameter.removeAtb(),
            SyncPixelName.SYNC_GET_OTHER_DEVICES_LINK_SHARED.pixelName to PixelParameter.removeAtb(),

            SyncPixelName.SYNC_ASK_USER_TO_SWITCH_ACCOUNT.pixelName to PixelParameter.removeAtb(),
            SyncPixelName.SYNC_USER_ACCEPTED_SWITCHING_ACCOUNT.pixelName to PixelParameter.removeAtb(),
            SyncPixelName.SYNC_USER_CANCELLED_SWITCHING_ACCOUNT.pixelName to PixelParameter.removeAtb(),
            SyncPixelName.SYNC_USER_SWITCHED_ACCOUNT.pixelName to PixelParameter.removeAtb(),
            SyncPixelName.SYNC_USER_SWITCHED_LOGOUT_ERROR.pixelName to PixelParameter.removeAtb(),
            SyncPixelName.SYNC_USER_SWITCHED_LOGIN_ERROR.pixelName to PixelParameter.removeAtb(),

            SyncPixelName.SYNC_SETUP_DEEP_LINK_TIMEOUT.pixelName to PixelParameter.removeAtb(),
        )
    }
}
