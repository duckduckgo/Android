

package com.duckduckgo.history.impl.store

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter

/**
 * Represents a history entry in the database.
 *
 * @property id The unique ID of the history entry.
 * @property url The URL of the history entry.
 * @property title The title of the history entry.
 * @property query The search query associated with the history entry, if any.
 * @property isSerp Whether the history entry is a Search Engine Results Page (SERP).
 */
@Entity(tableName = "history_entries", indices = [Index(value = ["url"], unique = true)])
data class HistoryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val query: String?,
    val isSerp: Boolean,
) {
    override fun equals(other: Any?): Boolean {
        return this.url == (other as? HistoryEntryEntity)?.url
    }

    override fun hashCode(): Int {
        return url.hashCode()
    }
}

/**
 * Represents a visit to a history entry in the database.
 *
 * @property historyEntryId The ID of the history entry that was visited.
 * @property timestamp The timestamp of the visit. This should be generated using the [DatabaseDateFormatter.timestamp] function.
 */
@Entity(
    tableName = "visits_list",
    primaryKeys = ["timestamp", "historyEntryId"],
    foreignKeys = [
        ForeignKey(
            entity = HistoryEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["historyEntryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class VisitEntity(
    val historyEntryId: Long,
    val timestamp: String,
)

/**
 * Represents a history entry along with its associated visits.
 *
 * @property historyEntry The history entry.
 * @property visits The list of visits to the history entry.
 */
data class HistoryEntryWithVisits(
    @Embedded val historyEntry: HistoryEntryEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "historyEntryId",
    )
    val visits: List<VisitEntity>,
)
