package com.example.testmessagesimple

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Friendship::class, Message::class],
    version = 2, // Version incrémentée pour refléter les changements de schéma
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
}