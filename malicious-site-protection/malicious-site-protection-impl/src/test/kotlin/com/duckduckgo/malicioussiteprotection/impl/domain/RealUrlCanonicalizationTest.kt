package com.duckduckgo.malicioussiteprotection.impl.domain

import androidx.core.net.toUri
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

@RunWith(ParameterizedRobolectricTestRunner::class)
class RealUrlCanonicalizationTest(private val testCase: TestCase) {

    private val testee = RealUrlCanonicalization()

    @Test
    fun whenTestRunsItReturnsTheExpectedResult() = runTest {
        val result = testee.canonicalizeUrl(testCase.input.toUri())
        assertEquals(testCase.expected.toUri(), result)
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "Test case: {index} - {0}")
        fun testData(): List<TestCase> {
            return listOf(
                TestCase(
                    input = "https://broken.third-party.site/path/to/resource#fragment",
                    expected = "https://broken.third-party.site/path/to/resource",
                ),
                TestCase(
                    input = "https://broken.third-party.site/path/to/../resource",
                    expected = "https://broken.third-party.site/path/resource",
                ),
                TestCase(
                    input = "https://broken.third-party.site//path//to//resource",
                    expected = "https://broken.third-party.site/path/to/resource",
                ),
                TestCase(
                    input = "https://broken.third-party.site/path/to/resource%20with%20spaces%20%20%20%20",
                    expected = "https://broken.third-party.site/path/to/resource%20with%20spaces%20%20%20%20",
                ),
                TestCase(
                    input = "https://broken.third-party.site/path/to/resource%23encodedfragment",
                    expected = "https://broken.third-party.site/path/to/resource%23encodedfragment",
                ),
                TestCase(
                    input = "https://broken.third-party.site/path/./to/./resource",
                    expected = "https://broken.third-party.site/path/to/resource",
                ),
                TestCase(
                    input = "https://broken.third-party.site/path/to/resource%20with%20spaces",
                    expected = "https://broken.third-party.site/path/to/resource%20with%20spaces",
                ),
                TestCase(
                    input = "https://broken.third-party.site/path/to/%2E%2E/%2E%2E/resource",
                    expected = "https://broken.third-party.site/resource",
                ),
                TestCase(
                    input = "https://broken.third-party.site/path/to/%2F%2F%2F%2F%2F%2F%2F%2F%2F",
                    expected = "https://broken.third-party.site/path/to",
                ),
            )
        }
    }

    data class TestCase(
        val input: String,
        val expected: String,
    )
}
