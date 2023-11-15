package com.duckduckgo.autofill.sharedcreds

import com.duckduckgo.autofill.impl.sharedcreds.AppleSharedCredentialsParser
import com.duckduckgo.autofill.impl.sharedcreds.SharedCredentialJsonReader
import com.duckduckgo.autofill.impl.sharedcreds.SharedCredentialsParser.SharedCredentialConfig
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.common.test.CoroutineTestRule
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
class AppleSharedCredentialsParserTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val moshi = Moshi.Builder().build()
    private val jsonReader: SharedCredentialJsonReader = mock()
    private val autofillUrlMatcher: AutofillUrlMatcher = mock()

    private val testee = AppleSharedCredentialsParser(
        moshi = moshi,
        jsonReader = jsonReader,
        dispatchers = coroutineTestRule.testDispatcherProvider,
        autofillUrlMatcher = autofillUrlMatcher,
    )

    @Test
    fun whenJsonNotReadThenRulesAreEmpty() = runTest {
        whenever(jsonReader.read()).thenReturn(null)
        val rules = testee.read()
        rules.assertRulesAreEmpty()
    }

    @Test
    fun whenJsonIsCorruptNotReadThenRulesAreEmpty() = runTest {
        whenever(jsonReader.read()).thenReturn("not valid json")
        val rules = testee.read()
        rules.assertRulesAreEmpty()
    }
}

private fun SharedCredentialConfig.assertRulesAreEmpty() {
    assertTrue(omnidirectionalRules.isEmpty())
    assertTrue(unidirectionalRules.isEmpty())
}
