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

package com.duckduckgo.contentscopescripts.impl

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ContentScopeScriptPerfWrapperTest {

    private val appBuildConfig: AppBuildConfig = mock()
    private val testee = ContentScopeScriptPerfWrapper(appBuildConfig)

    @Test
    fun whenBuildIsNotInternalThenScriptIsReturnedUnchanged() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)

        assertEquals(BUNDLE, testee.wrap(BUNDLE))
    }

    @Test
    fun whenBuildIsFdroidThenScriptIsReturnedUnchanged() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.FDROID)

        assertEquals(BUNDLE, testee.wrap(BUNDLE))
    }

    @Test
    fun whenBuildIsInternalThenBundleIsPreservedVerbatim() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        assertTrue(testee.wrap(BUNDLE).contains(BUNDLE))
    }

    @Test
    fun whenBuildIsInternalThenStartMarkPrecedesTheBundle() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        val wrapped = testee.wrap(BUNDLE)

        assertTrue(wrapped.indexOf("performance.mark('ddg-cs-start')") < wrapped.indexOf(BUNDLE))
    }

    @Test
    fun whenBuildIsInternalThenMeasureAndSummaryFollowTheBundle() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        val wrapped = testee.wrap(BUNDLE)
        val bundleEnd = wrapped.indexOf(BUNDLE) + BUNDLE.length

        assertTrue(wrapped.indexOf("performance.mark('ddg-cs-end')") > bundleEnd)
        assertTrue(wrapped.indexOf("performance.measure('ddg-contentscope'") > bundleEnd)
        assertTrue(wrapped.indexOf("window.__ddgPerf") > bundleEnd)
    }

    @Test
    fun whenBuildIsInternalThenSourceUrlIsTheFinalLine() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        val lines = testee.wrap(BUNDLE).trimEnd().lines()

        assertEquals("//# sourceURL=ddg-contentscope.js", lines.last())
    }

    @Test
    fun whenBundleHasNoTrailingSemicolonThenItDoesNotFuseIntoAppendedCode() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)
        val noSemicolon = "(function(){})()"

        val wrapped = testee.wrap(noSemicolon)
        val afterBundle = wrapped.substring(wrapped.indexOf(noSemicolon) + noSemicolon.length)

        assertTrue(afterBundle.startsWith("\n;"))
    }

    @Test
    fun whenBundleEndsWithLineCommentThenAppendedCodeStartsOnANewLine() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)
        val trailingComment = "var a = 1; // done"

        val wrapped = testee.wrap(trailingComment)
        val afterBundle = wrapped.substring(wrapped.indexOf(trailingComment) + trailingComment.length)

        assertTrue(afterBundle.startsWith("\n"))
    }

    @Test
    fun whenBuildIsInternalThenReportedSizeIsThatOfTheUnwrappedBundle() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        val expected = BUNDLE.toByteArray(Charsets.UTF_8).size

        assertTrue(testee.wrap(BUNDLE).contains("bytes: $expected"))
    }

    @Test
    fun whenBuildIsInternalThenAppendedCodeCannotThrowIntoThePage() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        val wrapped = testee.wrap(BUNDLE)

        assertTrue(wrapped.contains("try {"))
        assertTrue(wrapped.contains("catch (e) {}"))
    }

    @Test
    fun whenBuildIsInternalThenStartMarkIsGuardedAgainstThrowingIntoThePage() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        val wrapped = testee.wrap(BUNDLE)
        val beforeBundle = wrapped.substring(0, wrapped.indexOf(BUNDLE))

        // The prologue is PREPENDED, so an unguarded throw here (e.g. a page that clobbers
        // window.performance) would abort evaluation before the bundle's first statement. The try/catch
        // must open and close around the mark call, entirely before the bundle begins.
        val tryIndex = beforeBundle.indexOf("try {")
        val markIndex = beforeBundle.indexOf("performance.mark('ddg-cs-start')")
        val catchIndex = beforeBundle.indexOf("catch (e) {}")

        assertTrue("try must be present before the mark call", tryIndex in 0 until markIndex)
        assertTrue("catch must be present after the mark call, still before the bundle", catchIndex > markIndex)
    }

    @Test
    fun whenBuildIsInternalThenBundleIsNotEnclosedInAnOpenBlock() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        val wrapped = testee.wrap(BUNDLE)
        val bundleStart = wrapped.indexOf(BUNDLE)
        val bundleEnd = bundleStart + BUNDLE.length
        val beforeBundle = wrapped.substring(0, bundleStart)

        // A string-literal check for "try {" can be satisfied trivially (e.g. "try{" with no space) while
        // still leaving the bundle inside an open block. Balanced braces before the bundle is the real
        // invariant: it proves no block — try or otherwise — is still open where the bundle begins. The
        // guarded prologue also contains a "try {", so the epilogue's must be located by searching after
        // the bundle, not by the first occurrence in the whole string.
        assertEquals(beforeBundle.count { it == '{' }, beforeBundle.count { it == '}' })
        assertTrue("the epilogue's try must still run after the bundle", wrapped.indexOf("try {", bundleEnd) > bundleEnd)
    }

    companion object {
        private const val BUNDLE = "(function(){ var x = 1; })();"
    }
}
