package com.example.testmessagesimple

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Friendship::class, Message::class],
    version = 3, // Version 3 : nouveaux champs dans Message pour stockage local
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
}