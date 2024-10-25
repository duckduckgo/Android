/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.store

import androidx.annotation.WorkerThread
import com.duckduckgo.networkprotection.store.db.NetPExclusionListDao
import com.duckduckgo.networkprotection.store.db.NetPManuallyExcludedApp
import kotlinx.coroutines.flow.Flow

@WorkerThread
interface NetPManualExclusionListRepository {
    fun getManualAppExclusionList(): List<NetPManuallyExcludedApp>

    fun getManualAppExclusionListFlow(): Flow<List<NetPManuallyExcludedApp>>

    fun manuallyExcludeApp(packageName: String)

    fun manuallyExcludeApps(packageNames: List<String>)

    fun manuallyEnableApp(packageName: String)

    fun manuallyEnableApps(packageNames: List<String>)

    fun restoreDefaultProtectedList()
}

class RealNetPManualExclusionListRepository constructor(
    private val exclusionListDao: NetPExclusionListDao,
) : NetPManualExclusionListRepository {
    override fun getManualAppExclusionList(): List<NetPManuallyExcludedApp> = exclusionListDao.getManualAppExclusionList()

    override fun getManualAppExclusionListFlow(): Flow<List<NetPManuallyExcludedApp>> = exclusionListDao.getManualAppExclusionListFlow()

    override fun manuallyExcludeApp(packageName: String) {
        exclusionListDao.insertIntoManualAppExclusionList(NetPManuallyExcludedApp(packageId = packageName, isProtected = false))
    }

    override fun manuallyExcludeApps(packageNames: List<String>) {
        packageNames.map {
            NetPManuallyExcludedApp(packageId = it, isProtected = false)
        }.also {
            exclusionListDao.insertIntoManualAppExclusionList(it)
        }
    }

    override fun manuallyEnableApp(packageName: String) {
        exclusionListDao.insertIntoManualAppExclusionList(NetPManuallyExcludedApp(packageId = packageName, isProtected = true))
    }

    override fun manuallyEnableApps(packageNames: List<String>) {
        packageNames.map {
            NetPManuallyExcludedApp(packageId = it, isProtected = true)
        }.also {
            exclusionListDao.insertIntoManualAppExclusionList(it)
        }
    }

    override fun restoreDefaultProtectedList() {
        exclusionListDao.deleteManualAppExclusionList()
    }
}
