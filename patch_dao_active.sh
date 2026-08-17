#!/bin/bash
sed -i 's/SELECT \* FROM vouchers ORDER BY timestamp DESC/SELECT * FROM vouchers WHERE isArchived = 0 ORDER BY timestamp DESC/g' app/src/main/java/com/example/data/LotteryDao.kt
sed -i 's/SELECT \* FROM export_records ORDER BY timestamp DESC/SELECT * FROM export_records WHERE isArchived = 0 ORDER BY timestamp DESC/g' app/src/main/java/com/example/data/LotteryDao.kt
