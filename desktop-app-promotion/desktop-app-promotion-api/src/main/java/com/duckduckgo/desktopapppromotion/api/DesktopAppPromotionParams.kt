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
 * The screen is a renderer: every piece of content and every pixel it may fire is supplied here, and
 * it never infers behaviour from which feature launched it.
 *
 * Every content field is nullable, and `null` means "use the promo screen's own default". The
 * defaults are real, translated resources owned by the implementation module, so a caller that wants
 * the canonical copy — including a deeplink, which can only supply JSON — passes nothing.
 */
data class DesktopAppPromotionParams(

    /** Toolbar title. */
    val toolbarTitle: String? = null,

    /** Screen title. */
    val title: String? = null,

    /** Body copy shown under the title. */
    val body: String? = null,

    /** Illustration shown above the title. `0` means use the default. */
    @DrawableRes val illustration: Int = 0,

    /** The human-readable URL shown on screen, e.g. `"duckduckgo.com/browser"`. */
    val downloadUrlDisplay: String? = null,

    /**
     * The full, attributed URL used for the share sheet and the copy-to-clipboard action,
     * e.g. `"https://duckduckgo.com/browser?origin=funnel_appsettings_android"`.
     * Callers own their own attribution origin — this module does not construct or validate it.
     */
    val downloadUrl: String? = null,

    /** Label of the primary share button. */
    val shareButtonLabel: String? = null,

    /** Title used for the OS share-sheet chooser when the user taps the share button. */
    val shareIntentTitle: String? = null,

    /**
     * Optional complete message shared to the OS share sheet, already containing [downloadUrl] if
     * the caller wants it there. When `null`, the bare URL is shared.
     */
    val shareIntentBody: String? = null,

    /** Whether to show the dismiss ("No Thanks") button. */
    val showDismissButton: Boolean = false,

    /** Label of the dismiss button, when shown. */
    val dismissButtonLabel: String? = null,

    /** What to fire, and with what params, for each of this screen's four interaction points. */
    val pixels: PixelConfig = PixelConfig(),

    /**
     * Opaque key routing post-interaction side effects back to a
     * [DesktopAppPromotionInteractionHandler] contributed by the caller's module. `null` means this
     * caller wants no side effects.
     */
    val handlerId: String? = null,

) : GlobalActivityStarter.ActivityParams

/**
 * Per-interaction pixel configuration. Each field is independently nullable: `null` means "this
 * caller doesn't track this interaction" and the screen fires nothing for it. There is no
 * default/fallback pixel — callers keep firing their own already-reviewed pixel names, and this
 * module never invents its own pixel taxonomy.
 */
data class PixelConfig(

    /** Fired once, when the screen is first shown. Not re-fired on rotation or recreation. */
    val impression: PixelFireSpec? = null,

    /** Fired when the user taps the share button. */
    val shareClicked: PixelFireSpec? = null,

    /** Fired when the user taps the on-screen URL to copy it to the clipboard. */
    val linkClicked: PixelFireSpec? = null,

    /**
     * Fired when the user taps the dismiss button. Only reachable when
     * [DesktopAppPromotionParams.showDismissButton] is `true`.
     */
    val dismissed: PixelFireSpec? = null,
) : Serializable

/**
 * One pixel to fire: a wire-format pixel name plus its parameters.
 *
 * [pixelName] is a plain `String`, not `Pixel.PixelName`, on purpose — the caller passes its own
 * enum's `.pixelName` so this module never has to know about any feature-specific pixel-name enum.
 *
 * [parameters] must already satisfy the repo's pixel privacy rules; that responsibility stays with
 * the caller that owns the pixel definition. `HashMap` rather than `Map` because
 * [GlobalActivityStarter.ActivityParams] is `Serializable` and `Map` is not a serializable type.
 */
data class PixelFireSpec(
    val pixelName: String,
    val parameters: HashMap<String, String> = HashMap(),
) : Serializable
