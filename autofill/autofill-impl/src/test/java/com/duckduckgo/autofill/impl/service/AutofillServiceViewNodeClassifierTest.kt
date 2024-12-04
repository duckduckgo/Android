/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autofill.impl.service

import android.util.Pair
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.autofill.impl.service.AutofillFieldType.PASSWORD
import com.duckduckgo.autofill.impl.service.AutofillFieldType.UNKNOWN
import com.duckduckgo.autofill.impl.service.AutofillFieldType.USERNAME
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutofillServiceViewNodeClassifierTest {

    val testee = AutofillServiceViewNodeClassifier()

    // autofillHints
    @Test
    fun whenAutofillHintsIsNullThenReturnUnknown() {
        val viewNode = viewNode().autofillHints(null)
        assertEquals(AutofillFieldType.UNKNOWN, testee.classify(viewNode))
    }

    // autofillHints containsAny(usernameHints)
    @Test
    fun whenAutofillHintsContainsUsernameViewHintsThenReturnUsername() {
        assertEquals(USERNAME, testee.classify(viewNode().autofillHints(arrayOf(View.AUTOFILL_HINT_EMAIL_ADDRESS))))
        assertEquals(USERNAME, testee.classify(viewNode().autofillHints(arrayOf(View.AUTOFILL_HINT_USERNAME))))
        assertEquals(
            USERNAME,
            testee.classify(
                viewNode().autofillHints(
                    // one autofill hint is username
                    arrayOf(View.AUTOFILL_HINT_CREDIT_CARD_NUMBER, View.AUTOFILL_HINT_USERNAME),
                ),
            ),
        )

        assertEquals(UNKNOWN, testee.classify(viewNode().autofillHints(arrayOf(View.AUTOFILL_HINT_PHONE))))
        assertEquals(UNKNOWN, testee.classify(viewNode().autofillHints(arrayOf(View.AUTOFILL_HINT_CREDIT_CARD_NUMBER))))
        assertEquals(UNKNOWN, testee.classify(viewNode().autofillHints(arrayOf(""))))
    }

    // autofillHints containsAny(passwordHints)
    @Test
    fun whenAutofillHintsContainsPasswordViewHintsThenReturnPassword() {
        assertEquals(PASSWORD, testee.classify(viewNode().autofillHints(arrayOf(View.AUTOFILL_HINT_PASSWORD))))
        assertEquals(PASSWORD, testee.classify(viewNode().autofillHints(arrayOf("passwordAuto"))))
        assertEquals(
            PASSWORD,
            testee.classify(
                viewNode().autofillHints(
                    // one autofill hint is password
                    arrayOf(View.AUTOFILL_HINT_CREDIT_CARD_NUMBER, View.AUTOFILL_HINT_PASSWORD),
                ),
            ),
        )

        assertEquals(UNKNOWN, testee.classify(viewNode().autofillHints(arrayOf(View.AUTOFILL_HINT_PHONE))))
        assertEquals(UNKNOWN, testee.classify(viewNode().autofillHints(arrayOf(View.AUTOFILL_HINT_CREDIT_CARD_NUMBER))))
        assertEquals(UNKNOWN, testee.classify(viewNode().autofillHints(arrayOf(""))))
    }

    // idEntry or hint containsAny(usernameKeywords)
    @Test
    fun whenNodeHintContainsUsernameKeywordsThenReturnUsername() {
        validUsernameCombinations.forEach { usernameCombination ->
            assertEquals(
                "$usernameCombination failed",
                USERNAME,
                testee.classify(viewNode().hint(usernameCombination)),
            )
        }

        assertEquals(UNKNOWN, testee.classify(viewNode().hint("userna"))) // incomplete keyword
        assertEquals(UNKNOWN, testee.classify(viewNode().hint("")))
        assertEquals(UNKNOWN, testee.classify(viewNode().hint("randomHint")))
    }

    @Test
    fun whenNodeIdEntryContainsUsernameKeywordsThenReturnUsername() {
        validUsernameCombinations.forEach { usernameCombination ->
            assertEquals(
                "$usernameCombination failed",
                USERNAME,
                testee.classify(viewNode().idEntry(usernameCombination)),
            )
        }

        assertEquals(UNKNOWN, testee.classify(viewNode().idEntry("userna"))) // incomplete keyword
        assertEquals(UNKNOWN, testee.classify(viewNode().idEntry("")))
        assertEquals(UNKNOWN, testee.classify(viewNode().idEntry("randomHint")))
    }

    // idEntry or hint containsAny(passwordKeywords)
    @Test
    fun whenNodeHintContainsPasswordKeywordsThenReturnPassword() {
        validPasswordCombinations.forEach { passwordCombination ->
            assertEquals(
                "$passwordCombination failed",
                PASSWORD,
                testee.classify(viewNode().hint(passwordCombination)),
            )
        }

        assertEquals(UNKNOWN, testee.classify(viewNode().hint("pass"))) // incomplete keyword
        assertEquals(UNKNOWN, testee.classify(viewNode().hint("")))
        assertEquals(UNKNOWN, testee.classify(viewNode().hint("something")))
        assertEquals(UNKNOWN, testee.classify(viewNode().hint("Passw0rd")))
    }

    @Test
    fun whenNodeIdEntryContainsPasswordKeywordsThenReturnPassword() {
        validPasswordCombinations.forEach { passwordCombination ->
            assertEquals(
                "$passwordCombination failed",
                PASSWORD,
                testee.classify(viewNode().idEntry(passwordCombination)),
            )
        }

        assertEquals(UNKNOWN, testee.classify(viewNode().idEntry("pass"))) // incomplete keyword
        assertEquals(UNKNOWN, testee.classify(viewNode().idEntry("")))
        assertEquals(UNKNOWN, testee.classify(viewNode().idEntry("something")))
        assertEquals(UNKNOWN, testee.classify(viewNode().idEntry("Passw0rd")))
    }

    // htmlInfo.attributes contains usernameKeywords (in type or autofill attribute)
    @Test
    fun whenNodeHtmlAttributeTypeContainsUsernameKeywordsThenReturnUsername() {
        // username hints
        validUsernameCombinations.forEach { usernameCombination ->
            assertEquals(
                "$usernameCombination failed",
                USERNAME,
                testee.classify(viewNode().htmlAttributes(listOf(Pair("type", usernameCombination)))),
            )
        }

        // non username hints
        assertEquals(UNKNOWN, testee.classify(viewNode().htmlAttributes(listOf(Pair("type", "userna"))))) // incomplete keyword
        assertEquals(UNKNOWN, testee.classify(viewNode().htmlAttributes(listOf(Pair("type", "")))))
        assertEquals(UNKNOWN, testee.classify(viewNode().htmlAttributes(listOf(Pair("type", "randomHint")))))
    }

    @Test
    fun whenNodeHtmlAttributeIsAutofillAndValueContainsUsernameKeywordsThenReturnUsername() {
        // username hints
        autofillAttributeKeyCombination.forEach { autofillKey ->
            validUsernameCombinations.forEach { usernameCombination ->
                assertEquals(
                    "$autofillKey - $usernameCombination failed",
                    USERNAME,
                    testee.classify(viewNode().htmlAttributes(listOf(Pair(autofillKey, usernameCombination)))),
                )
            }
        }

        // if one matches is enough
        assertEquals(
            USERNAME,
            testee.classify(
                viewNode().htmlAttributes(
                    listOf(
                        Pair("randomKey", "randomValue"),
                        Pair(autofillAttributeKeyCombination.random(), validUsernameCombinations.random()),
                        Pair("anotherRandom", "anotherValue"),
                    ),
                ),
            ),
        )

        // non username hints
        assertEquals(
            UNKNOWN,
            testee.classify(viewNode().htmlAttributes(listOf(Pair(autofillAttributeKeyCombination.random(), "userna")))),
        ) // incomplete keyword
        assertEquals(UNKNOWN, testee.classify(viewNode().htmlAttributes(listOf(Pair(autofillAttributeKeyCombination.random(), "")))))
        assertEquals(UNKNOWN, testee.classify(viewNode().htmlAttributes(listOf(Pair(autofillAttributeKeyCombination.random(), "randomHint")))))
    }

    // htmlInfo.attributes contains passwordKeywords (in type or autofill attribute)
    @Test
    fun whenNodeHtmlAttributeTypeContainsPasswordKeywordsThenReturnPassword() {
        // username hints
        validPasswordCombinations.forEach { usernameCombination ->
            assertEquals(
                "$usernameCombination failed",
                PASSWORD,
                testee.classify(viewNode().htmlAttributes(listOf(Pair("type", usernameCombination)))),
            )
        }

        // non username hints
        assertEquals(UNKNOWN, testee.classify(viewNode().htmlAttributes(listOf(Pair("type", "userna"))))) // incomplete keyword
        assertEquals(UNKNOWN, testee.classify(viewNode().htmlAttributes(listOf(Pair("type", "")))))
        assertEquals(UNKNOWN, testee.classify(viewNode().htmlAttributes(listOf(Pair("type", "randomHint")))))
    }

    @Test
    fun whenNodeHtmlAttributeIsAutofillAndValueContainsPasswordKeywordsThenReturnPassword() {
        // username hints
        autofillAttributeKeyCombination.forEach { autofillKey ->
            validPasswordCombinations.forEach { usernameCombination ->
                assertEquals(
                    "$autofillKey - $usernameCombination failed",
                    PASSWORD,
                    testee.classify(viewNode().htmlAttributes(listOf(Pair(autofillKey, usernameCombination)))),
                )
            }
        }

        // if one matches is enough
        assertEquals(
            PASSWORD,
            testee.classify(
                viewNode().htmlAttributes(
                    listOf(
                        Pair("randomKey", "randomValue"),
                        Pair(autofillAttributeKeyCombination.random(), validPasswordCombinations.random()),
                        Pair("anotherRandom", "anotherValue"),
                    ),
                ),
            ),
        )

        // non username hints
        assertEquals(UNKNOWN, testee.classify(viewNode().htmlAttributes(listOf(Pair(autofillAttributeKeyCombination.random(), "userna")))))
        assertEquals(UNKNOWN, testee.classify(viewNode().htmlAttributes(listOf(Pair(autofillAttributeKeyCombination.random(), "")))))
        assertEquals(UNKNOWN, testee.classify(viewNode().htmlAttributes(listOf(Pair(autofillAttributeKeyCombination.random(), "randomHint")))))
    }

    private val validUsernameCombinations = listOf(
        "username",
        "aKeywordWithUsernameInside",
        "USERNAME",
        "a-Username_1",
        "user NAME",
        "your email",
        "user name field",
        "account_name",
    )

    private val validPasswordCombinations = listOf(
        "password",
        "PASSWORD",
        "a-Password_1",
        "PassworD",
    )

    private val autofillAttributeKeyCombination = listOf(
        "autofill",
        "ua-autofill-hint",
        "autofill_car",
    )
}
