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
import android.content.res.Configuration
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.pagetranslation.impl.PageTranslationFeature
import com.duckduckgo.pagetranslation.impl.language.LanguagePair
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackDirectories
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackScheduler
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifest.Companion.LEXICAL_SHORTLIST_FILE
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifest.Companion.MODEL_FILE
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifest.Companion.SOURCE_VOCAB_FILE
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifest.Companion.TARGET_VOCAB_FILE
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifest.Companion.VOCAB_FILE
import com.duckduckgo.pagetranslation.impl.offer.Detection
import com.duckduckgo.pagetranslation.impl.translatekit.TranslateKitLibrary
import com.duckduckgo.pagetranslation.impl.translatekit.Translator
import com.duckduckgo.translatekit.ModelSpec
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import logcat.LogPriority.ERROR
import logcat.asLog
import logcat.logcat
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

/** One piece of a page's text, as the C-S-S bundle's extract returns it. */
data class TranslationUnit(val text: String, val isHtml: Boolean)

sealed interface BatchTranslation {
    /**
     * One entry per unit, in the units' order: the translated text of a plain unit, translate-kit's JSON tree of an HTML unit,
     * or null for a unit that couldn't be translated.
     */
    data class Translated(val translations: List<String?>) : BatchTranslation

    /** Page translation is off, translate-kit isn't loaded, or the pair's language pack isn't installed or failed to load. */
    data object Failed : BatchTranslation
}

/**
 * Runs every translate-kit call for all tabs, one at a time. Whenever a call finishes, the next one is the oldest pending call
 * whose `isVisible` returns true, or the oldest pending call if none does. `isVisible` is read off the main thread.
 *
 * A caller that's canceled while its call waits drops it. A call that's already running finishes and its result is dropped.
 */
interface TranslationEngine {
    /** Returns null when page translation is off, translate-kit isn't loaded or it detects no language. */
    suspend fun detectLanguage(text: String, isVisible: () -> Boolean): Detection?

    /** Translates [units] with [pair]'s installed language pack, replacing another pair's loaded translator. */
    suspend fun translate(pair: LanguagePair, units: List<TranslationUnit>, isVisible: () -> Boolean): BatchTranslation
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<TranslationEngine>())
@ContributesIntoSet(AppScope::class, binding = binding<MainProcessLifecycleObserver>())
class RealTranslationEngine @Inject constructor(
    private val context: Context,
    private val library: TranslateKitLibrary,
    private val directories: LanguagePackDirectories,
    private val scheduler: LanguagePackScheduler,
    private val feature: PageTranslationFeature,
    @AppCoroutineScope private val appScope: CoroutineScope,
    dispatchers: DispatcherProvider,
) : TranslationEngine, MainProcessLifecycleObserver {
    // translate-kit's calls block, and a translator must not be closed while it translates
    private val engineDispatcher = dispatchers.io().limitedParallelism(1)

    private val callQueue = TranslationPriorityQueue(appScope, engineDispatcher)
    private val idleUnloadJob = ConflatedJob()

    // only touched on engineDispatcher
    private var loadedTranslator: LoadedTranslator? = null

    override fun onCreate(owner: LifecycleOwner) {
        context.registerComponentCallbacks(
            object : ComponentCallbacks2 {
                override fun onTrimMemory(level: Int) = unloadTranslatorSoon()

                override fun onConfigurationChanged(newConfig: Configuration) = Unit

                @Deprecated("Deprecated in Java")
                override fun onLowMemory() = unloadTranslatorSoon()
            },
        )
    }

    override fun onStop(owner: LifecycleOwner) = unloadTranslatorSoon()

    override suspend fun detectLanguage(text: String, isVisible: () -> Boolean): Detection? = runCall(isVisible) {
        if (!isAvailable()) return@runCall null

        val result = library.detectLanguage(text)
        result.language?.let { Detection(it, result.isReliable) }
    }

    override suspend fun translate(pair: LanguagePair, units: List<TranslationUnit>, isVisible: () -> Boolean): BatchTranslation =
        runCall(isVisible) {
            val translator = getTranslator(pair) ?: return@runCall BatchTranslation.Failed

            BatchTranslation.Translated(translateUnits(translator, units))
        }

    private suspend fun <T> runCall(isVisible: () -> Boolean, block: () -> T): T = callQueue.run(isVisible) {
        idleUnloadJob.cancel()
        try {
            block()
        } finally {
            idleUnloadJob += appScope.launch(engineDispatcher) {
                delay(IDLE_TIMEOUT)
                unloadTranslator()
            }
        }
    }

    private fun getTranslator(pair: LanguagePair): Translator? {
        val packDirectory = directories.getPackDirectory(pair.key)

        // a pack deleted or replaced while its translator is loaded must not keep translating
        loadedTranslator?.takeIf { it.pair == pair && packDirectory.exists() }?.let { return it.translator }

        unloadTranslator()
        if (!isAvailable() || !packDirectory.exists()) return null

        // an installed file is named after its manifest key, with the extension that tells translate-kit its format
        val files = packDirectory.listFiles().orEmpty().associateBy { it.nameWithoutExtension }
        fun path(key: String) = (files[key] ?: packDirectory.resolve(key)).path
        val spec = ModelSpec(
            sourceLang = pair.source,
            targetLang = pair.target,
            modelPath = path(MODEL_FILE),
            vocabPaths = if (VOCAB_FILE in files) {
                listOf(path(VOCAB_FILE))
            } else {
                listOf(path(SOURCE_VOCAB_FILE), path(TARGET_VOCAB_FILE))
            },
            shortlistPath = path(LEXICAL_SHORTLIST_FILE),
            numWorkers = 1,
        )

        return try {
            library.loadTranslator(spec).also { loadedTranslator = LoadedTranslator(pair, it) }
        } catch (e: IllegalArgumentException) {
            logcat(ERROR) { "Page translation language pack failed to load: ${e.asLog()}" }
            scheduler.removeIfCorrupted(pair.key)
            null
        }
    }

    private fun isAvailable() = feature.translation().isEnabled() && library.init()

    private fun translateUnits(translator: Translator, units: List<TranslationUnit>): List<String?> {
        val translations = arrayOfNulls<String>(units.size)
        units.indices.groupBy { units[it].isHtml }.forEach { (isHtml, indices) ->
            translateAll(
                translator,
                indices.map { units[it].text },
                isHtml,
            ).forEachIndexed { i, translation -> translations[indices[i]] = translation }
        }

        return translations.asList()
    }

    // one malformed input fails translate-kit's whole batch, so a failed batch is translated again one input at a time
    private fun translateAll(translator: Translator, texts: List<String>, isHtml: Boolean): List<String?> = try {
        translator.translate(texts, isHtml).map { if (isHtml && it == MALFORMED_HTML_TREE) null else it }
    } catch (e: RuntimeException) {
        if (texts.size == 1) {
            logcat(ERROR) { "Page translation unit failed to translate: ${e.asLog()}" }
            listOf(null)
        } else {
            texts.map { translateAll(translator, listOf(it), isHtml).single() }
        }
    }

    private fun unloadTranslatorSoon() {
        appScope.launch(engineDispatcher) {
            idleUnloadJob.cancel()
            unloadTranslator()
        }
    }

    private fun unloadTranslator() {
        loadedTranslator?.translator?.close()
        loadedTranslator = null
    }

    private class LoadedTranslator(val pair: LanguagePair, val translator: Translator)

    private companion object {
        val IDLE_TIMEOUT = 1.minutes
        const val MALFORMED_HTML_TREE = "null"
    }
}
