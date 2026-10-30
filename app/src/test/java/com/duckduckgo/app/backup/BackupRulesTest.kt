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

    @Test
    fun cloudBackupExcludesEveryDomain() {
        val cloudBackup = modernRules.section("cloud-backup")

        assertEquals(ALL_DOMAINS.map { it to "." }.toSet(), cloudBackup.rules("exclude").toSet())
        assertTrue(cloudBackup.getElementsByTagName("include").length == 0)
    }

    @Test
    fun legacyRulesOnlyIncludeForDeviceTransfer() {
        val includes = legacyRules.documentElement.getElementsByTagName("include")

        assertTrue(includes.length > 0)
        for (i in 0 until includes.length) {
            assertEquals("deviceToDeviceTransfer", (includes.item(i) as Element).getAttribute("requireFlags"))
        }
    }

    @Test
    fun deviceTransferIncludesUnencryptedDomains() {
        val expected = TRANSFERRED_DOMAINS.map { it to "." }.toSet()

        assertEquals(expected, modernRules.section("device-transfer").rules("include").toSet())
        assertEquals(expected, legacyRules.documentElement.rules("include").toSet())
    }

    @Test
    fun exclusionListsMatch() {
        val deviceTransferExcludes = modernRules.section("device-transfer").rules("exclude").toSet()
        val legacyExcludes = legacyRules.documentElement.rules("exclude").toSet()

        assertEquals(deviceTransferExcludes, legacyExcludes)
    }

    @Test
    fun localMigrationMarkerIsExcluded() {
        val marker = "sharedpref" to "com.duckduckgo.devicemigration.local.xml"

        assertTrue(modernRules.section("device-transfer").rules("exclude").contains(marker))
        assertTrue(legacyRules.documentElement.rules("exclude").contains(marker))
    }

    @Test
    fun transferredMarkerIsNotExcluded() {
        val excludes = modernRules.section("device-transfer").rules("exclude")

        assertTrue(excludes.none { it.second.startsWith("com.duckduckgo.devicemigration.transferred") })
    }

    @Test
    fun atbStoreIsTransferred() {
        // Device migration detection falls back to a transferred ATB when the old phone never wrote the migration marker.
        val atbStore = "sharedpref" to "com.duckduckgo.app.statistics.xml"

        assertTrue(atbStore !in modernRules.section("device-transfer").rules("exclude"))
        assertTrue(atbStore !in legacyRules.documentElement.rules("exclude"))
    }

    @Test
    fun encryptedStoresAreExcluded() {
        val excludes = modernRules.section("device-transfer").rules("exclude").toSet()

        val expected = ENCRYPTED_AND_NON_TRANSFERABLE.toSet()
        assertTrue("missing: ${expected - excludes}", excludes.containsAll(expected))
    }

    @Test
    fun vpnUserChoicesAreTransferred() {
        val excludes = modernRules.section("device-transfer").rules("exclude").toSet()

        val vpnUserChoices = setOf(
            "database" to "vpn.db",
            "file" to "harmony_prefs/com.duckduckgo.networkprotection.store.prefs.v1",
        )
        assertTrue("excluded: ${vpnUserChoices intersect excludes}", (vpnUserChoices intersect excludes).isEmpty())
    }

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
        val ALL_DOMAINS = listOf("file", "database", "sharedpref", "external", "root")
        val TRANSFERRED_DOMAINS = listOf("file", "database", "sharedpref", "root")

        val ENCRYPTED_AND_NON_TRANSFERABLE =
            listOf(
                "sharedpref" to "com.duckduckgo.devicemigration.local.xml",
                "sharedpref" to "com.duckduckgo.app.devicemigration.cleanup.xml",
                "sharedpref" to "com.duckduckgo.subscriptions.devicemigration.xml",
                "sharedpref" to "com.duckduckgo.sync.devicemigration.xml",
                "sharedpref" to "com.duckduckgo.mobile.android.vpn.devicemigration.cleanup.xml",
                "sharedpref" to "com.duckduckgo.networkprotection.devicemigration.cleanup.xml",
                "sharedpref" to "com.duckduckgo.app.email.settings.xml",
                "sharedpref" to "com.duckduckgo.app.email.settings.encryptiontest.xml",
                "sharedpref" to "com.duckduckgo.sync.store.v1.xml",
                "sharedpref" to "com.duckduckgo.securestorage.store.xml",
                "sharedpref" to "com.duckduckgo.app.fire.unsentpixels.settings.xml",
                "sharedpref" to "com.duckduckgo.feature.toggles.pixels.prefs.xml",
                "sharedpref" to "com.duckduckgo.adclick.impl.pixels.xml",
                "file" to "harmony_prefs/com.duckduckgo.subscriptions.store",
                "file" to "harmony_prefs/com.duckduckgo.pir.impl.store.secure",
                "file" to "harmony_prefs/com.duckduckgo.securestorage.store_v2",
                "file" to "harmony_prefs/com.duckduckgo.securestorage.store_v3",
                "file" to "fire_mode_duck_ai_files",
                "database" to "secure_storage_database_encrypted.db",
                "database" to "pir_encrypted.db",
                "database" to "pir_decrypted.db",
                "database" to "fire_mode.db",
                "database" to "fire_mode_duck_ai.db",
                "database" to "pixels.db",
                "database" to "wide_events.db",
                "database" to "event_hub_pixel_state.db",
                "database" to "crash_database.db",
                "database" to "anr_database.db",
                "database" to "crash_anr_internal_database.db",
                "database" to "sync.db",
                "sharedpref" to "com.duckduckgo.mobile.android.app.tracking.conn.loss.xml",
                "sharedpref" to "com.duckduckgo.mobile.android.vpn.prefs.xml",
                "sharedpref" to "com.duckduckgo.android.atp.onboarding.store.xml",
                "sharedpref" to "com.duckduckgo.mobile.atp.cohort.prefs.xml",
                "file" to "harmony_prefs/com.duckduckgo.mobile.android.vpn.feature.registry.v1",
                "file" to "harmony_prefs/com.duckduckgo.vpn.tunnel.config.v1",
                "file" to "harmony_prefs/com.duckduckgo.netp.store.waitlist",
                "file" to "harmony_prefs/com.duckduckgo.android.atp.onboarding.store",
                "file" to "harmony_prefs/com.duckduckgo.mobile.android.vpn.service.notification.v1",
                "file" to "harmony_prefs/com.duckduckgo.networkprotection.dialog.access.revoked.store.v1",
                "file" to "harmony_prefs/com.duckduckgo.networkprotection.cohort.prefs.v1",
                "file" to "harmony_prefs/com.duckduckgo.mobile.atp.cohort.prefs",
                "file" to "harmony_prefs/com.duckduckgo.networkprotection.internal.connectionclass.v1",
                "file" to "harmony_prefs/network.type.collector.file.v1",
                "root" to "app_webview/Profiles/Fire",
            )
    }
}
