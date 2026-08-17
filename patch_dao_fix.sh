#!/bin/bash
sed -i '78,84c\
    @Transaction\
    @Query("SELECT * FROM vouchers WHERE isArchived = 1 ORDER BY timestamp DESC")\
    fun getArchivedVouchersWithCustomer(): Flow<List<VoucherWithCustomer>>\
\
    @Transaction\
    @Query("SELECT * FROM vouchers WHERE isArchived = 0 ORDER BY timestamp DESC")\
    fun getAllVouchersWithCustomer(): Flow<List<VoucherWithCustomer>>\
' app/src/main/java/com/example/data/LotteryDao.kt
