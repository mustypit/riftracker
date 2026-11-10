package io.github.mustypit.rifttracker.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mustypit.rifttracker.game.Deck
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Opening a version-1 database (matches only) keeps every match and adds the decks table. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @After fun close() = AppDb.resetForTests()

    @Test
    fun version1To2KeepsMatches() = runBlocking {
        AppDb.resetForTests()
        context.deleteDatabase("rifttracker.db")
        // The version-1 schema, as Room created it (app/schemas/.../1.json).
        val file = context.getDatabasePath("rifttracker.db").also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE IF NOT EXISTS `matches` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startedAt` INTEGER NOT NULL, `finished` INTEGER NOT NULL, `json` TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_matches_finished` ON `matches` (`finished`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_matches_startedAt` ON `matches` (`startedAt`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
            db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'a517a5da1d3c541d4f33dbcf60af01ab')")
            db.execSQL("INSERT INTO matches (startedAt, finished, json) VALUES (1000, 1, '{\"format\":\"BO1\",\"myLegend\":\"Jinx\",\"oppLegend\":\"Garen\",\"startedAt\":1000,\"games\":[]}')")
            db.version = 1
        }
        val db = AppDb.get(context)
        val matches = MatchStore(db.matches()).finished.first()
        assertEquals(1, matches.size)
        assertEquals("Jinx", matches.single().myLegend)
        val decks = DeckStore(db.decks())
        decks.save(Deck(name = "Test", legend = "daughter-of-the-void"))
        assertEquals(listOf("Test"), decks.all.first().map { it.name })
    }
}
