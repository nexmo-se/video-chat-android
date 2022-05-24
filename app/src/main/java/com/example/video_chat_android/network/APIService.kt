package com.example.video_chat_android.network
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

interface APIService {
    @GET("session/{roomName}")
    fun getCredential(@Path("roomName") roomName: String?): Call<GetSessionResponse?>?
    val session:Call<GetSessionResponse?>?
}