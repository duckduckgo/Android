/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.fire

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteDao
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelName.FORGET_ALL_PRESSED_BROWSING
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter.CLEAR_PERSONAL_DATA_FIREPROOF_WEBSITES
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class ForgetAllFireproofPixelSenderTest {
    private lateinit var forgetAllFireproofPixelSender: ForgetAllFireproofPixelSender
    private lateinit var database: AppDatabase
    private lateinit var fireproofWebsiteDao: FireproofWebsiteDao
    private val pixel: Pixel = mock()

    @Before
    fun before() {
        database = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fireproofWebsiteDao = database.fireproofWebsiteDao()

        forgetAllFireproofPixelSender = ForgetAllFireproofPixelSender(pixel, fireproofWebsiteDao)
    }

    @After
    fun after() {
        database.close()
    }

    @Test
    fun whenForgetAllPressedThenPixelSentUsingPixelName() = runBlocking {
        forgetAllFireproofPixelSender.forgetAllPressed(FORGET_ALL_PRESSED_BROWSING)

        verify(pixel).fire(
            pixel = eq(FORGET_ALL_PRESSED_BROWSING),
            parameters = any(),
            encodedParameters = any()
        )
    }

    @Test
    fun whenUserHasFireproofWebsitesThenFireproofParamTrue() = runBlocking {
        fireproofWebsiteDao.insert(FireproofWebsiteEntity("example.com"))

        forgetAllFireproofPixelSender.forgetAllPressed(FORGET_ALL_PRESSED_BROWSING)

        verify(pixel).fire(
            pixel = eq(FORGET_ALL_PRESSED_BROWSING),
            parameters = eq(mapOf(CLEAR_PERSONAL_DATA_FIREPROOF_WEBSITES to "true")),
            encodedParameters = any()
        )
    }

    @Test
    fun whenUserDoesNotHaveFireproofWebsitesThenFireproofParamFalse() = runBlocking {
        forgetAllFireproofPixelSender.forgetAllPressed(FORGET_ALL_PRESSED_BROWSING)

        verify(pixel).fire(
            pixel = eq(FORGET_ALL_PRESSED_BROWSING),
            parameters = eq(mapOf(CLEAR_PERSONAL_DATA_FIREPROOF_WEBSITES to "false")),
            encodedParameters = any()
        )
    }
}