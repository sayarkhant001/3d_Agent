package com.threeDLedger.data

import kotlinx.coroutines.flow.Flow

class LotteryRepository(private val lotteryDao: LotteryDao) {
    val allCustomers: Flow<List<Customer>> = lotteryDao.getAllCustomers()
    val archivedVouchers: Flow<List<VoucherWithCustomer>> = lotteryDao.getArchivedVouchersWithCustomer()

    val allVouchersWithCustomer: Flow<List<VoucherWithCustomer>> = lotteryDao.getAllVouchersWithCustomer()
    val allVouchersWithBets: Flow<List<VoucherWithBets>> = lotteryDao.getAllVouchersWithBets()
    val allBets: Flow<List<Bet>> = lotteryDao.getAllBets()
    val numberExposures: Flow<List<NumberExposure>> = lotteryDao.getNumberExposures()
    val allBannedNumbers: Flow<List<BannedNumber>> = lotteryDao.getAllBannedNumbers()
    val allExportRecords: Flow<List<ExportRecordWithNumbers>> = lotteryDao.getAllExportRecords()

    suspend fun updateCustomer(customer: Customer) {
        lotteryDao.updateCustomer(customer)
    }

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

    suspend fun insertBannedNumber(bannedNumber: BannedNumber) {
        lotteryDao.insertBannedNumber(bannedNumber)
    }

    suspend fun deleteBannedNumber(bannedNumber: BannedNumber) {
        lotteryDao.deleteBannedNumber(bannedNumber)
    }

    suspend fun archiveAndReset(thresholdBatch: Int) {
        lotteryDao.archiveAllVouchers()
        lotteryDao.archiveAllExportRecords()
        lotteryDao.deleteOldArchives(thresholdBatch)
        lotteryDao.deleteOldExportArchives(thresholdBatch)
        lotteryDao.deleteOrphanedBets()
        lotteryDao.deleteOrphanedExportedNumbers()
    }

    suspend fun getVoucherDetails(voucherId: Int): VoucherWithBets? {
        return lotteryDao.getVoucherWithBets(voucherId)
    }
}
