package com.example.qeesmanager

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.qeesmanager.data.AppDatabase
import com.example.qeesmanager.data.Voucher
import com.example.qeesmanager.databinding.ActivityMainBinding
import com.example.qeesmanager.network.HotspotLoginManager
import com.example.qeesmanager.ui.VoucherViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: VoucherViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val dao = AppDatabase.getDatabase(this).voucherDao()
        val loginManager = HotspotLoginManager()
        viewModel = VoucherViewModel(dao, loginManager)

        viewModel.runAutoCleanup(days = 3)

        binding.btnSaveAndConnect.setOnClickListener {
            val code = binding.etVoucherCode.text.toString().trim()
            val url = binding.etLoginUrl.text.toString().trim()
            if (code.isEmpty() || url.isEmpty()) {
                Toast.makeText(this, "يرجى إدخال رمز الكرت ورابط الدخول", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val voucher = Voucher(
                code = code,
                networkName = "شبكة الهوتسبوت",
                loginUrl = url
            )
            lifecycleScope.launch {
                dao.insertVoucher(voucher)
                viewModel.connectVoucher(voucher)
                binding.etVoucherCode.text?.clear()
            }
        }

        binding.btnDeleteExpired.setOnClickListener {
            viewModel.deleteAllExpired()
        }

        lifecycleScope.launch {
            viewModel.uiEvent.collectLatest { msg ->
                Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
            }
        }

        lifecycleScope.launch {
            viewModel.activeVouchers.collectLatest { activeList ->
                viewModel.expiredVouchers.collectLatest { expiredList ->
                    binding.tvStats.text = "الكروت الفعالة: ${activeList.size} | الكروت المنتهية: ${expiredList.size}"
                    val text = if (activeList.isEmpty()) {
                        "لا توجد كروت فعالة حالياً."
                    } else {
                        activeList.joinToString("\n\n") { "• رمز الكرت: ${it.code}\n  الرابط: ${it.loginUrl}" }
                    }
                    binding.tvVouchersList.text = text
                }
            }
        }
    }
}
