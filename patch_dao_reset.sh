#!/bin/bash
sed -i '/suspend fun archiveAllVouchers()/a \
    @Query("UPDATE export_records SET isArchived = 1")\
    suspend fun archiveAllExportRecords()\
' app/src/main/java/com/example/data/LotteryDao.kt

sed -i '/suspend fun deleteOldArchives(thresholdBatch: Int)/a \
    @Query("DELETE FROM export_records WHERE batchNumber <= :thresholdBatch AND isArchived = 1")\
    suspend fun deleteOldExportArchives(thresholdBatch: Int)\
' app/src/main/java/com/example/data/LotteryDao.kt
