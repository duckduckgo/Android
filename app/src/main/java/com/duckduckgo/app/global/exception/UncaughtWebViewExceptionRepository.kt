/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.global.exception

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber


class UncaughtWebViewExceptionRepository(private val uncaughtWebViewExceptionDao: UncaughtWebViewExceptionDao) {

    suspend fun uncaughtExceptionWhileInterceptingRequest(e: Throwable, exceptionSource: UncaughtWebViewExceptionSource) {
        Timber.e(e, "Uncaught exception while intercepting request")
        withContext(Dispatchers.IO) {
            val anonymisedExceptionCause = extractExceptionCause(e)
            val exceptionEntity = UncaughtWebViewExceptionEntity(message = anonymisedExceptionCause, exceptionSource = exceptionSource)
            uncaughtWebViewExceptionDao.add(exceptionEntity)
        }
    }

    suspend fun getExceptions(): List<UncaughtWebViewExceptionEntity> {
        return withContext(Dispatchers.IO) {
            uncaughtWebViewExceptionDao.all()
        }
    }

    private fun extractExceptionCause(e: Throwable): String {
        return "${e.javaClass.name} - ${e.stackTrace?.first()}"
    }

    suspend fun deleteException(id: Long) {
        withContext(Dispatchers.IO) {
            uncaughtWebViewExceptionDao.delete(id)
        }
    }
}