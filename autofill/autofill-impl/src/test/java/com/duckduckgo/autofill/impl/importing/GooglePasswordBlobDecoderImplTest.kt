package com.duckduckgo.autofill.impl.importing

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GooglePasswordBlobDecoderImplTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()
    private val testee = GooglePasswordBlobDecoderImpl(dispatchers = coroutineTestRule.testDispatcherProvider)

    @Test
    fun whenEmptyCsvDataThenNoCredentialsReturned() = runTest {
        val input = "${DATA_TYPE_PREFIX}bmFtZSx1cmwsdXNlcm5hbWUscGFzc3dvcmQsbm90ZQ=="
        val expected = "name,url,username,password,note"
        assertEquals(expected, testee.decode(input))
    }

    @Test
    fun whenValidBlobMultiplePasswordsThenCredentialsReturned() = runTest {
        val input = DATA_TYPE_PREFIX +
            "bmFtZSx1cmwsdXNlcm5hbWUscGFzc3dvcmQsbm90ZQosaHR0cHM6Ly9leGFtcGxlLmNvbSx0ZXN0LXVzZXIsdGVzdC1wYXNzd29yZCx" +
            "0ZXN0LW5vdGVzCmZpbGwuZGV2LGh0dHBzOi8vZmlsbC5kZXYvZm9ybS9sb2dpbi1zaW1wbGUsdGVzdC11c2VyLHRlc3RQYXNzd29yZEZpbGxEZXYs"
                .trimIndent()
        val expected = """
            name,url,username,password,note
            ,https://example.com,test-user,test-password,test-notes
            fill.dev,https://fill.dev/form/login-simple,test-user,testPasswordFillDev,
        """.trimIndent()
        assertEquals(expected, testee.decode(input))
    }

    @Test(expected = IllegalArgumentException::class)
    fun whenMissingDataTypeThenExceptionThrown() = runTest {
        testee.decode("bmFtZSx1cmwsdXNlcm5hbW")
    }

    @Test(expected = IllegalArgumentException::class)
    fun whenEmptyInputThenExceptionThrow() = runTest {
        testee.decode("")
    }

    companion object {
        private const val DATA_TYPE_PREFIX = "data:text/csv;charset=utf-8;;base64,"
    }
}
