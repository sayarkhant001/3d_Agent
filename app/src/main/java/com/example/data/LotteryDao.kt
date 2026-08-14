package com.example.data

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoucher(voucher: Voucher): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBets(bets: List<Bet>)

    @Transaction
    @Query("SELECT * FROM vouchers ORDER BY timestamp DESC")
    fun getAllVouchersWithCustomer(): Flow<List<VoucherWithCustomer>>

    @Transaction
    @Query("SELECT * FROM vouchers ORDER BY timestamp DESC")
    fun getAllVouchersWithBets(): Flow<List<VoucherWithBets>>

    @Transaction
    @Query("SELECT * FROM vouchers WHERE id = :voucherId")
    suspend fun getVoucherWithBets(voucherId: Int): VoucherWithBets?
    
    @Query("SELECT * FROM bets")
    fun getAllBets(): Flow<List<Bet>>
    
    @Query("SELECT number, SUM(amount) as totalAmount FROM bets GROUP BY number ORDER BY totalAmount DESC")
    fun getNumberExposures(): Flow<List<NumberExposure>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExportRecord(record: ExportRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExportedNumbers(numbers: List<ExportedNumber>)

    @Transaction
    @Query("SELECT * FROM export_records ORDER BY timestamp DESC")
    fun getAllExportRecords(): Flow<List<ExportRecordWithNumbers>>
}
