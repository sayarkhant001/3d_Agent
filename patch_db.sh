#!/bin/bash
sed -i 's/entities = \[Customer::class, Voucher::class, Bet::class, ExportRecord::class, ExportedNumber::class\]/entities = \[Customer::class, Voucher::class, Bet::class, ExportRecord::class, ExportedNumber::class, BannedNumber::class\]/g' app/src/main/java/com/example/data/AppDatabase.kt
sed -i 's/version = 4/version = 5/g' app/src/main/java/com/example/data/AppDatabase.kt

sed -i '/val MIGRATION_3_4 = object : Migration(3, 4) {/i \
        val MIGRATION_4_5 = object : Migration(4, 5) {\
            override fun migrate(db: SupportSQLiteDatabase) {\
                db.execSQL("CREATE TABLE IF NOT EXISTS `banned_numbers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `number` TEXT NOT NULL)")\
                db.execSQL("ALTER TABLE vouchers ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")\
                db.execSQL("ALTER TABLE export_records ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")\
            }\
        }\
' app/src/main/java/com/example/data/AppDatabase.kt

sed -i 's/.addMigrations(MIGRATION_3_4)/.addMigrations(MIGRATION_3_4, MIGRATION_4_5)/g' app/src/main/java/com/example/data/AppDatabase.kt
