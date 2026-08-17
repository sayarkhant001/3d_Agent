package com.threeDLedger.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "customers")
@Serializable
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val commissionRate: Double = 0.0,
    val multiplier: Int = 80,
    val paidAmount: Double = 0.0
)

@Entity(tableName = "vouchers")
@Serializable
data class Voucher(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerId: Int,
    val batchNumber: Int = 15,
    val date: String,
    val time: String, // e.g. "15"
    val totalAmount: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false,
    val remark: String = ""
)

@Entity(tableName = "bets")
@Serializable
data class Bet(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val voucherId: Int,
    val number: String,
    val amount: Int
)

@Entity(tableName = "export_records")
data class ExportRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val batchNumber: Int,
    val type: String,
    val totalAmount: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false
)

@Entity(tableName = "banned_numbers")
data class BannedNumber(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val number: String
)

@Entity(tableName = "exported_numbers")
data class ExportedNumber(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val exportRecordId: Int,
    val number: String,
    val amount: Int
)
