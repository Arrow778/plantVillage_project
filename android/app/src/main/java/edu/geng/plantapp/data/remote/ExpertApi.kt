package edu.geng.plantapp.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import okhttp3.MultipartBody
import okhttp3.RequestBody

data class ContributeRequest(
    val disease_name: String,
    val treatment_plan: String,
    val image_url: String? = "default.jpg"
)

data class ContributeResponse(
    val status: String?,
    val msg: String?
)

data class ExpertStatsResponse(
    val total: Int,
    val pending: Int,
    val accepted: Int
)

data class ContributionItem(
    val id: Int,
    val disease_name: String?,
    val status: String?,
    val reject_reason: String?,
    val treatment_plan: String?,
    val image_url: String?,
    val created_at: String?
)

data class ContributionListResponse(
    val items: List<ContributionItem>,
    val total: Int,
    val pages: Int,
    val current_page: Int
)

interface ExpertApi {
    @Multipart
    @POST("expert/contribute")
    suspend fun contribute(
        @Header("Authorization") token: String,
        @Part("disease_name") diseaseName: RequestBody,
        @Part("treatment_plan") treatmentPlan: RequestBody,
        @Part images: List<MultipartBody.Part>
    ): Response<ContributeResponse>

    @Multipart
    @POST("expert/contribute/{id}")
    suspend fun resubmitContribution(
        @Header("Authorization") token: String,
        @retrofit2.http.Path("id") id: Int,
        @Part("disease_name") diseaseName: RequestBody,
        @Part("treatment_plan") treatmentPlan: RequestBody,
        @Part images: List<MultipartBody.Part>
    ): Response<ContributeResponse>

    @GET("expert/stats")
    suspend fun getStats(
        @Header("Authorization") token: String
    ): Response<ExpertStatsResponse>

    @GET("expert/list")
    suspend fun getContributions(
        @Header("Authorization") token: String,
        @retrofit2.http.Query("page") page: Int,
        @retrofit2.http.Query("size") size: Int = 3
    ): Response<ContributionListResponse>
}
