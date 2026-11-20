package com.example.qeesmanager.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

sealed class LoginResult {
    object Success : LoginResult()
    object ExpiredOrInvalid : LoginResult()
    data class Error(val message: String) : LoginResult()
}

class HotspotLoginManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun loginWithVoucher(loginUrl: String, code: String): LoginResult = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("username", code)
                .add("password", "")
                .build()

            val request = Request.Builder()
                .url(loginUrl)
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""

                val expiredKeywords = listOf(
                    "no more sessions",
                    "uptime limit reached",
                    "traffic limit reached",
                    "invalid username or password",
                    "انتهت صلاحية",
                    "رصيدك غير كاف",
                    "انتهى الوقت"
                )

                if (expiredKeywords.any { body.contains(it, ignoreCase = true) }) {
                    return@withContext LoginResult.ExpiredOrInvalid
                }

                if (response.isSuccessful && (body.contains("status") || body.contains("logged in"))) {
                    return@withContext LoginResult.Success
                }

                return@withContext LoginResult.Error("تعذر التأكد من حالة الدخول")
            }
        } catch (e: Exception) {
            return@withContext LoginResult.Error(e.localizedMessage ?: "خطأ في الاتصال بالشبكة")
        }
    }
}
