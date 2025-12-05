package com.example.derzhava.net

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object ApiClient {

    // Базовый URL для обращения к API.
    // Измените на адрес вашего VPS, где развернуты PHP‑скрипты из каталога `api`.
    // Например: "https://your-vps-domain.com/api/". Использование https строго рекомендовано.
    // По умолчанию используется заглушка, которую необходимо заменить при деплое.
    private const val BASE_URL = "http://89.104.74.16/api/"

    private val httpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
    }

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
