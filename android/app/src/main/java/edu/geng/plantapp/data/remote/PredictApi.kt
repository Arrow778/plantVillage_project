package edu.geng.plantapp.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

data class WikiData(
    val title: String,
    val symptoms: String,
    val standard_treatment: String,
    val danger_level: String
)

data class WikiResponse(
    val status: String?,
    val data: WikiData?,
    val msg: String?,
    val query_id: String?
)

interface PredictApi {
    @GET("predict/wiki/detail")
    suspend fun getWikiDetail(
        @Header("Authorization") token: String,
        @Query("disease_name") diseaseName: String
    ): Response<WikiResponse>
}
