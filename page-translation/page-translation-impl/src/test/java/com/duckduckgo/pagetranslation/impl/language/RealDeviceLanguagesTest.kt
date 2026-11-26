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

package com.duckduckgo.pagetranslation.impl.language

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "fr-rFR", sdk = [30])
class RealDeviceLanguagesTest {

    private val originalLocale = Locale.getDefault()
    private val testee = RealDeviceLanguages(ApplicationProvider.getApplicationContext())

    @Before
    fun setUp() {
        Locale.setDefault(Locale.ENGLISH)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
    }

    @Test
    fun whenNoAppLocaleThenReturnsSystemLocales() {
        assertEquals(listOf("fr-FR"), testee.get())
    }

    @Test
    fun whenAppLocaleSetThenAppLocaleComesFirstFollowedBySystemLocales() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("de-DE"))

        assertEquals(listOf("de-DE", "fr-FR"), testee.get())
    }

    @Test
    fun whenAppLocaleMatchesSystemLocaleThenItIsNotDuplicated() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("fr-FR"))

        assertEquals(listOf("fr-FR"), testee.get())
    }

    @Test
    fun whenLocaleIsEnglishThenNamesAreEnglishAndCapitalised() {
        assertEquals("Spanish", testee.displayName("es"))
        assertEquals("English", testee.displayName("en"))
    }

    @Test
    fun whenLocaleIsFrenchThenNamesAreCapitalisedInFrench() {
        Locale.setDefault(Locale.FRENCH)

        assertEquals("Espagnol", testee.displayName("es"))
    }

    @Test
    fun whenCodeIsUnknownThenCodeIsReturned() {
        assertEquals("zz", testee.displayName("zz").lowercase())
    }
}
