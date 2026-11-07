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

@Database(entities = [MatchEntity::class], version = 1, exportSchema = true)
abstract class AppDb : RoomDatabase() {
    abstract fun matches(): MatchDao

    companion object {
        @Volatile private var instance: AppDb? = null

        fun get(context: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDb::class.java, "rifttracker.db")
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
