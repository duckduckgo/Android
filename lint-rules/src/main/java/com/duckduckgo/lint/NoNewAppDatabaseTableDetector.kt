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

package com.duckduckgo.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope.JAVA_FILE
import com.android.tools.lint.detector.api.Severity.ERROR
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.TextFormat.TEXT
import com.intellij.psi.PsiClassType
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UClassLiteralExpression
import java.util.EnumSet

/**
 * Freezes the set of tables in [com.duckduckgo.app.global.db.AppDatabase].
 *
 * `app.db` predates feature-owned databases, so it collected tables from many unrelated features that
 * now share one version counter and one set of migrations. It is legacy and must only ever shrink:
 * new storage belongs to the feature that owns it.
 *
 * The grandfathered set is deliberately hardcoded rather than baselined:
 * adding an entry has to happen in the same diff as the table, where a reviewer will see it.
 */
@Suppress("UnstableApiUsage")
class NoNewAppDatabaseTableDetector : Detector(), SourceCodeScanner {

    override fun getApplicableUastTypes() = listOf(UClass::class.java)

    override fun createUastHandler(context: JavaContext): UElementHandler = Handler(context)

    internal class Handler(private val context: JavaContext) : UElementHandler() {

        override fun visitClass(node: UClass) {
            if (node.qualifiedName != APP_DATABASE_FQN) return
            val entities = node.findAnnotation(ROOM_DATABASE_FQN)?.findDeclaredAttributeValue("entities") ?: return
            val entries = (entities as? UCallExpression)?.valueArguments ?: listOf(entities)

            for (entry in entries) {
                // An entity that doesn't resolve is reported rather than skipped, so a classpath gap can't let a new table through.
                val entityFqn = ((entry as? UClassLiteralExpression)?.type as? PsiClassType)?.resolve()?.qualifiedName
                if (entityFqn in GRANDFATHERED_ENTITIES) continue

                context.report(
                    NO_NEW_APP_DATABASE_TABLE,
                    entry,
                    context.getLocation(entry),
                    NO_NEW_APP_DATABASE_TABLE.getBriefDescription(TEXT),
                )
            }
        }
    }

    companion object {
        private const val APP_DATABASE_FQN = "com.duckduckgo.app.global.db.AppDatabase"
        private const val ROOM_DATABASE_FQN = "androidx.room.Database"

        /**
         * Entities that were in `AppDatabase` when this rule was introduced, in declaration order.
         * Do not add to this set. Remove an entry when its table leaves `AppDatabase`.
         */
        private val GRANDFATHERED_ENTITIES = setOf(
            "com.duckduckgo.app.trackerdetection.model.TdsTracker",
            "com.duckduckgo.app.trackerdetection.model.TdsEntity",
            "com.duckduckgo.app.trackerdetection.model.TdsDomainEntity",
            "com.duckduckgo.app.trackerdetection.model.TdsCnameEntity",
            "com.duckduckgo.app.privacy.model.UserAllowListedDomain",
            "com.duckduckgo.app.privacy.db.NetworkLeaderboardEntry",
            "com.duckduckgo.app.privacy.db.SitesVisitedEntity",
            "com.duckduckgo.app.tabs.model.TabEntity",
            "com.duckduckgo.app.tabs.model.TabSelectionEntity",
            "com.duckduckgo.app.tabs.db.TabPageContextEntity",
            "com.duckduckgo.app.browser.session.WebViewSessionEntity",
            "com.duckduckgo.app.bookmarks.db.BookmarkEntity",
            "com.duckduckgo.app.bookmarks.db.FavoriteEntity",
            "com.duckduckgo.app.bookmarks.db.BookmarkFolderEntity",
            "com.duckduckgo.app.survey.model.Survey",
            "com.duckduckgo.app.cta.model.DismissedCta",
            "com.duckduckgo.app.usage.search.SearchCountEntity",
            "com.duckduckgo.app.usage.app.AppDaysUsedEntity",
            "com.duckduckgo.app.browser.rating.db.AppEnjoymentEntity",
            "com.duckduckgo.app.notification.model.Notification",
            "com.duckduckgo.app.privacy.model.PrivacyProtectionCountsEntity",
            "com.duckduckgo.app.trackerdetection.model.TdsMetadata",
            "com.duckduckgo.app.onboarding.store.UserStage",
            "com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteEntity",
            "com.duckduckgo.app.global.events.db.UserEventEntity",
            "com.duckduckgo.app.location.data.LocationPermissionEntity",
            "com.duckduckgo.app.statistics.model.PixelEntity",
            "com.duckduckgo.app.browser.pageloadpixel.PageLoadedPixelEntity",
            "com.duckduckgo.app.browser.pageloadpixel.firstpaint.PagePaintedPixelEntity",
            "com.duckduckgo.app.trackerdetection.db.WebTrackerBlocked",
            "com.duckduckgo.app.browser.cookies.db.AuthCookieAllowedDomainEntity",
            "com.duckduckgo.savedsites.store.Entity",
            "com.duckduckgo.savedsites.store.Relation",
            "com.duckduckgo.app.browser.defaultbrowsing.prompts.store.DefaultBrowserPromptsAppUsageEntity",
            "com.duckduckgo.app.tabs.db.DuckAiTabSessionEntity",
        )

        val NO_NEW_APP_DATABASE_TABLE = Issue.create(
            id = "NoNewAppDatabaseTable",
            briefDescription = "Do not add new tables to AppDatabase",
            explanation = """
                `AppDatabase` (`app.db`) is legacy and must only ever shrink. It is not accepting new \
                entities in its `@Database(entities = ...)` list.

                Every table in `app.db` shares one version counter and one set of migrations with \
                unrelated features, and ties the feature's storage to a database it does not own.

                Put new storage in the module that owns the feature instead: declare a Room database \
                in that feature's `-impl` module and keep its entities and DAOs there.

                Do not add an entry to the grandfathered list to silence this check.
            """,
            category = Category.CORRECTNESS,
            priority = 10,
            severity = ERROR,
            implementation = Implementation(
                NoNewAppDatabaseTableDetector::class.java,
                EnumSet.of(JAVA_FILE),
            ),
        )
    }
}
