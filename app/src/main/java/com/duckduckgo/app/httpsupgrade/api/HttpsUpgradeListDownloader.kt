/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.httpsupgrade.api

import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.httpsupgrade.db.HttpsUpgradeDomainDao
import io.reactivex.Completable
import timber.log.Timber
import javax.inject.Inject


class HttpsUpgradeListDownloader @Inject constructor(
        private val service: HttpsUpgradeListService,
        private val database: AppDatabase,
        private val httpsUpgradeDao: HttpsUpgradeDomainDao) {

    fun downloadList(): Completable {
        Timber.i("Downloading HTTPS Upgrade data")

        return Completable.fromAction({
            val call = service.https()
            val response = call.execute()
            if (response.isSuccessful) {
                Timber.d("Got HTTPS upgrade list from server")

                val domains = response.body()!!.simpleUpgrade.top500.toTypedArray()
                database.runInTransaction({
                    httpsUpgradeDao.deleteAll()
                    httpsUpgradeDao.insertAll(*domains)
                })
            }
        })
    }
}