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

package com.duckduckgo.lint.ui

import com.android.tools.lint.detector.api.getChildren
import org.w3c.dom.Attr
import org.w3c.dom.Element

class DesignSystemLintExtensions {

    companion object {
        // Buttons
        private const val LEGACY_PRIMARY_LOWERCASE_BUTTON = "com.duckduckgo.mobile.android.ui.view.button.ButtonPrimaryLowercase"
        private const val LEGACY_PRIMARY_ROUNDED_BUTTON = "com.duckduckgo.mobile.android.ui.view.button.ButtonPrimaryRounded"
        private const val LEGACY_SECONDARY_ROUNDED_BUTTON = "com.duckduckgo.mobile.android.ui.view.button.ButtonSecondaryRounded"

        private const val LEGACY_ANDROID_BUTTON = "Button"
        private const val APP_COMPAT_BUTTON = "AppCompatButton"
        private const val MATERIAL_BUTTON = "com.google.android.material.button.MaterialButton"

        // TextView
        private const val TEXT_VIEW = "TextView"
        private const val APPCOMPAT__TEXT_VIEW = "AppCompatTextView"
        private const val MATERIAL_TEXT_VIEW = "com.google.android.material.textview.MaterialTextView"

        private val DEPRECATED_BUTTON_WIDGETS = listOf(
            LEGACY_ANDROID_BUTTON,
            APP_COMPAT_BUTTON,
            MATERIAL_BUTTON,
            LEGACY_PRIMARY_LOWERCASE_BUTTON,
            LEGACY_PRIMARY_ROUNDED_BUTTON,
            LEGACY_SECONDARY_ROUNDED_BUTTON,
        )

        private val DEPRECATED_TEXT_WIDGETS = listOf(
            TEXT_VIEW,
            APPCOMPAT__TEXT_VIEW,
            MATERIAL_TEXT_VIEW
        )

        // Switch
        private const val APP_COMPAT_SWITCH = "androidx.appcompat.widget.SwitchCompat"
        private const val MATERIAL_SWITCH = "com.google.android.material.switchmaterial.SwitchMaterial"
        private const val SWITCH = "Switch"

        private val DEPRECATED_SWITCHES = listOf(APP_COMPAT_SWITCH, MATERIAL_SWITCH, SWITCH)

        // Android Design System Components
        private const val BUTTON_PRIMARY_LARGE = "com.duckduckgo.mobile.android.ui.view.button.ButtonPrimaryLarge"
        private const val BUTTON_PRIMARY_SMALL = "com.duckduckgo.mobile.android.ui.view.button.ButtonPrimarySmall"
        private const val BUTTON_SECONDARY_LARGE = "com.duckduckgo.mobile.android.ui.view.button.ButtonSecondaryLarge"
        private const val BUTTON_SECONDARY_SMALL = "com.duckduckgo.mobile.android.ui.view.button.ButtonSecondarySmall"
        private const val BUTTON_GHOST_LARGE = "com.duckduckgo.mobile.android.ui.view.button.ButtonGhostLarge"
        private const val BUTTON_GHOST_SMALL = "com.duckduckgo.mobile.android.ui.view.button.ButtonGhostSmall"
        private const val ONE_LINE_LIST_ITEM = "com.duckduckgo.mobile.android.ui.view.listitem.OneLineListItem"
        private const val TWO_LINE_LIST_ITEM = "com.duckduckgo.mobile.android.ui.view.listitem.TwoLineListItem"
        private const val SWITCH_VIEW = "com.duckduckgo.mobile.android.ui.view.DaxSwitch"
        private const val DAX_TEXT = "com.duckduckgo.mobile.android.ui.view.text.DaxTextView"

        val ANDROID_DESIGN_COMPONENTS =
            listOf(
                BUTTON_PRIMARY_LARGE,
                BUTTON_PRIMARY_SMALL,
                BUTTON_SECONDARY_LARGE,
                BUTTON_SECONDARY_SMALL,
                BUTTON_GHOST_LARGE,
                BUTTON_GHOST_SMALL,
                ONE_LINE_LIST_ITEM,
                TWO_LINE_LIST_ITEM,
                SWITCH_VIEW,
                DAX_TEXT
            )

        val DEPRECATED_WIDGETS = DEPRECATED_BUTTON_WIDGETS.plus(DEPRECATED_TEXT_WIDGETS).plus(DEPRECATED_SWITCHES)
    }
}

private val REGEX_HEX_COLOR = Regex("^#[0-9a-fA-F]{8}$|#[0-9a-fA-F]{6}$|#[0-9a-fA-F]{4}$|#[0-9a-fA-F]{3}$")

internal fun String.isColorHexcode() = REGEX_HEX_COLOR.containsMatchIn(this)

/**
 * "mds" is used as an allowlist filter, where we'll assume that the resource contains theme-friendly colors
 *  or it's an exception to the rule.
 */
internal fun String.isHardcodedColorResInXml() = startsWith("@color/") && !contains("dax")

internal fun Attr.belongsToItem() = ownerElement.nodeName == "item"

internal fun Element.belongsToStyle() = parentNode.nodeName == "style"

internal fun Element.belongsToThemeOrThemeOverlay() = belongsToStyle()
    && parentNode.attributes.getNamedItem("name").nodeValue.startsWith("Theme")

/**
 * Looks for VALUE in a <style> resource
 * e.g. <item name="attrName">VALUE</item>
 * else returns null
 */
internal fun Element.findValueOfItemWithName(attrName: String): String? {
    if (nodeName != "style") throw IllegalArgumentException("Expected <style> element but found: $nodeName")
    return getChildren(this)
        .firstOrNull { element -> element.nodeName == "item" && element.getAttribute("name") == attrName }
        ?.firstChild
        ?.nodeValue
}
