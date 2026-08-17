#!/bin/bash
sed -i '/fun getAllVouchersWithCustomer/i \
    @Transaction\
    @Query("SELECT * FROM vouchers WHERE isArchived = 1 ORDER BY timestamp DESC")\
    fun getArchivedVouchersWithCustomer(): Flow<List<VoucherWithCustomer>>\
' app/src/main/java/com/example/data/LotteryDao.kt

sed -i '/val allVouchersWithCustomer/i \
    val archivedVouchers: Flow<List<VoucherWithCustomer>> = lotteryDao.getArchivedVouchersWithCustomer()\
' app/src/main/java/com/example/data/LotteryRepository.kt

sed -i '/val allExportRecords/i \
    val archivedVouchers: StateFlow<List<VoucherWithCustomer>> = repository.archivedVouchers\
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())\
' app/src/main/java/com/example/ui/MainViewModel.kt
