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

package com.duckduckgo.pagetranslation.impl.session

import android.webkit.WebView
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pagetranslation.impl.engine.BatchTranslation
import com.duckduckgo.pagetranslation.impl.engine.TranslationEngine
import com.duckduckgo.pagetranslation.impl.engine.TranslationUnit
import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import com.duckduckgo.pagetranslation.impl.messaging.PageTranslationMessaging
import com.duckduckgo.pagetranslation.impl.messaging.PageUnit
import com.duckduckgo.pagetranslation.impl.messaging.UnitTranslation
import com.duckduckgo.pagetranslation.impl.offer.Detection
import com.duckduckgo.pagetranslation.impl.session.TranslationState.Failed
import com.duckduckgo.pagetranslation.impl.session.TranslationState.Idle
import com.duckduckgo.pagetranslation.impl.session.TranslationState.Translated
import com.duckduckgo.pagetranslation.impl.session.TranslationState.Translating
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RealTranslationSessionTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    private val messaging = FakePageTranslationMessaging()
    private val engine = FakeTranslationEngine()
    private val isVisible = MutableStateFlow(true)
    private val state = MutableStateFlow<TranslationState>(Idle)
    private val session = RealTranslationSession(
        TIME_ORIGIN,
        messaging,
        engine,
        RealTranslationBatchPlanner(),
        isVisible,
        state,
        CoroutineScope(coroutineRule.testDispatcher),
    )

    @Test
    fun injectTheBundleIntoItsDocument() = runSessionTest {
        messaging.units = listOf(plain(0, "a", vp = true))

        translate()

        assertEquals(listOf(TIME_ORIGIN), messaging.injected)
    }

    @Test
    fun applyTheViewportsBatchesFirstThenTheRestInPageOrder() = runSessionTest {
        messaging.units = listOf(plain(0, "a"), plain(1, "b", vp = true), html(2, "<b>c</b>", vp = true), plain(3, "d"))

        translate()

        assertEquals(
            listOf(
                listOf(UnitTranslation(1, "B", isHtml = false)),
                listOf(UnitTranslation(2, "<B>C</B>", isHtml = true)),
                listOf(UnitTranslation(0, "A", isHtml = false), UnitTranslation(3, "D", isHtml = false)),
            ),
            messaging.applied,
        )
        assertEquals(Translated, state.value)
    }

    @Test
    fun reportTranslatedOnceTheViewportIsAppliedWhileTheRestStillTranslates() = runSessionTest {
        messaging.units = listOf(plain(0, "a", vp = true), plain(1, "b"))
        engine.holdBatchStartingWith("b")

        translate()

        assertEquals(Translated, state.value)
        assertEquals(listOf(listOf(0)), messaging.appliedIds)
    }

    @Test
    fun countTheFirstBatchAsTheViewportWhenNoUnitIsInIt() = runSessionTest {
        messaging.units = listOf(plain(0, "a"), html(1, "b"))
        engine.holdBatchStartingWith("b")

        translate()

        assertEquals(Translated, state.value)
        assertEquals(listOf(listOf(0)), messaging.appliedIds)
    }

    @Test
    fun reportTranslatedWhenThePageWritesFewerUnitsThanTheBatch() = runSessionTest {
        messaging.units = listOf(plain(0, "a", vp = true), plain(1, "b", vp = true))
        messaging.writtenCount = { 1 }

        translate()

        assertEquals(Translated, state.value)
    }

    @Test
    fun leaveAUnitTheEngineCouldNotTranslateOutOfTheApply() = runSessionTest {
        messaging.units = listOf(plain(0, "a", vp = true), plain(1, UNTRANSLATABLE, vp = true))

        translate()

        assertEquals(listOf(listOf(UnitTranslation(0, "A", isHtml = false))), messaging.applied)
    }

    @Test
    fun failWithAppliedUnitsWhenALaterViewportBatchFails() = runSessionTest {
        messaging.units = listOf(plain(0, "a", vp = true), html(1, FAILING, vp = true), plain(2, "c"))

        translate()

        assertEquals(Failed(hasAppliedUnits = true), state.value)
        assertEquals(listOf(listOf(0)), messaging.appliedIds)
    }

    @Test
    fun failWithoutAppliedUnitsWhenTheFirstViewportBatchFails() = runSessionTest {
        messaging.units = listOf(plain(0, FAILING, vp = true), plain(1, "b"))

        translate()

        assertEquals(Failed(hasAppliedUnits = false), state.value)
        assertEquals(emptyList<List<Int>>(), messaging.appliedIds)
    }

    @Test
    fun failWithoutAppliedUnitsWhenThePageDoesNotApply() = runSessionTest {
        messaging.units = listOf(plain(0, "a", vp = true))
        messaging.writtenCount = { null }

        translate()

        assertEquals(Failed(hasAppliedUnits = false), state.value)
    }

    @Test
    fun failWithoutAppliedUnitsWhenThePageWritesNothingInTheViewport() = runSessionTest {
        messaging.units = listOf(plain(0, "a", vp = true), plain(1, "b"))
        messaging.writtenCount = { 0 }

        translate()

        assertEquals(Failed(hasAppliedUnits = false), state.value)
        assertEquals(listOf(listOf(0)), messaging.appliedIds)
    }

    @Test
    fun failWithoutAppliedUnitsWhenThePageDoesNotExtract() = runSessionTest {
        messaging.units = null

        translate()

        assertEquals(Failed(hasAppliedUnits = false), state.value)
    }

    @Test
    fun stopSilentlyAndStayTranslatedWhenABatchFailsAfterTheViewport() = runSessionTest {
        messaging.units = listOf(plain(0, "a", vp = true), html(1, FAILING), plain(2, "c"))

        translate()

        assertEquals(Translated, state.value)
        assertEquals(listOf(listOf(0)), messaging.appliedIds)
    }

    @Test
    fun waitWhileHiddenThenContinueFromTheNextBatchAfterApplyingTheOneInFlight() = runSessionTest {
        messaging.units = listOf(plain(0, "a", vp = true), html(1, "b"), plain(2, "c"))
        val held = engine.holdBatchStartingWith("b")
        translate()

        isVisible.value = false
        held.complete(Unit)
        runCurrent()

        assertEquals(listOf(listOf(0), listOf(1)), messaging.appliedIds)

        isVisible.value = true
        runCurrent()

        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), messaging.appliedIds)
        assertEquals(3, engine.translatedTexts.size)
    }

    @Test
    fun startNothingWhileTheTabIsHidden() = runSessionTest {
        messaging.units = listOf(plain(0, "a", vp = true))
        isVisible.value = false

        session.translate(PAIR)
        runCurrent()

        assertEquals(Translating, state.value)
        assertEquals(emptyList<String>(), engine.translatedTexts)
    }

    @Test
    fun tellTheEngineWhetherTheTabIsVisible() = runSessionTest {
        messaging.units = listOf(plain(0, "a", vp = true))
        engine.holdBatchStartingWith("a")
        translate()

        assertTrue(engine.isVisible())
        isVisible.value = false
        assertFalse(engine.isVisible())
    }

    @Test
    fun dropTheLateResultsOfAnEndedSession() = runSessionTest {
        messaging.units = listOf(plain(0, "a", vp = true))
        val held = engine.holdBatchStartingWith("a")
        translate()

        session.end()
        held.complete(Unit)
        runCurrent()

        assertEquals(Translating, state.value)
        assertEquals(emptyList<List<Int>>(), messaging.appliedIds)
        assertEquals(1, messaging.cancelCount)
    }

    @Test
    fun translateADocumentOnlyOnce() = runSessionTest {
        messaging.units = listOf(plain(0, "a", vp = true))
        translate()

        session.translate(PAIR)
        runCurrent()

        assertEquals(listOf(listOf(0)), messaging.appliedIds)
    }

    private fun runSessionTest(block: suspend TestScope.() -> Unit) = coroutineRule.testScope.runTest { block() }

    private fun TestScope.translate() {
        session.translate(PAIR)
        runCurrent()
    }

    private fun plain(id: Int, text: String, vp: Boolean = false) = PageUnit(id, text, isHtml = false, isInViewport = vp)

    private fun html(id: Int, text: String, vp: Boolean = false) = PageUnit(id, text, isHtml = true, isInViewport = vp)

    private class FakePageTranslationMessaging : PageTranslationMessaging {
        var units: List<PageUnit>? = emptyList()
        var writtenCount: (List<UnitTranslation>) -> Int? = { it.size }
        val injected = mutableListOf<Double>()
        val applied = mutableListOf<List<UnitTranslation>>()
        val appliedIds: List<List<Int>>
            get() = applied.map { batch -> batch.map { it.id } }
        var cancelCount = 0

        override fun register(webView: WebView) = Unit

        override fun cancel() {
            cancelCount++
        }

        override suspend fun inject(timeOrigin: Double) {
            injected += timeOrigin
        }

        override suspend fun extract(): List<PageUnit>? = units

        override suspend fun apply(pair: LanguagePair, translations: List<UnitTranslation>): Int? {
            applied += translations
            return writtenCount(translations)
        }
    }

    // translates by uppercasing, fails a batch holding FAILING and can't translate UNTRANSLATABLE
    private class FakeTranslationEngine : TranslationEngine {
        val translatedTexts = mutableListOf<String>()
        lateinit var isVisible: () -> Boolean
        private val held = mutableMapOf<String, CompletableDeferred<Unit>>()

        fun holdBatchStartingWith(text: String) = CompletableDeferred<Unit>().also { held[text] = it }

        override suspend fun detectLanguage(text: String, isVisible: () -> Boolean): Detection? = null

        override suspend fun translate(pair: LanguagePair, units: List<TranslationUnit>, isVisible: () -> Boolean): BatchTranslation {
            this.isVisible = isVisible
            held[units.first().text]?.await()
            translatedTexts += units.map { it.text }
            if (units.any { it.text == FAILING }) return BatchTranslation.Failed

            return BatchTranslation.Translated(units.map { if (it.text == UNTRANSLATABLE) null else it.text.uppercase() })
        }
    }

    private companion object {
        val PAIR = LanguagePair("es", "en")
        const val TIME_ORIGIN = 1732634400123.456
        const val FAILING = "failing"
        const val UNTRANSLATABLE = "untranslatable"
    }
}
