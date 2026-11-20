package com.example.qeesmanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface VoucherDao {
    @Query("SELECT * FROM vouchers WHERE isExpired = 0 ORDER BY createdAt DESC")
    fun getActiveVouchers(): Flow<List<Voucher>>

    @Query("SELECT * FROM vouchers WHERE isExpired = 1 ORDER BY expiredAt DESC")
    fun getExpiredVouchers(): Flow<List<Voucher>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVoucher(voucher: Voucher)

    @Update
    suspend fun updateVoucher(voucher: Voucher)

    @Delete
    suspend fun deleteVoucher(voucher: Voucher)

    @Query("DELETE FROM vouchers WHERE isExpired = 1")
    suspend fun deleteAllExpiredVouchers(): Int

    @Query("DELETE FROM vouchers WHERE isExpired = 1 AND expiredAt < :thresholdTimestamp")
    suspend fun autoCleanOldExpired(thresholdTimestamp: Long)
}
