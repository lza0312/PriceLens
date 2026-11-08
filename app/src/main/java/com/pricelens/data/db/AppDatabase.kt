package com.pricelens.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SpuEntity::class,
        SkuEntity::class,
        QuoteEntity::class,
        WatchEntity::class,
        AlertEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun spuDao(): SpuDao
    abstract fun skuDao(): SkuDao
    abstract fun quoteDao(): QuoteDao
    abstract fun watchDao(): WatchDao
    abstract fun alertDao(): AlertDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "pricelens.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
