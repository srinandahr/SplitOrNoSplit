package com.srinandahr.splitornosplit

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

// The Interface
interface SplitwiseApi {
    @GET("get_groups")
    suspend fun getGroups(@Header("Authorization") token: String): GroupsResponse

    @POST("create_expense")
    suspend fun createExpense(
        @Header("Authorization") token: String,
        @Body request: ExpenseRequest
    ): Response<Any>
}

// The Object (Singleton) that builds the connection
object SplitwiseNetwork {
    private const val BASE_URL = "https://secure.splitwise.com/api/v3.0/"

    val api: SplitwiseApi by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SplitwiseApi::class.java)
    }
}