package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Customer::class, Voucher::class, Bet::class, ExportRecord::class, ExportedNumber::class, BannedNumber::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lotteryDao(): LotteryDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE vouchers ADD COLUMN remark TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `banned_numbers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `number` TEXT NOT NULL)")
                db.execSQL("ALTER TABLE vouchers ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE export_records ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE customers ADD COLUMN multiplier INTEGER NOT NULL DEFAULT 80")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lottery_database"
                )
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
