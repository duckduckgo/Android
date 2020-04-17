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

package com.duckduckgo.app.fire

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.webkit.CookieManager
import android.widget.Toast
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


interface DuckDuckGoCookieManager {
    suspend fun removeExternalCookies()
    fun flush()
}

class CookiesHelper(context: Context) : SQLiteOpenHelper(WebViewContextWrapper(context), "Cookies", null, 1) {

    override fun onCreate(db: SQLiteDatabase?) {
        Timber.d("COOKIE: onCreate")
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        Timber.d("COOKIE: onUpgrade")
    }
}

class WebViewContextWrapper(context: Context) : ContextWrapper(context) {

    override fun getDatabasePath(name: String?): File {
        val dataDir = baseContext.applicationInfo.dataDir
        val file = File(dataDir, "app_webview/$name")
        return file
    }

    override fun openOrCreateDatabase(name: String?, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase {
        Timber.d("COOKIE: openOrCreateDatabase called for $name")
        return super.openOrCreateDatabase(name, mode, factory)
    }

    override fun openOrCreateDatabase(
        name: String?,
        mode: Int,
        factory: SQLiteDatabase.CursorFactory?,
        errorHandler: DatabaseErrorHandler?
    ): SQLiteDatabase {
        val result = SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), null)
        Timber.d("COOKIE: openOrCreateDatabase called for $name")
        return result
    }
}

class WebViewCookieManager(
    private val context: Context,
    private val bookmarks: BookmarksDao,
    private val cookieManager: CookieManager,
    private val host: String
) : DuckDuckGoCookieManager {
    override suspend fun removeExternalCookies() {

        //val allCookies = getAllCookies()

        if (cookieManager.hasCookies()) {
            val ddgCookies = getDuckDuckGoCookies()
            val excludedSites = withContext(Dispatchers.IO) {
                getHostsToPreserve()
            }
            removeCookies(excludedSites)
            storeDuckDuckGoCookies(ddgCookies)
        }

        withContext(Dispatchers.IO) {
            flush()
        }

        /*

        suspendCoroutine<Unit> { continuation ->
            cookieManager.removeAllCookies {
                Timber.v("All cookies removed; restoring ${ddgCookies.size} DDG cookies")
                continuation.resume(Unit)
            }
        }

        storeDuckDuckGoCookies(ddgCookies)

        allCookies.forEach { cookie ->
             suspendCoroutine<Unit> { continuation ->
                 cookieManager.setCookie(cookie.domain, cookie.toString()) { success ->
                     Timber.v("Cookie $cookie stored successfully: $success")
                     continuation.resume(Unit)
                 }
             }
         }*/

    }

    private fun getHostsToPreserve(): List<String> {
        val bookmarksList = bookmarks.bookmarksSync()
        return bookmarksList.flatMap { entity ->
            val acceptedHosts = mutableListOf<String>()
            val host = Uri.parse(entity.url).host
            host.split(".")
                .foldRight("", { next, acc ->
                    val next = ".$next$acc"
                    acceptedHosts.add(next)
                    next
                })
            acceptedHosts.add(host)
            acceptedHosts
        }
    }

    private fun removeCookies(excludedSites: List<String>): List<Cookie> {
        val allCookies = mutableListOf<Cookie>()
        val cookiesHelper = CookiesHelper(context)
        //val readableDatabase = cookiesHelper.readableDatabase
        val dataDir = context.applicationInfo.dataDir
        val knownLocations = listOf("app_webview/Default/Cookies", "app_webview/Cookies")
        val filePath: String = knownLocations.find { knownPath ->
            val file = File(dataDir, knownPath)
            file.exists()
        } ?: ""

        if (filePath.isNotEmpty()) {
            val file = File(dataDir, filePath)
            val readableDatabase = SQLiteDatabase.openDatabase(
                file.toString(),
                null,
                SQLiteDatabase.OPEN_READWRITE,
                DatabaseErrorHandler { Timber.d("COOKIE: onCorruption") })
            Timber.d("COOKIE: database version: ${readableDatabase.version}")
            val whereArg = excludedSites.foldIndexed("", { pos, acc, _ ->
                if (pos == 0) {
                    "host_key NOT LIKE ?"
                } else {
                    "$acc AND host_key NOT LIKE ?"
                }
            })
            val number = readableDatabase.delete("cookies", whereArg, excludedSites.toTypedArray())
            Toast.makeText(context, "$number cookies removed", Toast.LENGTH_LONG).show()

            readableDatabase.close()
            Timber.d("DONE")
            Toast.makeText(context, "All cookies removed", Toast.LENGTH_LONG).show()
        }

        return allCookies
    }

    private fun getAllCookies(): List<Cookie> {
        val allCookies = mutableListOf<Cookie>()
        val cookiesHelper = CookiesHelper(context)
        //val readableDatabase = cookiesHelper.readableDatabase
        var counter: Int = 0
        val dataDir = context.applicationInfo.dataDir
        val knownLocations = listOf("app_webview/Default/Cookies", "app_webview/Cookies")
        val filePath: String = knownLocations.find { knownPath ->
            val file = File(dataDir, knownPath)
            file.exists()
        } ?: ""

        if (filePath.isNotEmpty()) {
            val file = File(dataDir, filePath)
            val readableDatabase = SQLiteDatabase.openDatabase(
                file.toString(),
                null,
                SQLiteDatabase.OPEN_READONLY,
                DatabaseErrorHandler { Timber.d("COOKIE: onCorruption") })
            Timber.d("COOKIE: database version: ${readableDatabase.version}")
            val query = "SELECT * FROM cookies"
            val cursor = readableDatabase.rawQuery(query, null)
            if (cursor.moveToFirst()) {
                do {
                    var host: String = cursor.getString(cursor.getColumnIndex("host_key"))
                    val name: String = cursor.getString(cursor.getColumnIndex("name"))
                    val value: String = cursor.getString(cursor.getColumnIndex("value"))
                    val path: String = cursor.getString(cursor.getColumnIndex("path"))
                    val isSecure: Boolean = cursor.getInt(cursor.getColumnIndex("is_secure")).toBoolean()
                    val isHttpOnly: Boolean = cursor.getInt(cursor.getColumnIndex("is_httponly")).toBoolean()
                    //val firstPartyOnly: String = cursor.getString(cursor.getColumnIndex("firstPartyOnly"))
                    val cookieBuilder = okhttp3.Cookie.Builder().name(name).value(value).path(path)

                    if (isSecure) {
                        cookieBuilder.secure()
                    }

                    if (isHttpOnly) {
                        cookieBuilder.httpOnly()
                    }

                    if (host.startsWith(".")) {
                        val hostDropped = host.drop(1)
                        allCookies.add(Cookie(host, cookieBuilder.hostOnlyDomain(hostDropped).build()))
                    } else {
                        allCookies.add(Cookie(host, cookieBuilder.hostOnlyDomain(host).build()))
                    }
                    counter++
                    Timber.d("COOKIE: $name")
                } while (cursor.moveToNext())
            }
            readableDatabase.close()
            Timber.d("DONE")
            Toast.makeText(context, "$counter cookies removed", Toast.LENGTH_LONG).show()
        }

        return allCookies
    }

    private suspend fun storeDuckDuckGoCookies(cookies: List<String>) {
        cookies.forEach {
            val cookie = it.trim()
            Timber.d("Restoring DDB cookie: $cookie")
            storeCookie(cookie)
        }
    }

    private suspend fun storeCookie(cookie: String) {
        suspendCoroutine<Unit> { continuation ->
            cookieManager.setCookie(host, cookie) { success ->
                Timber.v("Cookie $cookie stored successfully: $success")
                continuation.resume(Unit)
            }
        }
    }

    private fun getDuckDuckGoCookies(): List<String> {
        return cookieManager.getCookie(host)?.split(";").orEmpty()
    }

    override fun flush() {
        cookieManager.flush()
    }
}

private fun Int.toBoolean(): Boolean {
    return this != 0
}

data class Cookie(val domain: String, private val cookie: okhttp3.Cookie) {
    override fun toString(): String {
        return cookie.toString() + "; SameSite=Lax"
    }
}