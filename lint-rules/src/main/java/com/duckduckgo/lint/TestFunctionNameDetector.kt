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

package com.duckduckgo.lint

import com.android.tools.lint.detector.api.AnnotationInfo
import com.android.tools.lint.detector.api.AnnotationUsageInfo
import com.android.tools.lint.detector.api.AnnotationUsageType
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.LintFix
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity.WARNING
import com.android.tools.lint.detector.api.SourceCodeScanner
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.ChatLanguageModel
import dev.langchain4j.model.ollama.OllamaChatModel
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.toKtPsiSourceElement
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.kotlin.KotlinUMethod
import org.jetbrains.uast.skipParentOfType
import java.util.EnumSet
import kotlin.LazyThreadSafetyMode.SYNCHRONIZED
import kotlin.io.path.Path

@Suppress("UnstableApiUsage")
class TestFunctionNameDetector : Detector(), SourceCodeScanner {

    /**
     * Prevents conflicting overloads from using the same name twice
     * when the LM cannot distinguish two test cases.
     */
    private val usedNames = hashSetOf<String>()

    override fun beforeCheckFile(context: Context) {
        super.beforeCheckFile(context)
        usedNames.clear()
    }

    override fun applicableAnnotations() = listOf("org.junit.Test")

    override fun isApplicableAnnotationUsage(type: AnnotationUsageType) = true

    override fun visitAnnotationUsage(
        context: JavaContext,
        element: UElement,
        annotationInfo: AnnotationInfo,
        usageInfo: AnnotationUsageInfo,
    ) {
        if (context.isAndroidTest()) return

        // Skip parameterized tests
        val containerClass = element.getParentOfType<UClass>() ?: return
        val annotation = containerClass.getAnnotation("org.junit.runner.RunWith")
        if (annotation != null) {
            if ("Parameterized" in annotation.parameterList.attributes.firstOrNull()?.value?.text.orEmpty()) {
                return
            }
        }

        val method = element.uastParent as? KotlinUMethod ?: return

        // Get the name in a way that is certain to retain the backticks.
        val functionName = (method.sourcePsi as? KtNamedDeclaration)?.nameIdentifier?.text ?: return

        val error = functionName.backticksErrorOrNull() ?: functionName.partsErrorOrNull() ?: functionName.capitalizationErrorOrNull() ?: return

        context.report(
            TEST_FUNCTION_NAME,
            context.getNameLocation(method),
            error.message,
            getLintFix(
                method,
                context,
            ),
        )
    }

    private fun String.backticksErrorOrNull(): Error? {
        if (this.startsWith('`') && this.endsWith('`')) return null
        return Error.BACKTICKS
    }

    private fun String.partsErrorOrNull(): Error? {
        val splits = this.split(" - ")
        return if (splits.size < 2) Error.PARTS else null
    }

    private fun String.capitalizationErrorOrNull(): Error? {
        val trimmed = trim('`')
        val splits = trimmed.split(" - ")
        return if (splits.all {
                it.firstOrNull()?.isUpperCase() == true
            }) {
            Error.CAPITALIZATION
        } else {
            null
        }
    }

    enum class Error(val message: String) {
        BACKTICKS("Test name should be in backticks."),
        PARTS("Test name should have two or three parts separated by a spaced hyphen in the form `functionUnderTest - state - expected outcome`"),
        CAPITALIZATION("Test name parts should not be capitalized")
    }

    private fun JavaContext.isAndroidTest() = Path("androidTest") in file.toPath()

    private fun getLintFix(
        method: KotlinUMethod,
        context: JavaContext
    ): LintFix? {
        if (Scope.ALL_JAVA_FILES !in context.scope) {
            // We're in the IDE so don't try and use the LLM to generate a LintFix.
            return null
        }

        val response = model.generate(prompt, UserMessage.from(method.sourcePsi!!.text)).content().text()
        context.log(null, "Response from LM: ")
        context.log(null, response)

        val firstBackTick = response.indexOfFirst { it == '`' }
        val secondBackTick = response.indexOfLast { it == '`' }
        if (firstBackTick == -1 || secondBackTick == -1) return null

        val proposedFunctionName = response.substring(firstBackTick..secondBackTick)

        if (proposedFunctionName.isEmpty()) {
            return null
        }

        if (!usedNames.add(proposedFunctionName)) {
            // We've already used this test name and we'd get a conflicting overloads error.
            return null
        }

        // Remove illegal characters
        val sanitizedFunctionName = proposedFunctionName.replace('.', '·').replace(':', '·').replace("/", "")

        if (sanitizedFunctionName.length > 120) {
            // It's too long and would break MAX_LINE_LENGTH. Leave this test to be migrated manually.
            return null
        }

        return LintFix.create().name("Use name suggested by language model").replace().all().with(sanitizedFunctionName).autoFix().build()
    }

    companion object {

        @JvmField val TEST_FUNCTION_NAME = Issue.create(
            id = "TestFunctionName",
            briefDescription = "Test function name",
            category = Category.TESTING,
            explanation = "The test function name should be enclosed in backticks. It should have either two or three parts, separated by hyphens. Each part should, where possible, start in lowercase",
            severity = WARNING,
            implementation = Implementation(
                TestFunctionNameDetector::class.java,
                EnumSet.of(Scope.JAVA_FILE, Scope.TEST_SOURCES),
                EnumSet.of(Scope.JAVA_FILE),
                EnumSet.of(Scope.TEST_SOURCES),
            ),
        )

        private val model: ChatLanguageModel by lazy(SYNCHRONIZED) {
            OllamaChatModel.builder().modelName("llama3").temperature(0.0).seed(0)
                /**
                 * Ollama binds 127.0.0.1 port 11434 by default. Change the bind address with the OLLAMA_HOST environment variable
                 * See https://github.com/ollama/ollama/blob/main/docs/faq.md
                 */
                .baseUrl("http://127.0.0.1:11434").build()
        }

        private val prompt: SystemMessage = SystemMessage.from(
            """
            You are a coding completion assistant working on an automated refactoring task for a Kotlin project.
             
            The unit test functions currently have various non-standard names. We are performing a migration from non-standard names to a new standard.
            
            The new standard for the names is:
            
            `methodUnderTest - state - expected outcome`
            
            Here:
            * "methodUnderTest" means the method that the test intends to exercise being exercised. If we're thinking of "arrange/act/assert"
            then the method under test is normally exercised in the "act" part of the test body i.e., the middle.
            * "state" means the setup or situation for the test. Thinking of "arrange/act/assert" the state would normally be the first part.
            Note that not all tests have state. For instance, tests with no set up or tests of pure functions.
            * "expected outcome" means what we are hoping to measure in the test. Thinking of "arrange/act/assert" this would be the "assert" part 
            (the last part of the test)
            
            Note that to meet the standard, the test names must have the following:
            * They must be in backticks (``)
            * They must have a minimum of two parts separated by a spaced hyphen " - "
            * The "state" (second part) is optional - it can be omitted to allow for tests with only two parts
            * The parts must start with lowercase if possible
            
            I am going to give you a Kotlin function to consider. You must propose a new name for the function that meets the convention. 
            Your answer MUST only contain the new proposed function name.
             
            Here is a sample input and output to help you.             
      
            Example input:
            
            @Test
            fun whenUserEnablesAutofillThenViewStateUpdatedToReflectChange() = runTest {
                testee.onEnableAutofill()
                testee.viewState.test {
                    assertTrue(this.awaitItem().autofillEnabled)
                    cancelAndIgnoreRemainingEvents()
                }
            }
            
            Expected output:
            `onEnableAutofill - viewState updated`
            
            Example input:
            
            @Test
            fun whenNotSignedIntoEmailProtectionThenReturnTypeIsNotSignedIn() = runTest {
                configureEmailProtectionNotSignedIn()
                val status = testee.getActivationStatus("foo@example.com")
                assertTrue(status is NotSignedIn)
            }
            
            Expected output:            
            `getActivationStatus - email protection not signed in - not signed in`

            Example input:
            
            @Test
            fun whenSaveCredentialsUnsuccessfulThenDoesNotDisableDeclineCountMonitoringFlag() = runTest {
                val bundle = bundle("example.com", someLoginCredentials())
                whenever(autofillStore.saveCredentials(any(), any())).thenReturn(null)
                testee.processResult(bundle, context, "tab-id-123", Fragment(), callback)
                verify(declineCounter, never()).disableDeclineCounter()
            }
            
            Expected output:
            `processResult - save credentials unsuccessful - does not disable decline count monitoring flag`

            I have given you three examples to follow.
            
            From now on, in this conversation I am going to give you the function body so you can devise a new name for it following the convention and examples. 
            
            Please answer with the expected output. Please DO NOT embellish the answer with extra information. Please DO NOT add three backticks
            to make a code block. Please ONLY answer with the proposed name of the function.
           """.trimIndent(),
        )
    }
}
