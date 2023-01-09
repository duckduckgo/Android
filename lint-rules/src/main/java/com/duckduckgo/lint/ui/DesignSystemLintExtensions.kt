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

        val DEPRECATED_WIDGETS = DEPRECATED_BUTTON_WIDGETS.plus(DEPRECATED_TEXT_WIDGETS).plus(DEPRECATED_SWITCHES)
    }
}


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
