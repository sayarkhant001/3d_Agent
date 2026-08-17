#!/bin/bash
sed -i 's/val isArchived: Boolean = false/val isArchived: Boolean = false,\n    val remark: String = ""/g' app/src/main/java/com/example/data/Entities.kt

sed -i 's/version = 5/version = 6/g' app/src/main/java/com/example/data/AppDatabase.kt
sed -i '/val MIGRATION_4_5/i \        val MIGRATION_5_6 = object : Migration(5, 6) {\n            override fun migrate(db: SupportSQLiteDatabase) {\n                db.execSQL("ALTER TABLE vouchers ADD COLUMN remark TEXT NOT NULL DEFAULT '\'''\''")\n            }\n        }' app/src/main/java/com/example/data/AppDatabase.kt
sed -i 's/\.addMigrations(MIGRATION_3_4, MIGRATION_4_5)/\.addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)/g' app/src/main/java/com/example/data/AppDatabase.kt
