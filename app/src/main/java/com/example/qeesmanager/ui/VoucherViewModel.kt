package com.example.qeesmanager.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.qeesmanager.data.Voucher
import com.example.qeesmanager.data.VoucherDao
import com.example.qeesmanager.network.HotspotLoginManager
import com.example.qeesmanager.network.LoginResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class VoucherViewModel(
    private val dao: VoucherDao,
    private val loginManager: HotspotLoginManager
) : ViewModel() {

    val activeVouchers = dao.getActiveVouchers()
    val expiredVouchers = dao.getExpiredVouchers()

    private val _uiEvent = MutableSharedFlow<String>()
    val uiEvent = _uiEvent.asSharedFlow()

    fun connectVoucher(voucher: Voucher) {
        viewModelScope.launch {
            val result = loginManager.loginWithVoucher(voucher.loginUrl, voucher.code)
            when (result) {
                is LoginResult.Success -> {
                    _uiEvent.emit("تم تسجيل الدخول بنجاح!")
                }
                is LoginResult.ExpiredOrInvalid -> {
                    dao.updateVoucher(
                        voucher.copy(isExpired = true, expiredAt = System.currentTimeMillis())
                    )
                    _uiEvent.emit("الكرت منتهي الصلاحية، تم نقله لقائمة المنتهية.")
                }
                is LoginResult.Error -> {
                    _uiEvent.emit("تنبيه: ${result.message}")
                }
            }
        }
    }

    fun markAsExpired(voucher: Voucher) {
        viewModelScope.launch {
            dao.updateVoucher(voucher.copy(isExpired = true, expiredAt = System.currentTimeMillis()))
            _uiEvent.emit("تم نقل الكرت إلى المنتهية")
        }
    }

    fun deleteAllExpired() {
        viewModelScope.launch {
            val count = dao.deleteAllExpiredVouchers()
            if (count > 0) {
                _uiEvent.emit("تم حذف $count كرت منتهي بنجاح")
            } else {
                _uiEvent.emit("لا توجد كروت منتهية لحذفها")
            }
        }
    }

    fun runAutoCleanup(days: Int = 3) {
        viewModelScope.launch {
            val threshold = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
            dao.autoCleanOldExpired(threshold)
        }
    }
}
