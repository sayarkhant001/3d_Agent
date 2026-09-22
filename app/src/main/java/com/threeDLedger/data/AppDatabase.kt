package com.threeDLedger.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Customer::class, Voucher::class, Bet::class, ExportRecord::class, ExportedNumber::class, BannedNumber::class], version = 8, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lotteryDao(): LotteryDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE banned_numbers ADD COLUMN amountLimit INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vouchers_customerId` ON `vouchers` (`customerId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vouchers_batchNumber` ON `vouchers` (`batchNumber`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_vouchers_isArchived` ON `vouchers` (`isArchived`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bets_voucherId` ON `bets` (`voucherId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bets_number` ON `bets` (`number`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exported_numbers_exportRecordId` ON `exported_numbers` (`exportRecordId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exported_numbers_number` ON `exported_numbers` (`number`)")
            }
        }

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
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

