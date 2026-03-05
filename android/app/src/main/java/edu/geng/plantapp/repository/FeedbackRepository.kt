package edu.geng.plantapp.repository

import android.graphics.Bitmap
import edu.geng.plantapp.data.local.DataStoreManager
import edu.geng.plantapp.data.remote.*
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

class FeedbackRepository(
    private val predictApiExt: PredictApiExtension,
    private val feedbackApi: FeedbackApi,
    private val dsManager: DataStoreManager,
    private val context: android.content.Context
) {
    fun getContext() = context

    companion object {
        private var cachedHistory: List<HistoryItem>? = null
    }

    suspend fun syncEdgeResult(bitmap: Bitmap, label: String, confidence: Float): Resource<Int> {
        return try {
            val token = dsManager.tokenFlow.first()
            if (token.isNullOrEmpty()) return Resource.Error("未登录")

            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            val byteArray = stream.toByteArray()
            
            val requestFile = byteArray.toRequestBody("image/jpeg".toMediaTypeOrNull(), 0, byteArray.size)
            val body = MultipartBody.Part.createFormData("file", "edge_image.jpg", requestFile)
            
            val labelBody = label.toRequestBody("text/plain".toMediaTypeOrNull())
            val confBody = confidence.toString().toRequestBody("text/plain".toMediaTypeOrNull())

            val response = predictApiExt.syncEdgeResult("Bearer $token", body, labelBody, confBody)
            if (response.isSuccessful) {
                val data = response.body()
                if (data?.status == "success" && data.history_id != null) {
                    cachedHistory = null // Invalidate cache
                    Resource.Success(data.history_id)
                } else {
                    Resource.Error(data?.message ?: "同步失败")
                }
            } else {
                val errorMsg = parseApiError(response.errorBody()?.string(), "同步服务器异常: ${response.code()}")
                Resource.Error(errorMsg)
            }
        } catch (e: Exception) {
            Resource.Error("网络异常: ${e.message}")
        }
    }

    suspend fun predictCloud(bitmap: Bitmap): Resource<CloudPredictData> {
        return try {
            val token = dsManager.tokenFlow.first()
            if (token.isNullOrEmpty()) return Resource.Error("未登录")

            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            val byteArray = stream.toByteArray()
            
            val requestFile = byteArray.toRequestBody("image/jpeg".toMediaTypeOrNull(), 0, byteArray.size)
            val body = MultipartBody.Part.createFormData("file", "cloud_image.jpg", requestFile)
            
            val response = predictApiExt.predictCloud("Bearer $token", body)
            if (response.isSuccessful) {
                val data = response.body()
                if (data?.status == "success" && data.data != null) {
                    cachedHistory = null // Invalidate cache
                    Resource.Success(data.data)
                } else {
                    Resource.Error(data?.message ?: "云端预测失败")
                }
            } else {
                val errorMsg = parseApiError(response.errorBody()?.string(), "预测服务器异常: ${response.code()}")
                Resource.Error(errorMsg)
            }
        } catch (e: Exception) {
            Resource.Error("网络异常: ${e.message}")
        }
    }

    suspend fun getHistoryList(forceRefresh: Boolean = false): Resource<List<HistoryItem>> {
        if (!forceRefresh && cachedHistory != null) {
            return Resource.Success(cachedHistory!!)
        }
        return try {
            val token = dsManager.tokenFlow.first()
            if (token.isNullOrEmpty()) return Resource.Error("未登录")

            val response = feedbackApi.getHistory(token = "Bearer $token")
            if (response.isSuccessful) {
                val body = response.body()
                if (body?.status == "success") {
                    val list = body.data ?: emptyList()
                    cachedHistory = list // Cache the successful result
                    Resource.Success(list)
                } else {
                    Resource.Error(body?.msg ?: "获取失败")
                }
            } else {
                val errorMsg = parseApiError(response.errorBody()?.string(), "服务器异常: ${response.code()}")
                Resource.Error(errorMsg)
            }
        } catch(e: Exception) {
            Resource.Error("网络异常: ${e.message}")
        }
    }

    suspend fun submitFeedback(historyId: Int, score: Int, comments: String): Resource<String> {
        return try {
            val token = dsManager.tokenFlow.first()
            if (token.isNullOrEmpty()) return Resource.Error("未登录")

            val req = FeedbackRequest(historyId, score, reason = comments)
            val response = feedbackApi.submitFeedback(token = "Bearer $token", request = req)
            if (response.isSuccessful) {
                cachedHistory = null // Invalidate so next fetch shows updated feedback_score
                val data = response.body()
                Resource.Success(data?.message ?: data?.msg ?: "提交成功")
            } else {
                val errorMsg = parseApiError(response.errorBody()?.string(), "提交失败: ${response.code()}")
                Resource.Error(errorMsg)
            }
        } catch (e: Exception) {
            Resource.Error("网络异常: ${e.message}")
        }
    }
}
