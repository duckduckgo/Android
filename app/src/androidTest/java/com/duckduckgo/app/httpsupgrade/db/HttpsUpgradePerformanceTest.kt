/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.httpsupgrade.db

/*
 * Copyright (c) 2018 DuckDuckGo
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

import android.arch.persistence.room.Room
import android.net.Uri
import android.support.test.InstrumentationRegistry
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.httpsupgrade.HttpsUpgrader
import com.duckduckgo.app.httpsupgrade.HttpsUpgraderImpl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import timber.log.Timber

@Ignore("Can run slow due to amount of data")
class HttpsUpgraderPerformanceTest {

    lateinit var dao: HttpsUpgradeDomainDao
    lateinit var httpsUpgrader: HttpsUpgrader
    lateinit var db: AppDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getContext(), AppDatabase::class.java).build()
        dao = db.httpsUpgradeDomainDao()
        httpsUpgrader = HttpsUpgraderImpl(dao)
    }

    @Test
    fun test2800() {
        val testDomains = ingest(2800)
        assertEquals(2800, testDomains.size)
        checkPerformance(testDomains)
    }

    @Test
    fun test200000() {
        val testDomains = ingest(200000)
        assertEquals(200000, testDomains.size)
        checkPerformance(testDomains)
    }

    @Test
    fun test500000() {
        val testDomains = ingest(500000)
        assertEquals(500000, testDomains.size)
        checkPerformance(testDomains)
    }

    private fun checkPerformance(testDomains: Array<String>) {
        var start = System.currentTimeMillis()
        var index = 0
        for (domain in testDomains) {
            assertFalse(httpsUpgrader.shouldUpgrade(Uri.parse("http://$domain")))

            if ((index % 100) ==  0) {
                val diff = System.currentTimeMillis() - start
                Timber.i("$diff $domain")
                start = System.currentTimeMillis()
            }
            index++
        }

        val diff = System.currentTimeMillis() - start
        Timber.i("$index $diff")
    }

    private fun ingest(size: Int): Array<String> {
        val start = System.currentTimeMillis()
        for (i in 0 .. size) {
            dao.insertAll(HttpsUpgradeDomain("domain$i.com"))
        }

        val testDomains = Array<String>(size, {
            "testdomain$it.com"
        })

        val diff = System.currentTimeMillis() - start
        Timber.i("ingest finished $diff")
        return testDomains
    }

}
