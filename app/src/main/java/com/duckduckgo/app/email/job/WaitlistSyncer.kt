/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.email.job

// interface WaitlistSyncer {
//    suspend fun syncEmailWaitlist(): WaitlistStatusResponse
// }
//
// class WaitlistDownloader(
//    private val emailService: EmailService,
//    private val emailDataStore: EmailDataStore
// ) : WaitlistSyncer {
//
//    override suspend fun syncEmailWaitlist(): WaitlistStatusResponse {
//        val test = withContext(Dispatchers.IO) {
//            runCatching {
//                emailService.waitlistStatus()
//            }.onSuccess {
//                if (emailDataStore.waitlistTimestamp > it.timestamp) {
//                    runCatching {
//                        emailService.getCode(emailDataStore.waitlistToken!!)
//                    }.onSuccess { codeResponse ->
//                        emailDataStore.inviteCode = codeResponse.code
//                    }
//                }
//                return@withContext ListenableWorker.Result.success()
//            }.onFailure {
//                return@withContext ListenableWorker.Result.failure()
//            }
//        }
//    }
// }
