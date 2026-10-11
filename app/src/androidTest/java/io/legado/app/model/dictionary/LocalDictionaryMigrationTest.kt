package io.legado.app.model.dictionary

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.legado.app.data.AppDatabase
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalDictionaryMigrationTest {
    @get:Rule val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory()
    )

    @Test fun upgradePreservesOnlineRulesAndCreatesEmptyLocalCatalog() {
        val name = "local-dictionary-migration"
        helper.createDatabase(name, 75).apply {
            execSQL("INSERT INTO dictRules(name,urlRule,showRule,enabled,sortNumber) VALUES('existing','https://example.invalid','','1',0)")
            close()
        }
        helper.runMigrationsAndValidate(name, 76, true).use { database ->
            database.query("SELECT name FROM dictRules").use { assertTrue(it.moveToFirst()); assertEquals("existing", it.getString(0)) }
            database.query("SELECT count(*) FROM localDictionaries").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
    }
}
