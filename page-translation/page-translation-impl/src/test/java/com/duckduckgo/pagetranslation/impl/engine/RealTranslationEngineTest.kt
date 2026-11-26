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

package com.duckduckgo.pagetranslation.impl.engine

import android.content.ComponentCallbacks2
import android.content.Context
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDirectories
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackScheduler
import com.duckduckgo.pagetranslation.impl.offer.Detection
import com.duckduckgo.pagetranslation.impl.translatekit.TranslateKitLibrary
import com.duckduckgo.pagetranslation.impl.translatekit.Translator
import com.duckduckgo.translatekit.LanguageResult
import com.duckduckgo.translatekit.ModelSpec
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.io.File
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class RealTranslationEngineTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val library = FakeTranslateKitLibrary()
    private val scheduler: LanguagePackScheduler = mock()
    private val context: Context = mock()
    private val lifecycleOwner: LifecycleOwner = mock()
    private val directories = object : LanguagePackDirectories {
        override fun getStagingDirectory(pair: String) = error("unused")
        override fun getPackDirectory(pair: String): File = temporaryFolder.root.resolve(pair)
        override fun getOldDirectory(pair: String) = error("unused")
        override fun getInstalledPairs() = error("unused")
        override fun getPairsWithOldDirectory() = error("unused")
    }

    private val engine = RealTranslationEngine(
        context,
        library,
        directories,
        scheduler,
        coroutineRule.testScope,
        coroutineRule.testDispatcherProvider,
    )

    @Test
    fun detectTheLanguageOfTheText() = runEngineTest {
        library.detection = LanguageResult(language = "es", isReliable = true, topLanguages = emptyList())

        assertEquals(Detection("es", isReliable = true), call { engine.detectLanguage("hola", visible) })
        assertEquals(listOf("hola"), library.detectedTexts)
    }

    @Test
    fun detectNothingWhenNoLanguageIsFound() = runEngineTest {
        library.detection = LanguageResult(language = null, isReliable = false, topLanguages = emptyList())

        assertNull(call { engine.detectLanguage("1234", visible) })
    }

    @Test
    fun detectNothingWhenTranslateKitIsNotLoaded() = runEngineTest {
        library.isLibraryAvailable = false

        assertNull(call { engine.detectLanguage("hola", visible) })
        assertEquals(emptyList<String>(), library.detectedTexts)
    }

    @Test
    fun loadTheInstalledPackWithOneWorkerAndTranslateWithIt() = runEngineTest {
        install(ES_EN, sharedVocab = true)

        val result = call { engine.translate(ES_EN, listOf(plain("hola"), plain("adiós")), visible) }

        assertEquals(BatchTranslation.Translated(listOf("hola [es-en]", "adiós [es-en]")), result)
        val spec = library.loadedSpecs.single()
        val packDirectory = directories.getPackDirectory("es-en")
        assertEquals("es", spec.sourceLang)
        assertEquals("en", spec.targetLang)
        assertEquals(packDirectory.resolve("model.bin").path, spec.modelPath)
        assertEquals(listOf(packDirectory.resolve("vocab.spm").path), spec.vocabPaths)
        assertEquals(packDirectory.resolve("lexicalShortlist.bin").path, spec.shortlistPath)
        assertEquals(1, spec.numWorkers)
    }

    @Test
    fun loadTheModelInTheFormatItWasInstalledIn() = runEngineTest {
        install(ES_EN)
        directories.getPackDirectory("es-en").run { resolve("model.bin").renameTo(resolve("model.npz")) }

        call { engine.translate(ES_EN, listOf(plain("hola")), visible) }

        assertEquals(directories.getPackDirectory("es-en").resolve("model.npz").path, library.loadedSpecs.single().modelPath)
    }

    @Test
    fun loadSeparateSourceAndTargetVocabularies() = runEngineTest {
        install(ES_EN, sharedVocab = false)

        call { engine.translate(ES_EN, listOf(plain("hola")), visible) }

        val packDirectory = directories.getPackDirectory("es-en")
        assertEquals(
            listOf(packDirectory.resolve("srcVocab.spm").path, packDirectory.resolve("trgVocab.spm").path),
            library.loadedSpecs.single().vocabPaths,
        )
    }

    @Test
    fun keepTheLoadedModelForTheSamePair() = runEngineTest {
        install(ES_EN)

        repeat(3) { call { engine.translate(ES_EN, listOf(plain("hola")), visible) } }

        assertEquals(1, library.loadedSpecs.size)
        assertEquals(listOf(false), library.translators.map { it.isClosed })
    }

    @Test
    fun swapTheModelForAnotherPair() = runEngineTest {
        install(ES_EN)
        install(DE_EN)

        call { engine.translate(ES_EN, listOf(plain("hola")), visible) }
        val result = call { engine.translate(DE_EN, listOf(plain("hallo")), visible) }

        assertEquals(BatchTranslation.Translated(listOf("hallo [de-en]")), result)
        assertEquals(listOf(true, false), library.translators.map { it.isClosed })
    }

    @Test
    fun stopTranslatingWithAPackDeletedWhileItsModelIsLoaded() = runEngineTest {
        install(ES_EN)
        call { engine.translate(ES_EN, listOf(plain("hola")), visible) }
        directories.getPackDirectory("es-en").deleteRecursively()

        assertEquals(BatchTranslation.Failed, call { engine.translate(ES_EN, listOf(plain("adiós")), visible) })
        assertEquals(listOf(true), library.translators.map { it.isClosed })
    }

    @Test
    fun failWithoutAnInstalledPack() = runEngineTest {
        assertEquals(BatchTranslation.Failed, call { engine.translate(ES_EN, listOf(plain("hola")), visible) })
        assertEquals(emptyList<ModelSpec>(), library.loadedSpecs)
        verify(scheduler, never()).removeIfCorrupted(any())
    }

    @Test
    fun failWhenTranslateKitIsNotLoaded() = runEngineTest {
        install(ES_EN)
        library.isLibraryAvailable = false

        assertEquals(BatchTranslation.Failed, call { engine.translate(ES_EN, listOf(plain("hola")), visible) })
    }

    @Test
    fun failAndCheckThePackWhenItFailsToLoad() = runEngineTest {
        install(ES_EN)
        library.isLoadFailing = true

        assertEquals(BatchTranslation.Failed, call { engine.translate(ES_EN, listOf(plain("hola")), visible) })
        verify(scheduler).removeIfCorrupted("es-en")
    }

    @Test
    fun loadAPackThatFailedToLoadOnlyOnce() = runEngineTest {
        install(ES_EN)
        library.isLoadFailing = true

        repeat(2) { assertEquals(BatchTranslation.Failed, call { engine.translate(ES_EN, listOf(plain("hola")), visible) }) }

        assertEquals(1, library.loadedSpecs.size)
        verify(scheduler, times(1)).removeIfCorrupted("es-en")
    }

    @Test
    fun loadAPackAgainOnceItWasReplaced() = runEngineTest {
        install(ES_EN)
        library.isLoadFailing = true
        call { engine.translate(ES_EN, listOf(plain("hola")), visible) }

        library.isLoadFailing = false
        val packDirectory = directories.getPackDirectory("es-en")
        packDirectory.setLastModified(packDirectory.lastModified() + 1000)

        assertEquals(BatchTranslation.Translated(listOf("hola [es-en]")), call { engine.translate(ES_EN, listOf(plain("hola")), visible) })
    }

    @Test
    fun translateHtmlUnitsAsJsonTreesAndKeepTheUnitsOrder() = runEngineTest {
        install(ES_EN)

        val result = call { engine.translate(ES_EN, listOf(html("<b>uno</b>"), plain("dos"), html("<i>tres</i>")), visible) }

        assertEquals(BatchTranslation.Translated(listOf("tree(<b>uno</b>)", "dos [es-en]", "tree(<i>tres</i>)")), result)
    }

    @Test
    fun skipAUnitWhoseTranslatedHtmlIsMalformed() = runEngineTest {
        install(ES_EN)

        val result = call { engine.translate(ES_EN, listOf(html(MALFORMED_OUTPUT), html("<b>uno</b>")), visible) }

        assertEquals(BatchTranslation.Translated(listOf(null, "tree(<b>uno</b>)")), result)
    }

    @Test
    fun translateTheOtherUnitsWhenOneFailsTheBatch() = runEngineTest {
        install(ES_EN)

        val result = call { engine.translate(ES_EN, listOf(html("<b>uno</b>"), html(FAILING_INPUT), html("<i>dos</i>")), visible) }

        assertEquals(BatchTranslation.Translated(listOf("tree(<b>uno</b>)", null, "tree(<i>dos</i>)")), result)
    }

    @Test
    fun runTheVisibleTabsCallsFirst() = runEngineTest {
        install(ES_EN)

        val hiddenTabCall = async { engine.translate(ES_EN, listOf(plain("hidden")), hidden) }
        val visibleTabCall = async { engine.translate(ES_EN, listOf(plain("visible")), visible) }
        runCurrent()

        assertEquals(listOf("visible", "hidden"), library.translatedInputs)
        assertTrue(hiddenTabCall.isCompleted && visibleTabCall.isCompleted)
    }

    @Test
    fun unloadTheModelWhenIdle() = runEngineTest {
        install(ES_EN)
        call { engine.translate(ES_EN, listOf(plain("hola")), visible) }

        advanceTimeBy(59.seconds)
        runCurrent()
        assertEquals(listOf(false), library.translators.map { it.isClosed })

        advanceTimeBy(1.minutes)
        runCurrent()
        assertEquals(listOf(true), library.translators.map { it.isClosed })
    }

    @Test
    fun keepTheModelLoadedWhileCallsKeepComing() = runEngineTest {
        install(ES_EN)

        repeat(3) {
            call { engine.translate(ES_EN, listOf(plain("hola")), visible) }
            advanceTimeBy(50.seconds)
            runCurrent()
        }

        assertEquals(listOf(false), library.translators.map { it.isClosed })
    }

    @Test
    fun unloadTheModelInTheBackground() = runEngineTest {
        install(ES_EN)
        call { engine.translate(ES_EN, listOf(plain("hola")), visible) }

        engine.onStop(lifecycleOwner)
        runCurrent()

        assertEquals(listOf(true), library.translators.map { it.isClosed })
    }

    @Test
    fun unloadTheModelWhenTheSystemTrimsMemory() = runEngineTest {
        install(ES_EN)
        engine.onCreate(lifecycleOwner)
        val callbacks = argumentCaptor<ComponentCallbacks2>().also { verify(context).registerComponentCallbacks(it.capture()) }.firstValue
        call { engine.translate(ES_EN, listOf(plain("hola")), visible) }

        callbacks.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
        runCurrent()

        assertEquals(listOf(true), library.translators.map { it.isClosed })
    }

    @Test
    fun reloadTheModelAfterItWasUnloaded() = runEngineTest {
        install(ES_EN)
        call { engine.translate(ES_EN, listOf(plain("hola")), visible) }
        engine.onStop(lifecycleOwner)
        runCurrent()

        val result = call { engine.translate(ES_EN, listOf(plain("adiós")), visible) }

        assertEquals(BatchTranslation.Translated(listOf("adiós [es-en]")), result)
        assertEquals(listOf(true, false), library.translators.map { it.isClosed })
    }

    private fun runEngineTest(block: suspend TestScope.() -> Unit) = coroutineRule.testScope.runTest { block() }

    private suspend fun <T> TestScope.call(block: suspend () -> T): T {
        val result = async { block() }
        runCurrent()
        return result.await()
    }

    private fun install(pair: LanguagePair, sharedVocab: Boolean = true) {
        val files = if (sharedVocab) {
            listOf(
                "model.bin",
                "vocab.spm",
                "lexicalShortlist.bin",
            )
        } else {
            listOf("model.bin", "srcVocab.spm", "trgVocab.spm", "lexicalShortlist.bin")
        }
        val directory = directories.getPackDirectory(pair.key).apply { mkdirs() }
        files.forEach { directory.resolve(it).writeText(it) }
    }

    private fun plain(text: String) = TranslationUnit(text, isHtml = false)

    private fun html(text: String) = TranslationUnit(text, isHtml = true)

    private class FakeTranslateKitLibrary : TranslateKitLibrary {
        var isLibraryAvailable = true
        var isLoadFailing = false
        var detection = LanguageResult(language = null, isReliable = false, topLanguages = emptyList())
        val detectedTexts = mutableListOf<String>()
        val loadedSpecs = mutableListOf<ModelSpec>()
        val translators = mutableListOf<FakeTranslator>()
        val translatedInputs = mutableListOf<String>()

        override fun init() = isLibraryAvailable

        override fun decompressZstd(compressedFile: File, outputFile: File) = Unit

        override fun detectLanguage(text: String): LanguageResult {
            detectedTexts += text
            return detection
        }

        override fun loadTranslator(spec: ModelSpec): Translator {
            loadedSpecs += spec
            if (isLoadFailing) throw IllegalArgumentException("translate-kit loadModel failed")
            return FakeTranslator("${spec.sourceLang}-${spec.targetLang}", translatedInputs).also { translators += it }
        }
    }

    private class FakeTranslator(private val pair: String, private val translatedInputs: MutableList<String>) : Translator {
        var isClosed = false

        override fun translate(inputs: List<String>, isHtml: Boolean): List<String> {
            check(!isClosed) { "TranslationModel has been closed" }
            if (FAILING_INPUT in inputs) throw IllegalArgumentException("malformed HTML")
            translatedInputs += inputs
            return inputs.map {
                when {
                    !isHtml -> "$it [$pair]"
                    it == MALFORMED_OUTPUT -> "null"
                    else -> "tree($it)"
                }
            }
        }

        override fun close() {
            isClosed = true
        }
    }

    private companion object {
        val ES_EN = LanguagePair("es", "en")
        val DE_EN = LanguagePair("de", "en")
        const val FAILING_INPUT = "<b>unclosed"
        const val MALFORMED_OUTPUT = "<i>malformed output</i>"

        val visible = { true }
        val hidden = { false }
    }
}
