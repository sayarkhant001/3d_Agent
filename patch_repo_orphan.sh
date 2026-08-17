#!/bin/bash
sed -i '/lotteryDao.deleteOldExportArchives(thresholdBatch)/a \        lotteryDao.deleteOrphanedBets()\n        lotteryDao.deleteOrphanedExportedNumbers()' app/src/main/java/com/example/data/LotteryRepository.kt
