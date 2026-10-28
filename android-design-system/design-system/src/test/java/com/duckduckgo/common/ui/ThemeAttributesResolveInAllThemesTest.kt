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

package com.duckduckgo.common.ui

import android.content.Context
import android.content.res.Resources
import android.util.TypedValue
import android.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.mobile.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * While the new theme toggle exists, the old and new theme hierarchies must stay interchangeable, in
 * day and night: every `daxColor*` attribute resolves to a colour in every theme and overlay, the new
 * themes override every colour the old ones set, and each theme's selector attributes (overlays,
 * onboarding themes, Light and Dark themes) point into its own hierarchy.
 */
@RunWith(AndroidJUnit4::class)
class ThemeAttributesResolveInAllThemesTest {

    private val context: Context = RuntimeEnvironment.getApplication()
    private val daxColorAttrs = R.attr::class.java.fields.filter { it.name.startsWith("daxColor") }
    private val onboardingColorAttrs = R.attr::class.java.fields.filter {
        it.name.startsWith("onboarding") || it.name.startsWith("quickSetup")
    }
    private val baseThemes = listOf(
        "Theme_DuckDuckGo_Light",
        "Theme_DuckDuckGo_Dark",
        "Theme_DuckDuckGo_New_Light",
        "Theme_DuckDuckGo_New_Dark",
    )
    private val selectorAttrs = listOf(
        "daxThemeLight",
        "daxThemeDark",
        "daxOnboardingThemeLight",
        "daxOnboardingThemeDark",
        "daxOnboardingThemeOverlay",
        "daxOmnibarThemeOverlay",
        "daxInputModeCardThemeOverlay",
        "daxInputModeCardDarkFireThemeOverlay",
    )

    private fun styles(prefix: String) = R.style::class.java.fields.filter { it.name.startsWith(prefix) }

    private fun styleId(styleName: String) = R.style::class.java.getField(styleName).getInt(null)

    private fun themeOf(styleName: String): Resources.Theme = ContextThemeWrapper(context, styleId(styleName)).theme

    private fun attrId(name: String) = R.attr::class.java.getField(name).getInt(null)

    private fun resolve(theme: Resources.Theme, attr: Int): TypedValue? = TypedValue().takeIf { theme.resolveAttribute(attr, it, true) }

    // A colour is an inline colour or a colour resource, which resolves to a state list.
    private fun TypedValue.isColour() =
        type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT ||
            (type == TypedValue.TYPE_STRING && "$string".startsWith("res/color"))

    // Colour resources resolve to the same resource id, inline colours to the same data.
    private fun TypedValue?.identity() = this?.let { "${it.type}/${it.data}/${it.resourceId}/${it.string}" }

    // Runs [block] with the day, then the night resources, passing the mode of that configuration.
    private fun inDayAndNight(block: (mode: String) -> Unit) {
        listOf("notnight" to "Light", "night" to "Dark").forEach { (qualifier, mode) ->
            RuntimeEnvironment.setQualifiers("+$qualifier")
            assertEquals("Qualifier $qualifier was not applied", mode == "Dark", context.isInNightMode())
            block(mode)
        }
    }

    @Test
    fun everyDaxColorAttributeResolvesToAColourInEveryThemeAndOverlay() = inDayAndNight { mode ->
        assertTrue("No daxColor attributes found", daxColorAttrs.isNotEmpty())
        // The un-suffixed base and the splash themes are not full themes, so they do not define the colours.
        val themes = styles("Theme_DuckDuckGo").filterNot { it.name == "Theme_DuckDuckGo" || it.name.startsWith("Theme_DuckDuckGo_Splash") }
        val overlays = styles("ThemeOverlay_DuckDuckGo")

        val themeTargets = themes.map { it.name to themeOf(it.name) }
        val overlayTargets = overlays.flatMap { overlay ->
            baseThemes.map { base -> "${overlay.name} over $base" to themeOf(base).also { it.applyStyle(overlay.getInt(null), true) } }
        }
        val gaps = (themeTargets + overlayTargets).flatMap { (name, theme) ->
            daxColorAttrs.filterNot { resolve(theme, it.getInt(null))?.isColour() == true }.map { "$name: ${it.name}" }
        }

        assertTrue("$mode: attributes missing or not a colour:\n${gaps.joinToString("\n")}", gaps.isEmpty())
    }

    @Test
    fun newThemesOverrideEveryColourTheOldThemesSet() {
        val gaps = listOf("Light", "Dark").flatMap { mode ->
            val missing = items("design-system-theming.xml", "Theme.DuckDuckGo.$mode").filter { isColour(it.key, it.value) }.keys -
                items("design-system-theming-new.xml", "Theme.DuckDuckGo.New.$mode").keys
            missing.map { "Theme.DuckDuckGo.New.$mode: $it" }
        }

        assertTrue("Colours set in the old theme but not overridden in the new one:\n${gaps.joinToString("\n")}", gaps.isEmpty())
    }

    @Test
    fun selectorAttributesPointIntoTheirOwnHierarchy() = inDayAndNight { mode ->
        val themes = styles("Theme_DuckDuckGo").filterNot { it.name == "Theme_DuckDuckGo" || it.name.startsWith("Theme_DuckDuckGo_Splash") }
        val wrong = themes.flatMap { style ->
            val isNew = style.name.startsWith("Theme_DuckDuckGo_New")
            selectorAttrs.mapNotNull { selector ->
                val target = resolve(themeOf(style.name), attrId(selector))?.resourceId ?: return@mapNotNull null
                val entry = context.resources.getResourceEntryName(target)
                // The empty `.None` overlays carry no colours, so both hierarchies share them.
                val inHierarchy = entry.endsWith(".None") || entry.contains(".New.") == isNew
                "${style.name}: $selector -> $entry".takeIf { !inHierarchy }
            }
        }

        assertTrue("$mode: selectors pointing into the other hierarchy:\n${wrong.joinToString("\n")}", wrong.isEmpty())
        listOf("", "New_").forEach { family ->
            val theme = themeOf("Theme_DuckDuckGo_${family}Dark")
            assertEquals(styleId("Theme_DuckDuckGo_${family}Light"), resolve(theme, R.attr.daxThemeLight)?.resourceId)
            assertEquals(styleId("Theme_DuckDuckGo_${family}Dark"), resolve(theme, R.attr.daxThemeDark)?.resourceId)
        }
    }

    // A new-theme overlay with an old parent keeps its `.New.` name, so compare the colours it inherits.
    @Test
    fun newThemeOverlaysDoNotInheritOldColours() = inDayAndNight { mode ->
        val oldAndNew = listOf("Light", "Dark").map { themeOf("Theme_DuckDuckGo_$it") to themeOf("Theme_DuckDuckGo_New_$it") }
        val wrong = styles("Theme_DuckDuckGo_New").flatMap { style ->
            selectorAttrs.mapNotNull { resolve(themeOf(style.name), attrId(it))?.resourceId }
                .filterNot { context.resources.getResourceEntryName(it).endsWith(".None") }
                .mapNotNull { overlayId ->
                    val overlay = context.resources.newTheme().also { it.applyStyle(overlayId, true) }
                    // Flag it when, for a mode, it has the old value of most colours the new theme changed.
                    val inheritsOld = oldAndNew.any { (old, new) ->
                        val changed = daxColorAttrs.map { it.getInt(null) }.filter { resolve(old, it).identity() != resolve(new, it).identity() }
                        changed.count { resolve(overlay, it)?.identity() == resolve(old, it).identity() } * 2 > changed.size
                    }
                    "${style.name} -> ${context.resources.getResourceEntryName(overlayId)}".takeIf { inheritsOld }
                }
        }

        assertTrue("$mode: new theme overlays inheriting old colours:\n${wrong.distinct().joinToString("\n")}", wrong.isEmpty())
    }

    @Test
    fun onboardingOverlayResolvesTheOnboardingColoursOfTheCurrentMode() = inDayAndNight { mode ->
        assertTrue("No onboarding attributes found", onboardingColorAttrs.isNotEmpty())
        listOf("Theme_DuckDuckGo_", "Theme_DuckDuckGo_New_").forEach { family ->
            val overlay = resolve(themeOf("$family$mode"), R.attr.daxOnboardingThemeOverlay)!!.resourceId
            val themed = themeOf("$family$mode").also { it.applyStyle(overlay, true) }
            val expected = themeOf("$family${mode}_Onboarding")
            val wrong = onboardingColorAttrs.filterNot {
                val actual = resolve(themed, it.getInt(null))
                actual?.isColour() == true && actual.identity() == resolve(expected, it.getInt(null)).identity()
            }
            assertTrue("$family$mode: onboarding attributes not the $mode onboarding colours:\n${wrong.joinToString { it.name }}", wrong.isEmpty())
        }
    }

    // A colour item is a daxColor attribute or any item with a colour value.
    private fun isColour(name: String, value: String) = name.startsWith("daxColor") || value.startsWith("@color/") || value.startsWith("#")

    private fun items(file: String, style: String): Map<String, String> {
        val xml = File("src/main/res/values/$file").readText()
        val body = Regex("""<style name="${Regex.escape(style)}"[^>]*>(.*?)</style>""", RegexOption.DOT_MATCHES_ALL).find(xml)!!.groupValues[1]
        return Regex("""<item name="([^"]+)"[^>]*>([^<]*)</item>""").findAll(body).associate { it.groupValues[1] to it.groupValues[2].trim() }
    }
}
