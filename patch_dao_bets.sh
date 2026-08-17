#!/bin/bash
sed -i 's/SELECT \* FROM bets/SELECT bets.* FROM bets INNER JOIN vouchers ON bets.voucherId = vouchers.id WHERE vouchers.isArchived = 0/g' app/src/main/java/com/example/data/LotteryDao.kt
sed -i 's/SELECT number, SUM(amount) as totalAmount FROM bets GROUP BY number ORDER BY totalAmount DESC/SELECT bets.number, SUM(bets.amount) as totalAmount FROM bets INNER JOIN vouchers ON bets.voucherId = vouchers.id WHERE vouchers.isArchived = 0 GROUP BY bets.number ORDER BY totalAmount DESC/g' app/src/main/java/com/example/data/LotteryDao.kt

sed -i '/suspend fun deleteOldExportArchives(thresholdBatch: Int)/a \
    @Query("DELETE FROM bets WHERE voucherId NOT IN (SELECT id FROM vouchers)")\
    suspend fun deleteOrphanedBets()\
    @Query("DELETE FROM exported_numbers WHERE exportRecordId NOT IN (SELECT id FROM export_records)")\
    suspend fun deleteOrphanedExportedNumbers()\
' app/src/main/java/com/example/data/LotteryDao.kt
