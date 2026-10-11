package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.LocalDictionary
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalDictionaryDao {
    @get:Query("SELECT * FROM localDictionaries ORDER BY sortNumber, name, id")
    val all: List<LocalDictionary>

    @Query("SELECT * FROM localDictionaries ORDER BY sortNumber, name, id")
    fun observe(): Flow<List<LocalDictionary>>

    @Query("SELECT * FROM localDictionaries WHERE id = :id")
    fun get(id: String): LocalDictionary?

    @Query("SELECT * FROM localDictionaries WHERE fingerprint = :fingerprint AND state = 'ready' LIMIT 1")
    fun findFingerprint(fingerprint: String): LocalDictionary?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg dictionaries: LocalDictionary)

    @Query("DELETE FROM localDictionaries WHERE id = :id")
    fun delete(id: String)

    @Query("UPDATE localDictionaries SET sortNumber = :orderNumber WHERE id = :id")
    fun sort(id: String, orderNumber: Int)
}
