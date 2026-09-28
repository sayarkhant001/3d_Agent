package com.threeDLedger.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "customers")
@Serializable
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val commissionRate: Double = 0.0,
    val multiplier: Int = 600,
    val paidAmount: Double = 0.0
)

@Entity(
    tableName = "vouchers",
    indices = [
        Index(value = ["customerId"]),
        Index(value = ["batchNumber"]),
        Index(value = ["isArchived"])
    ]
)
@Serializable
data class Voucher(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val customerId: Int,
    val batchNumber: Int = 1,
    val date: String,
    val time: String,
    val totalAmount: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false,
    val remark: String = ""
)

@Entity(
    tableName = "bets",
    indices = [
        Index(value = ["voucherId"]),
        Index(value = ["number"])
    ]
)
@Serializable
data class Bet(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val voucherId: Int,
    val number: String,
    val amount: Int
)

@Entity(tableName = "dines")
@Serializable
data class Dine(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val commissionRate: Double = 15.0,
    val exactMultiplier: Int = 600,
    val tuwtMultiplier: Int = 10
)

@Entity(tableName = "export_records")
data class ExportRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val batchNumber: Int,
    val type: String,
    val totalAmount: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false,
    val dineId: Int = 0,
    val dineName: String = "",
    val voucherSerial: Int = 1
)

@Entity(tableName = "banned_numbers")
data class BannedNumber(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val number: String,
    val amountLimit: Int = 0
)

data class BannedLimitRemoval(
    val number: String,
    val attemptedAmount: Int,
    val limitAmount: Int,
    val currentBetTotal: Int,
    val acceptedAmount: Int,
    val removedAmount: Int,
    val reason: String
)

@Entity(
    tableName = "exported_numbers",
    indices = [
        Index(value = ["exportRecordId"]),
        Index(value = ["number"])
    ]
)
data class ExportedNumber(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val exportRecordId: Int,
    val number: String,
    val amount: Int
)

@Entity(tableName = "three_d_winning_history")
data class ThreeDWinningHistory(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val drawDate: String,
    val drawDateFormatted: String,
    val winningNumber: String,
    val firstPrize6D: String = "",
    val remark: String = ""
)
