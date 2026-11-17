package com.example.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AUD-034 follow-through: the Room v4 schema now ships in app/schemas, so the 3 → 4 migration
 * is exercised against the *real* schema on-device instead of a proxy.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationInstrumentedTest {

    private val testDbName = "migration-test"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrate3to4_preservesRowsAndAddsSourceUri() {
        // Create a v3 database with one row and no sourceUri column.
        val db = helper.createDatabase(testDbName, 3)
        db.execSQL(
            "INSERT INTO recent_files (path, name, size, extension, lastOpened, isSample, parentZipPath, zipEntryPath)" +
                " VALUES ('/sdcard/report.txt', 'report.txt', 1024, 'txt', 123456789, 0, NULL, NULL)"
        )
        db.close()

        // Migrate to v4, validating against the committed schema JSON.
        val migrated = helper.runMigrationsAndValidate(testDbName, 4, true, AppDatabase.MIGRATION_3_4)
        migrated.query("SELECT name, sourceUri FROM recent_files").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("report.txt", cursor.getString(0))
            assertNull("existing rows must keep sourceUri = NULL", cursor.getString(1))
        }
        migrated.close()

        // Opening through the real Room stack must also work.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, testDbName)
            .addMigrations(AppDatabase.MIGRATION_3_4)
            .fallbackToDestructiveMigrationFrom(1, 2)
            .build()
        val files = kotlinx.coroutines.runBlocking {
            kotlinx.coroutines.flow.first(roomDb.recentFileDao().getAllRecentFiles())
        }
        assertEquals(1, files.size)
        roomDb.close()
    }
}
