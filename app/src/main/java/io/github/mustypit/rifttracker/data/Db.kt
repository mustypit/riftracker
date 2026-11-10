package io.github.mustypit.rifttracker.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import io.github.mustypit.rifttracker.game.Match
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One row per match. The match itself (games, point log) is JSON: new fields get defaults when old
 * rows are read, so most model changes need no migration. [finished] and [startedAt] are columns
 * for querying.
 */
@Entity(tableName = "matches", indices = [Index("finished"), Index("startedAt")])
data class MatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val finished: Boolean,
    val json: String,
)

@Dao
interface MatchDao {
    @Query("SELECT * FROM matches WHERE finished = 1 ORDER BY startedAt DESC")
    fun observeFinished(): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches WHERE finished = 0 ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<MatchEntity?>

    @Query("SELECT * FROM matches WHERE finished = 0 ORDER BY startedAt DESC LIMIT 1")
    suspend fun active(): MatchEntity?

    @Insert
    suspend fun insert(m: MatchEntity): Long

    @Update
    suspend fun update(m: MatchEntity)

    @Query("DELETE FROM matches WHERE id = :id")
    suspend fun delete(id: Long)
}

/** One row per deck, the deck as JSON (like matches). */
@Entity(tableName = "decks")
data class DeckEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val updatedAt: Long,
    val json: String,
)

@Dao
interface DeckDao {
    @Query("SELECT * FROM decks ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DeckEntity>>

    @Insert
    suspend fun insert(d: DeckEntity): Long

    @Update
    suspend fun update(d: DeckEntity)

    @Query("DELETE FROM decks WHERE id = :id")
    suspend fun delete(id: Long)
}

/** Version 2 adds the decks table; matches are untouched. */
val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `decks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `updatedAt` INTEGER NOT NULL, `json` TEXT NOT NULL)")
    }
}

@Database(entities = [MatchEntity::class, DeckEntity::class], version = 2, exportSchema = true)
abstract class AppDb : RoomDatabase() {
    abstract fun matches(): MatchDao
    abstract fun decks(): DeckDao

    companion object {
        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "rifttracker.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }

        fun resetForTests() = synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}

/** Matches in and out of the database. */
class MatchStore(private val dao: MatchDao) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun decode(e: MatchEntity) = json.decodeFromString<Match>(e.json).copy(id = e.id)
    private fun encode(m: Match) = MatchEntity(id = m.id, startedAt = m.startedAt, finished = m.isFinished, json = json.encodeToString(m.copy(id = 0)))

    val finished: Flow<List<Match>> = dao.observeFinished().map { list -> list.map(::decode) }
    val active: Flow<Match?> = dao.observeActive().map { it?.let(::decode) }

    suspend fun activeNow(): Match? = dao.active()?.let(::decode)

    /** Inserts a new match (id 0) or updates it; returns it with its id. */
    suspend fun save(m: Match): Match =
        if (m.id == 0L) m.copy(id = dao.insert(encode(m))) else m.also { dao.update(encode(it)) }

    suspend fun delete(id: Long) = dao.delete(id)
}

/** Decks in and out of the database. */
class DeckStore(private val dao: DeckDao) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun decode(e: DeckEntity) = json.decodeFromString<io.github.mustypit.rifttracker.game.Deck>(e.json).copy(id = e.id, updatedAt = e.updatedAt)
    private fun encode(d: io.github.mustypit.rifttracker.game.Deck) = DeckEntity(id = d.id, updatedAt = d.updatedAt, json = json.encodeToString(d.copy(id = 0)))

    val all: Flow<List<io.github.mustypit.rifttracker.game.Deck>> = dao.observeAll().map { list -> list.map(::decode) }

    suspend fun save(d: io.github.mustypit.rifttracker.game.Deck): io.github.mustypit.rifttracker.game.Deck =
        if (d.id == 0L) d.copy(id = dao.insert(encode(d))) else d.also { dao.update(encode(it)) }

    suspend fun delete(id: Long) = dao.delete(id)
}
