#!/bin/bash
sed -i '/val allExportRecords/i \    val allBannedNumbers: Flow<List<BannedNumber>> = lotteryDao.getAllBannedNumbers()' app/src/main/java/com/example/data/LotteryRepository.kt

sed -i '/suspend fun getVoucherDetails/i \
    suspend fun insertBannedNumber(bannedNumber: BannedNumber) {\
        lotteryDao.insertBannedNumber(bannedNumber)\
    }\
\
    suspend fun deleteBannedNumber(bannedNumber: BannedNumber) {\
        lotteryDao.deleteBannedNumber(bannedNumber)\
    }\
\
    suspend fun archiveAndReset(thresholdBatch: Int) {\
        lotteryDao.archiveAllVouchers()\
        lotteryDao.deleteOldArchives(thresholdBatch)\
    }\
' app/src/main/java/com/example/data/LotteryRepository.kt
