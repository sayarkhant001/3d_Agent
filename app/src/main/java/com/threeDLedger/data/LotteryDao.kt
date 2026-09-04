package com.threeDLedger.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class VoucherWithCustomer(
    @Embedded val voucher: Voucher,
    @Relation(
        parentColumn = "customerId",
        entityColumn = "id"
    )
    val customer: Customer
)

data class VoucherWithBets(
    @Embedded val voucher: Voucher,
    @Relation(
        parentColumn = "id",
        entityColumn = "voucherId"
    )
    val bets: List<Bet>
)

data class NumberExposure(
    val number: String,
    val totalAmount: Int
)

data class ArchiveBatchSummary(
    val batchNumber: Int,
    val voucherCount: Int,
    val customerCount: Int,
    val totalAmount: Int,
    val minDate: String,
    val maxDate: String
)

data class ExportRecordWithNumbers(
    @Embedded val record: ExportRecord,
    @Relation(
        parentColumn = "id",
        entityColumn = "exportRecordId"
    )
    val numbers: List<ExportedNumber>
)

@Dao
interface LotteryDao {
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM banned_numbers")
    fun getAllBannedNumbers(): Flow<List<BannedNumber>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBannedNumber(bannedNumber: BannedNumber)

    @Delete
    suspend fun deleteBannedNumber(bannedNumber: BannedNumber)

    @Query("UPDATE vouchers SET isArchived = 1")
    suspend fun archiveAllVouchers()
    @Query("UPDATE export_records SET isArchived = 1")
    suspend fun archiveAllExportRecords()


    @Query("DELETE FROM vouchers WHERE batchNumber <= :thresholdBatch AND isArchived = 1")
    suspend fun deleteOldArchives(thresholdBatch: Int)
    @Query("DELETE FROM export_records WHERE batchNumber <= :thresholdBatch AND isArchived = 1")
    suspend fun deleteOldExportArchives(thresholdBatch: Int)
    @Query("DELETE FROM bets WHERE voucherId NOT IN (SELECT id FROM vouchers)")
    suspend fun deleteOrphanedBets()
    @Query("DELETE FROM exported_numbers WHERE exportRecordId NOT IN (SELECT id FROM export_records)")
    suspend fun deleteOrphanedExportedNumbers()



    @Update
    suspend fun updateCustomer(customer: Customer)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoucher(voucher: Voucher): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBets(bets: List<Bet>)

    @Transaction
    @Query("SELECT * FROM vouchers WHERE isArchived = 1 ORDER BY timestamp DESC")
    fun getArchivedVouchersWithCustomer(): Flow<List<VoucherWithCustomer>>

    @Query("""
        SELECT
            batchNumber,
            COUNT(id)              AS voucherCount,
            COUNT(DISTINCT customerId) AS customerCount,
            SUM(totalAmount)       AS totalAmount,
            MIN(date)              AS minDate,
            MAX(date)              AS maxDate
        FROM vouchers
        WHERE isArchived = 1
        GROUP BY batchNumber
        ORDER BY batchNumber DESC
    """)
    fun getArchivedBatchSummaries(): Flow<List<ArchiveBatchSummary>>

    @Transaction
    @Query("SELECT * FROM vouchers WHERE isArchived = 0 ORDER BY timestamp DESC")
    fun getAllVouchersWithCustomer(): Flow<List<VoucherWithCustomer>>


    @Transaction
    @Query("SELECT * FROM vouchers WHERE isArchived = 0 ORDER BY timestamp DESC")
    fun getAllVouchersWithBets(): Flow<List<VoucherWithBets>>

    @Transaction
    @Query("SELECT * FROM vouchers WHERE id = :voucherId")
    suspend fun getVoucherWithBets(voucherId: Int): VoucherWithBets?
    
    @Query("SELECT bets.* FROM bets INNER JOIN vouchers ON bets.voucherId = vouchers.id WHERE vouchers.isArchived = 0")
    fun getAllBets(): Flow<List<Bet>>
    
    @Query("SELECT bets.number, SUM(bets.amount) as totalAmount FROM bets INNER JOIN vouchers ON bets.voucherId = vouchers.id WHERE vouchers.isArchived = 0 GROUP BY bets.number ORDER BY totalAmount DESC")
    fun getNumberExposures(): Flow<List<NumberExposure>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExportRecord(record: ExportRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExportedNumbers(numbers: List<ExportedNumber>)

    @Transaction
    @Query("SELECT * FROM export_records WHERE isArchived = 0 ORDER BY timestamp DESC")
    fun getAllExportRecords(): Flow<List<ExportRecordWithNumbers>>
}
