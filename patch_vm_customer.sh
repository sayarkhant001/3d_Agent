#!/bin/bash
sed -i '/fun addCustomer/i \
    fun updateCustomer(customer: Customer) {\
        viewModelScope.launch {\
            repository.updateCustomer(customer)\
        }\
    }\
' app/src/main/java/com/example/ui/MainViewModel.kt

sed -i '/suspend fun insertCustomer/i \
    @Update\
    suspend fun updateCustomer(customer: Customer)\
' app/src/main/java/com/example/data/LotteryDao.kt

sed -i '/suspend fun insertCustomer/i \
    suspend fun updateCustomer(customer: Customer) {\
        lotteryDao.updateCustomer(customer)\
    }\
' app/src/main/java/com/example/data/LotteryRepository.kt

