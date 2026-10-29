package com.example.shiftsnap.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// ═══════════════════════════════════════════════════════════════════════════════
// ApiClient.kt
// Modul: com.example.shiftsnap.api
//
// Singleton Retrofit client yang dikonfigurasi sekali dan dapat digunakan
// di seluruh aplikasi.
//
// Cara penggunaan:
//   val response = ApiClient.apiService.submitReport(requestBody)
// ═══════════════════════════════════════════════════════════════════════════════

object ApiClient {

    /**
     * Base URL backend ShiftSnap.
     * Ganti dengan URL production saat deploy.
     * Pastikan diakhiri dengan "/" (wajib untuk Retrofit).
     */
    private const val BASE_URL = "https://api.shiftsnap.example.com/v1/"

    // ── OkHttp Client ─────────────────────────────────────────────────────────

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        // Tampilkan body request/response di Logcat saat DEBUG
        // Ubah ke NONE saat production agar tidak ada data sensitif di log
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            // Tambahkan header Authorization di sini jika backend memerlukan token
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept",       "application/json")
                    // .addHeader("Authorization", "Bearer ${SessionManager.getToken()}")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // ── Retrofit Instance ─────────────────────────────────────────────────────

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // ── Exposed ApiService ────────────────────────────────────────────────────

    /**
     * Instance ApiService siap pakai.
     * Lazy → hanya dibuat sekali saat pertama kali diakses.
     */
    val apiService: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }
}
