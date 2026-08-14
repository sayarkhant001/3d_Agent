package com.example.data

import kotlinx.coroutines.flow.Flow

class LotteryRepository(private val lotteryDao: LotteryDao) {
    val allCustomers: Flow<List<Customer>> = lotteryDao.getAllCustomers()
    val allVouchersWithCustomer: Flow<List<VoucherWithCustomer>> = lotteryDao.getAllVouchersWithCustomer()
    val allVouchersWithBets: Flow<List<VoucherWithBets>> = lotteryDao.getAllVouchersWithBets()
    val allBets: Flow<List<Bet>> = lotteryDao.getAllBets()
    val numberExposures: Flow<List<NumberExposure>> = lotteryDao.getNumberExposures()
    val allExportRecords: Flow<List<ExportRecordWithNumbers>> = lotteryDao.getAllExportRecords()

    suspend fun insertCustomer(customer: Customer) {
        lotteryDao.insertCustomer(customer)
    }

    suspend fun insertVoucherWithBets(voucher: Voucher, bets: List<Bet>) {
        val voucherId = lotteryDao.insertVoucher(voucher).toInt()
        val betsWithVoucherId = bets.map { it.copy(voucherId = voucherId) }
        lotteryDao.insertBets(betsWithVoucherId)
    }

    suspend fun insertExportRecord(record: ExportRecord): Long {
        return lotteryDao.insertExportRecord(record)
    }

    suspend fun insertExportedNumbers(numbers: List<ExportedNumber>) {
        lotteryDao.insertExportedNumbers(numbers)
    }

    suspend fun getVoucherDetails(voucherId: Int): VoucherWithBets? {
        return lotteryDao.getVoucherWithBets(voucherId)
    }
}
