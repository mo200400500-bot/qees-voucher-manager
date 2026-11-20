package com.example.qeesmanager.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vouchers")
data class Voucher(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val code: String,
    val networkName: String,
    val loginUrl: String,
    val packageName: String = "باقة عامة",
    val isExpired: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val expiredAt: Long? = null
)
