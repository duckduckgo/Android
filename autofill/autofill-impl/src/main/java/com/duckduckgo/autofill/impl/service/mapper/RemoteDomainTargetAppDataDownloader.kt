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

package com.duckduckgo.autofill.impl.service.mapper

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.autofill.store.AutofillPrefsStore
import com.duckduckgo.autofill.store.targets.DomainTargetAppDao
import com.duckduckgo.autofill.store.targets.DomainTargetAppEntity
import com.duckduckgo.autofill.store.targets.TargetApp
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
class RemoteDomainTargetAppDataDownloader @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val remoteDomainTargetAppService: RemoteDomainTargetAppService,
    private val autofillPrefsStore: AutofillPrefsStore,
    private val domainTargetAppDao: DomainTargetAppDao,
) : MainProcessLifecycleObserver {
    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        // TODO: Add check for killswitch
        appCoroutineScope.launch(dispatcherProvider.io()) {
            Timber.d("Autofill-mapping: Attempting to download")
            download()
        }
    }

    private suspend fun download() {
        runCatching {
            remoteDomainTargetAppService.fetchDataset().run {
                Timber.d("Autofill-mapping: Downloaded targets dataset version: ${this.version}")
                Timber.d("Autofill-mapping: Downloaded targets dataset $this")
                if (autofillPrefsStore.domainTargetDatasetVersion != this.version) {
                    persistData(this)
                    autofillPrefsStore.domainTargetDatasetVersion = this.version
                }
            }
        }.onFailure {
            Timber.e(it, "Autofill-mapping: Dataset download failed")
        }
    }

    private fun persistData(dataset: RemoteDomainTargetDataSet) {
        Timber.d("Autofill-mapping: Persisting targets dataset")
        val toPersist = mutableListOf<DomainTargetAppEntity>()
        dataset.targets.forEach { target ->
            target.apps.forEach { app ->
                app.sha256_cert_fingerprints.forEach {
                    toPersist.add(
                        DomainTargetAppEntity(
                            domain = target.url,
                            targetApp = TargetApp(
                                packageName = app.package_name,
                                sha256CertFingerprints = it,
                            ),
                            dataExpiryInMillis = 0L,
                        ),
                    )
                }
            }
        }
        Timber.d("Autofill-mapping: Attempting to persist ${toPersist.size} entries")
        domainTargetAppDao.updateRemote(toPersist)
        Timber.d("Autofill-mapping: Persist complete")
    }
}
