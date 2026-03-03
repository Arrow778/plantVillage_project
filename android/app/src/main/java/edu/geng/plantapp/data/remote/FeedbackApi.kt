package edu.geng.plantapp.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

data class HistoryItem(
    val id: Int,
    val prediction: String?,
    val confidence: String?,
    val image_url: String?,
    val time: String?,
    val has_feedback: Boolean?,
    val feedback_score: Int?,   // null=未反馈, 1=点赞, -1=点踩
    val engine: String?
)

data class HistoryListResponse(
    val status: String?,
    val data: List<HistoryItem>?,
    val msg: String?
)

data class EdgeSyncResponse(
    val status: String,
    val history_id: Int?,
    val message: String?
)

data class CloudPredictData(
    val prediction: String,
    val confidence: Double,
    val image_id: String,
    val engine: String,
    val top_3: List<Map<String, String>>? = null
)

data class CloudPredictResponse(
    val status: String,
    val data: CloudPredictData?,
    val message: String?
)

data class FeedbackRequest(
    val history_id: Int,
    val score: Int,
    val reason: String = "",
    val suggestion: String = ""
)

data class DefaultResponse(
    val status: String?,
    val message: String?,
    val msg: String?
)

interface PredictApiExtension {
    @Multipart
    @POST("predict/edge_sync")
    suspend fun syncEdgeResult(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part,
        @Part("label") label: RequestBody,
        @Part("confidence") confidence: RequestBody
    ): Response<EdgeSyncResponse>

    @Multipart
    @POST("predict/cloud")
    suspend fun predictCloud(
        @Header("Authorization") token: String,
        @Part file: MultipartBody.Part
    ): Response<CloudPredictResponse>
}

interface FeedbackApi {
    @GET("feedback/history/list")
    suspend fun getHistory(
        @Header("Authorization") token: String
    ): Response<HistoryListResponse>
    
    @POST("feedback/submit")
    suspend fun submitFeedback(
        @Header("Authorization") token: String,
        @Body request: FeedbackRequest
    ): Response<DefaultResponse>
}
