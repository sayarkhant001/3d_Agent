#!/bin/bash
sed -i '/lotteryDao.archiveAllVouchers()/a \        lotteryDao.archiveAllExportRecords()' app/src/main/java/com/example/data/LotteryRepository.kt
sed -i '/lotteryDao.deleteOldArchives(thresholdBatch)/a \        lotteryDao.deleteOldExportArchives(thresholdBatch)' app/src/main/java/com/example/data/LotteryRepository.kt
