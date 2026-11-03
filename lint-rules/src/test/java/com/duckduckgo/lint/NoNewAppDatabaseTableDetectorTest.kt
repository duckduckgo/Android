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

import com.android.tools.lint.checks.infrastructure.TestFiles.kt
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.duckduckgo.lint.NoNewAppDatabaseTableDetector.Companion.NO_NEW_APP_DATABASE_TABLE
import org.junit.Test

@Suppress("UnstableApiUsage")
class NoNewAppDatabaseTableDetectorTest {

    @Test
    fun `grandfathered entities - no errors`() {
        lint()
            .files(
                ROOM_STUB,
                *GRANDFATHERED_ENTITY_STUBS,
                kt(
                    """
                package com.duckduckgo.app.global.db

                import androidx.room.Database
                import androidx.room.RoomDatabase
                import com.duckduckgo.app.tabs.model.TabEntity
                import com.duckduckgo.app.trackerdetection.model.*
                import com.duckduckgo.savedsites.store.Entity

                @Database(
                    exportSchema = true,
                    version = 64,
                    entities = [
                        TdsTracker::class,
                        TdsEntity::class,
                        TabEntity::class,
                        Entity::class,
                    ],
                )
                abstract class AppDatabase : RoomDatabase()
                """,
                ).indented(),
            )
            .issues(NO_NEW_APP_DATABASE_TABLE)
            .run()
            .expectClean()
    }

    @Test
    fun `new entity - error reported`() {
        lint()
            .files(
                ROOM_STUB,
                *GRANDFATHERED_ENTITY_STUBS,
                NEW_FEATURE_ENTITY_STUB,
                kt(
                    """
                package com.duckduckgo.app.global.db

                import androidx.room.Database
                import androidx.room.RoomDatabase
                import com.duckduckgo.app.newfeature.NewFeatureEntity
                import com.duckduckgo.app.tabs.model.TabEntity

                @Database(
                    exportSchema = true,
                    version = 64,
                    entities = [
                        TabEntity::class,
                        NewFeatureEntity::class,
                    ],
                )
                abstract class AppDatabase : RoomDatabase()
                """,
                ).indented(),
            )
            .issues(NO_NEW_APP_DATABASE_TABLE)
            .run()
            .expect(
                """
                src/com/duckduckgo/app/global/db/AppDatabase.kt:13: Error: Do not add new tables to AppDatabase [NoNewAppDatabaseTable]
                        NewFeatureEntity::class,
                        ~~~~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent(),
            )
    }

    @Test
    fun `entity reusing a grandfathered simple name from another package - error reported`() {
        lint()
            .files(
                ROOM_STUB,
                kt(
                    """
                package com.duckduckgo.newfeature

                class TabEntity
                """,
                ).indented(),
                kt(
                    """
                package com.duckduckgo.app.global.db

                import androidx.room.Database
                import androidx.room.RoomDatabase
                import com.duckduckgo.newfeature.TabEntity

                @Database(
                    exportSchema = true,
                    version = 64,
                    entities = [
                        TabEntity::class,
                    ],
                )
                abstract class AppDatabase : RoomDatabase()
                """,
                ).indented(),
            )
            .issues(NO_NEW_APP_DATABASE_TABLE)
            .run()
            .expect(
                """
                src/com/duckduckgo/app/global/db/AppDatabase.kt:11: Error: Do not add new tables to AppDatabase [NoNewAppDatabaseTable]
                        TabEntity::class,
                        ~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent(),
            )
    }

    @Test
    fun `unresolvable entity - error reported`() {
        lint()
            .files(
                ROOM_STUB,
                *GRANDFATHERED_ENTITY_STUBS,
                kt(
                    """
                package com.duckduckgo.app.global.db

                import androidx.room.Database
                import androidx.room.RoomDatabase
                import com.duckduckgo.app.tabs.model.TabEntity

                @Database(
                    exportSchema = true,
                    version = 64,
                    entities = [
                        TabEntity::class,
                        UnknownEntity::class,
                    ],
                )
                abstract class AppDatabase : RoomDatabase()
                """,
                ).indented(),
            )
            .allowCompilationErrors()
            .issues(NO_NEW_APP_DATABASE_TABLE)
            .run()
            .expect(
                """
                src/com/duckduckgo/app/global/db/AppDatabase.kt:12: Error: Do not add new tables to AppDatabase [NoNewAppDatabaseTable]
                        UnknownEntity::class,
                        ~~~~~~~~~~~~~~~~~~~~
                1 errors, 0 warnings
                """.trimIndent(),
            )
    }

    @Test
    fun `new entity in a database with the same name in another package - no errors`() {
        lint()
            .files(
                ROOM_STUB,
                NEW_FEATURE_ENTITY_STUB,
                kt(
                    """
                package com.duckduckgo.newfeature.impl.store

                import androidx.room.Database
                import androidx.room.RoomDatabase
                import com.duckduckgo.app.newfeature.NewFeatureEntity

                @Database(
                    exportSchema = true,
                    version = 1,
                    entities = [
                        NewFeatureEntity::class,
                    ],
                )
                abstract class AppDatabase : RoomDatabase()
                """,
                ).indented(),
            )
            .issues(NO_NEW_APP_DATABASE_TABLE)
            .run()
            .expectClean()
    }

    companion object {
        private val ROOM_STUB = kt(
            """
            package androidx.room

            import kotlin.reflect.KClass

            annotation class Database(
                val entities: Array<KClass<*>> = [],
                val version: Int,
                val exportSchema: Boolean = true,
            )

            abstract class RoomDatabase
            """,
        ).indented()

        private val GRANDFATHERED_ENTITY_STUBS = arrayOf(
            kt(
                """
                package com.duckduckgo.app.trackerdetection.model

                class TdsTracker

                class TdsEntity
                """,
            ).indented(),
            kt(
                """
                package com.duckduckgo.app.tabs.model

                class TabEntity
                """,
            ).indented(),
            kt(
                """
                package com.duckduckgo.savedsites.store

                class Entity
                """,
            ).indented(),
        )

        private val NEW_FEATURE_ENTITY_STUB = kt(
            """
            package com.duckduckgo.app.newfeature

            class NewFeatureEntity
            """,
        ).indented()
    }
}
