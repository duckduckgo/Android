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

package com.duckduckgo.contentscopeprivacyfeatures.ksp

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.validate

class ContentScopePrivacyFeatureProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger,
) : SymbolProcessor {

    private val annotationName = "com.duckduckgo.contentscopeprivacyfeatures.api.ContentScopePrivacyFeature"
    private val generatedPackage = "com.duckduckgo.contentscopeprivacyfeatures.impl.generated"

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(annotationName)
        val unprocessed = mutableListOf<KSAnnotated>()

        symbols.forEach { symbol ->
            if (!symbol.validate()) {
                unprocessed.add(symbol)
                return@forEach
            }

            if (symbol !is KSClassDeclaration || symbol.classKind != ClassKind.INTERFACE) {
                logger.error("@ContentScopePrivacyFeature can only be applied to interfaces", symbol)
                return@forEach
            }

            val annotation = symbol.annotations.first {
                it.annotationType.resolve().declaration.qualifiedName?.asString() == annotationName
            }
            val featureName = annotation.arguments.first { it.name?.asString() == "featureName" }.value as String

            if (featureName.isBlank()) {
                logger.error("@ContentScopePrivacyFeature featureName must not be blank", symbol)
                return@forEach
            }

            val pascalName = featureName.replaceFirstChar { it.uppercase() }
            val containingFiles = listOfNotNull(symbol.containingFile)

            generateRepository(featureName, pascalName, containingFiles)
            generatePlugin(featureName, pascalName, containingFiles)
            generateConfigPlugin(featureName, pascalName, containingFiles)
        }

        return unprocessed
    }

    private fun generateRepository(featureName: String, pascalName: String, containingFiles: List<KSFile>) {
        val className = "${pascalName}ContentScopePrivacyFeatureRepository"
        val code = """
            |package $generatedPackage
            |
            |import android.annotation.SuppressLint
            |import android.content.SharedPreferences
            |import com.duckduckgo.app.di.AppCoroutineScope
            |import com.duckduckgo.app.di.IsMainProcess
            |import com.duckduckgo.common.utils.DispatcherProvider
            |import com.duckduckgo.data.store.api.SharedPreferencesProvider
            |import com.duckduckgo.di.scopes.AppScope
            |import dagger.SingleInstanceIn
            |import javax.inject.Inject
            |import kotlinx.coroutines.CoroutineScope
            |import kotlinx.coroutines.launch
            |
            |@SingleInstanceIn(AppScope::class)
            |class $className @Inject constructor(
            |    private val sharedPreferencesProvider: SharedPreferencesProvider,
            |    private val dispatcherProvider: DispatcherProvider,
            |    @AppCoroutineScope private val coroutineScope: CoroutineScope,
            |    @IsMainProcess private val isMainProcess: Boolean,
            |) {
            |    @Volatile
            |    private var jsonData: String = "{}"
            |
            |    private val preferences: SharedPreferences by lazy {
            |        sharedPreferencesProvider.getSharedPreferences("com.duckduckgo.contentscopeprivacyfeatures.$featureName")
            |    }
            |
            |    init {
            |        coroutineScope.launch(dispatcherProvider.io()) {
            |            if (isMainProcess) {
            |                loadToMemory()
            |            }
            |        }
            |    }
            |
            |    fun getJsonData(): String = jsonData
            |
            |    @SuppressLint("UseKtx")
            |    fun insertJsonData(jsonData: String): Boolean {
            |        val success = preferences.edit().putString("json_data", jsonData).commit()
            |        if (success) {
            |            this.jsonData = jsonData
            |        }
            |        return success
            |    }
            |
            |    private fun loadToMemory() {
            |        jsonData = preferences.getString("json_data", null) ?: "{}"
            |    }
            |}
            |
        """.trimMargin()

        writeFile(className, code, containingFiles)
    }

    private fun generatePlugin(featureName: String, pascalName: String, containingFiles: List<KSFile>) {
        val className = "${pascalName}ContentScopePrivacyFeaturePlugin"
        val repoClassName = "${pascalName}ContentScopePrivacyFeatureRepository"
        val code = """
            |package $generatedPackage
            |
            |import com.duckduckgo.di.scopes.AppScope
            |import com.duckduckgo.privacy.config.api.PrivacyFeaturePlugin
            |import com.squareup.anvil.annotations.ContributesMultibinding
            |import javax.inject.Inject
            |
            |@ContributesMultibinding(AppScope::class)
            |class $className @Inject constructor(
            |    private val repository: $repoClassName,
            |) : PrivacyFeaturePlugin {
            |    override val featureName: String = "$featureName"
            |
            |    override fun store(featureName: String, jsonString: String): Boolean {
            |        return if (featureName == this.featureName) {
            |            repository.insertJsonData(jsonString)
            |        } else {
            |            false
            |        }
            |    }
            |}
            |
        """.trimMargin()

        writeFile(className, code, containingFiles)
    }

    private fun generateConfigPlugin(featureName: String, pascalName: String, containingFiles: List<KSFile>) {
        val className = "${pascalName}ContentScopePrivacyFeatureConfigPlugin"
        val repoClassName = "${pascalName}ContentScopePrivacyFeatureRepository"
        val code = """
            |package $generatedPackage
            |
            |import com.duckduckgo.contentscopescripts.api.ContentScopeConfigPlugin
            |import com.duckduckgo.di.scopes.AppScope
            |import com.squareup.anvil.annotations.ContributesMultibinding
            |import javax.inject.Inject
            |
            |@ContributesMultibinding(AppScope::class)
            |class $className @Inject constructor(
            |    private val repository: $repoClassName,
            |) : ContentScopeConfigPlugin {
            |    override fun config(): String = "\"$featureName\":" + repository.getJsonData()
            |
            |    override fun preferences(): String? = null
            |}
            |
        """.trimMargin()

        writeFile(className, code, containingFiles)
    }

    private fun writeFile(className: String, code: String, containingFiles: List<KSFile>) {
        codeGenerator.createNewFile(
            Dependencies(true, *containingFiles.toTypedArray()),
            generatedPackage,
            className,
        ).bufferedWriter().use { it.write(code) }
    }
}
