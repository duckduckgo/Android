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

package com.duckduckgo.app.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BackupRulesTest {

    private val modernRules = parse("backup_rules_android_12_upwards.xml")
    private val legacyRules = parse("backup_rules_android_11_and_older.xml")

    private val deviceTransferIncludes = modernRules.section("device-transfer").rules("include")

    @Test
    fun cloudBackupExcludesEveryDomain() {
        val cloudBackup = modernRules.section("cloud-backup")

        assertEquals(CLOUD_RESTORE_WOULD_LOOK_LIKE_A_TRANSFER, ALL_DOMAINS.map { it to "." }.toSet(), cloudBackup.rules("exclude").toSet())
        assertTrue(CLOUD_RESTORE_WOULD_LOOK_LIKE_A_TRANSFER, cloudBackup.getElementsByTagName("include").length == 0)
    }

    @Test
    fun legacyRulesOnlyIncludeForDeviceTransfer() {
        val includes = legacyRules.documentElement.getElementsByTagName("include")

        assertTrue(includes.length > 0)
        for (i in 0 until includes.length) {
            assertEquals(
                CLOUD_RESTORE_WOULD_LOOK_LIKE_A_TRANSFER,
                "deviceToDeviceTransfer",
                (includes.item(i) as Element).getAttribute("requireFlags"),
            )
        }
    }

    @Test
    fun deviceTransferIsAnInclusionList() {
        assertTrue(deviceTransferIncludes.isNotEmpty())
        assertTrue(modernRules.section("device-transfer").rules("exclude").isEmpty())
        assertTrue(legacyRules.documentElement.rules("exclude").isEmpty())
    }

    @Test
    fun noWholeDomainIsIncluded() {
        val wholeDomains = deviceTransferIncludes.filter { (_, path) -> path == "." || path.isEmpty() }

        assertTrue("whole domains included: $wholeDomains", wholeDomains.isEmpty())
    }

    @Test
    fun inclusionListsMatch() {
        assertEquals(deviceTransferIncludes.toSet(), legacyRules.documentElement.rules("include").toSet())
    }

    @Test
    fun transferredMarkerIsIncluded() {
        assertTrue("sharedpref" to "com.duckduckgo.devicemigration.transferred.xml" in deviceTransferIncludes)
    }

    @Test
    fun atbStoreIsIncluded() {
        // Device migration detection falls back to a transferred ATB when the old phone never wrote the migration marker.
        assertTrue("sharedpref" to "com.duckduckgo.app.statistics.xml" in deviceTransferIncludes)
    }

    @Test
    fun vpnUserChoicesAreIncluded() {
        val vpnUserChoices = setOf(
            "database" to "vpn.db",
            "file" to "harmony_prefs/com.duckduckgo.networkprotection.store.prefs.v1",
        )

        assertTrue("missing: ${vpnUserChoices - deviceTransferIncludes.toSet()}", deviceTransferIncludes.containsAll(vpnUserChoices))
    }

    @Test
    fun nonTransferableStoresAreNotIncluded() {
        val included = NON_TRANSFERABLE.filter { store -> deviceTransferIncludes.any { it.covers(store) } }

        assertTrue("included: $included", included.isEmpty())
    }

    private fun Pair<String, String>.covers(store: Pair<String, String>): Boolean =
        first == store.first && (second == store.second || store.second.startsWith("$second/"))

    private fun parse(name: String): org.w3c.dom.Document =
        DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/xml/$name"))

    private fun org.w3c.dom.Document.section(tag: String): Element = documentElement.getElementsByTagName(tag).item(0) as Element

    private fun Element.rules(tag: String): List<Pair<String, String>> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map {
            val element = nodes.item(it) as Element
            element.getAttribute("domain") to element.getAttribute("path")
        }
    }

    private companion object {
        // The new phone cannot tell a cloud restore from a device transfer: DeviceMigrationBackupAgent marks any restore
        // that wrote files, and detection also reads an ATB on a fresh package as transferred data.
        const val CLOUD_RESTORE_WOULD_LOOK_LIKE_A_TRANSFER =
            "Cloud backup must carry no data until device migration detection can tell a cloud restore from a device transfer"

        val ALL_DOMAINS = listOf("file", "database", "sharedpref", "external", "root")

        // Keystore-encrypted stores cannot be decrypted on another phone; the rest are per-device, per-install or telemetry state.
        val NON_TRANSFERABLE =
            listOf(
                "sharedpref" to "com.duckduckgo.devicemigration.local.xml",
                "sharedpref" to "com.duckduckgo.app.email.settings.xml",
                "sharedpref" to "com.duckduckgo.app.email.settings.encryptiontest.xml",
                "sharedpref" to "com.duckduckgo.sync.store.v1.xml",
                "sharedpref" to "com.duckduckgo.securestorage.store.xml",
                "sharedpref" to "com.duckduckgo.autofill.store.autofill_store.xml",
                "sharedpref" to "com.duckduckgo.app.fire.unsentpixels.settings.xml",
                "file" to "harmony_prefs/com.duckduckgo.subscriptions.store",
                "file" to "harmony_prefs/com.duckduckgo.pir.impl.store.secure",
                "file" to "harmony_prefs/com.duckduckgo.securestorage.store_v2",
                "file" to "harmony_prefs/com.duckduckgo.securestorage.store_v3",
                "file" to "harmony_prefs/com.duckduckgo.vpn.tunnel.config.v1",
                "file" to "fire_mode_duck_ai_files",
                "database" to "secure_storage_database_encrypted.db",
                "database" to "pir_encrypted.db",
                "database" to "pir_decrypted.db",
                "database" to "fire_mode.db",
                "database" to "fire_mode_duck_ai.db",
                "database" to "sync.db",
                "database" to "pixels.db",
                "database" to "wide_events.db",
                "database" to "crash_database.db",
                "database" to "anr_database.db",
                "root" to "app_webview/Profiles/Fire",
            )
    }
}
