package io.legado.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "localDictionaries")
data class LocalDictionary(
    @PrimaryKey val id: String,
    val name: String,
    val format: String,
    val sourceFile: String,
    val fingerprint: String,
    val inputLanguage: String = "",
    val outputLanguage: String = "",
    val sortNumber: Int = 0,
    val state: String = "ready",
    val entryCount: Long = 0,
    val warningCount: Int = 0
)
