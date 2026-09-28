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

package com.duckduckgo.desktopapppromotion.api

import androidx.annotation.DrawableRes
import com.duckduckgo.navigation.api.GlobalActivityStarter
import java.io.Serializable

/**
 * Launch params for the shared "get the DuckDuckGo desktop app/browser" promo screen.
 *
 * The screen is a renderer: every piece of content it may show is supplied here, and it never infers
 * behaviour from which feature launched it. Pixels are not part of this contract — a caller that wants
 * to react to what the user did contributes a
 * [DesktopAppPromotionInteractionHandler] and sets [handlerId].
 *
 * Every content field is nullable, and `null` means "use the promo screen's own default"
 */
data class DesktopAppPromotionParams(

    /** Toolbar title. */
    val toolbarTitle: String? = null,

    /** Screen title. */
    val title: String? = null,

    /** Body copy shown under the title. */
    val body: String? = null,

    /** Illustration shown above the title. `null` means use the default. */
    @DrawableRes val illustration: Int? = null,

    /** The download link shown on screen and used for the share sheet and copy-to-clipboard action. */
    val link: DownloadLinkConfig = DownloadLinkConfig(),

    /** Copy for the share button and the OS share sheet. */
    val share: ShareConfig = ShareConfig(),

    /** Whether to show the dismiss ("No Thanks") button. */
    val showDismissButton: Boolean = false,

    /** Label of the dismiss button, when shown. */
    val dismissButtonLabel: String? = null,

    /**
     * Opaque key routing interactions. `null` means this caller wants no side effects.
     */
    val handlerId: String? = null,

) : GlobalActivityStarter.ActivityParams

/** The download link shown on screen, and used for the share sheet and copy-to-clipboard action. */
data class DownloadLinkConfig(

    /** The human-readable URL shown on screen, e.g. `"duckduckgo.com/browser"`. */
    val downloadUrlDisplay: String? = null,

    /**
     * The full, attributed URL used for the share sheet and the copy-to-clipboard action,
     * e.g. `"https://duckduckgo.com/browser?origin=funnel_appsettings_android"`.
     * Callers own their own attribution origin — this module does not construct or validate it.
     */
    val downloadUrl: String? = null,
) : Serializable

/** Copy for the share button and the OS share sheet. */
data class ShareConfig(

    /** Label of the primary share button. */
    val shareButtonLabel: String? = null,

    /** Title used for the OS share-sheet chooser when the user taps the share button. */
    val shareIntentTitle: String? = null,

    /**
     * Optional complete message shared to the OS share sheet, already containing the download URL if
     * the caller wants it there. When `null`, the bare URL is shared.
     */
    val shareIntentBody: String? = null,
) : Serializable
