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

    @Test fun identityVersionUpgradePreservesBooksRulesAndCatalogWithoutRebuildingIndexes() {
        val name = "dictionary-identity-migration"
        val id = "00000000-0000-0000-0000-000000000001"
        helper.createDatabase(name, 76).apply {
            // 按旧结构提供必填默认值，真实书籍 JSON 作为迁移保持断言。
            val fields = arrayListOf<String>()
            val values = arrayListOf<Any>()
            query("PRAGMA table_info(books)").use { columns ->
                while (columns.moveToNext()) {
                    val field = columns.getString(1)
                    if ((columns.getInt(3) == 1 && columns.isNull(4)) || field in setOf("bookUrl", "readConfig")) {
                        fields.add("`$field`")
                        values.add(when (field) {
                            "bookUrl" -> "migration-book"
                            "readConfig" -> "{\"reverseToc\":true}"
                            else -> if (columns.getString(2).contains("TEXT")) "" else 0
                        })
                    }
                }
            }
            execSQL("INSERT INTO books (${fields.joinToString(",")}) VALUES (${fields.joinToString(",") { "?" }})", values.toTypedArray())
            execSQL("INSERT INTO dictRules(name,urlRule,showRule,enabled,sortNumber) VALUES('existing','https://example.invalid','','1',0)")
            execSQL("INSERT INTO localDictionaries(id,name,format,sourceFile,fingerprint,inputLanguage,outputLanguage,sortNumber,state,entryCount,warningCount) VALUES(?, 'existing', 'MDICT', 'sample.mdx', ?, 'English', 'Chinese', 7, 'ready', 2, 1)",
                arrayOf(id, "a".repeat(64)))
            close()
        }
        helper.runMigrationsAndValidate(name, 77, true).use { database ->
            database.query("SELECT readConfig FROM books WHERE bookUrl='migration-book'").use {
                assertTrue(it.moveToFirst()); assertEquals("{\"reverseToc\":true}", it.getString(0))
            }
            database.query("SELECT urlRule FROM dictRules WHERE name='existing'").use {
                assertTrue(it.moveToFirst()); assertEquals("https://example.invalid", it.getString(0))
            }
            database.query("SELECT id,sortNumber,fingerprint,state,entryCount,identityVersion FROM localDictionaries").use {
                assertTrue(it.moveToFirst()); assertEquals(id, it.getString(0)); assertEquals(7, it.getInt(1))
                assertEquals("a".repeat(64), it.getString(2)); assertEquals("ready", it.getString(3))
                assertEquals(2, it.getInt(4)); assertEquals(1, it.getInt(5))
            }
        }
        assertEquals(2, DictionaryIndex.VERSION)
    }
}
