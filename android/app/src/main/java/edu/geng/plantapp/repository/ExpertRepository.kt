package edu.geng.plantapp.repository

import android.content.Context
import android.net.Uri
import edu.geng.plantapp.data.local.DataStoreManager
import edu.geng.plantapp.data.remote.ExpertApi
import edu.geng.plantapp.data.remote.ExpertStatsResponse
import edu.geng.plantapp.data.remote.ContributionListResponse
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class ExpertRepository(
    private val expertApi: ExpertApi,
    private val dsManager: DataStoreManager,
    private val context: Context
) {
    suspend fun getStats(): Resource<ExpertStatsResponse> {
        return try {
            val token = dsManager.tokenFlow.first()
            if (token.isNullOrEmpty()) return Resource.Error("未登录")

            val response = expertApi.getStats("Bearer $token")
            if (response.isSuccessful) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error("获取统计失败: ${response.code()}")
            }
        } catch (e: Exception) {
            Resource.Error("网络异常: ${e.localizedMessage}")
        }
    }

    suspend fun contribute(diseaseName: String, treatmentPlan: String, imageUris: List<Uri>): Resource<String> {
        return try {
            val token = dsManager.tokenFlow.first()
            if (token.isNullOrEmpty()) return Resource.Error("未登录")

            val diseaseNameReq = diseaseName.toRequestBody("text/plain".toMediaTypeOrNull())
            val planReq = treatmentPlan.toRequestBody("text/plain".toMediaTypeOrNull())

            val imageParts = imageUris.mapIndexed { index, uri ->
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: ByteArray(0)
                inputStream?.close()
                val reqFile = bytes.toRequestBody("image/*".toMediaTypeOrNull())
                MultipartBody.Part.createFormData("images", "image_$index.jpg", reqFile)
            }

            val response = expertApi.contribute("Bearer $token", diseaseNameReq, planReq, imageParts)
            if (response.isSuccessful) {
                Resource.Success(response.body()?.msg ?: "贡献成功")
            } else {
                Resource.Error("提交失败: ${response.code()}")
            }
        } catch (e: Exception) {
            Resource.Error("网络异常: ${e.localizedMessage}")
        }
    }

    suspend fun resubmitContribution(id: Int, diseaseName: String, treatmentPlan: String, imageUris: List<Uri>): Resource<String> {
        return try {
            val token = dsManager.tokenFlow.first()
            if (token.isNullOrEmpty()) return Resource.Error("未登录")

            val diseaseNameReq = diseaseName.toRequestBody("text/plain".toMediaTypeOrNull())
            val planReq = treatmentPlan.toRequestBody("text/plain".toMediaTypeOrNull())

            val imageParts = imageUris.mapIndexed { index, uri ->
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: ByteArray(0)
                inputStream?.close()
                val reqFile = bytes.toRequestBody("image/*".toMediaTypeOrNull())
                MultipartBody.Part.createFormData("images", "image_$index.jpg", reqFile)
            }

            val response = expertApi.resubmitContribution("Bearer $token", id, diseaseNameReq, planReq, imageParts)
            if (response.isSuccessful) {
                Resource.Success(response.body()?.msg ?: "重新提交成功")
            } else {
                Resource.Error("重新提交失败: ${response.code()}")
            }
        } catch (e: Exception) {
            Resource.Error("网络异常: ${e.localizedMessage}")
        }
    }

    suspend fun getContributions(page: Int, size: Int = 3): Resource<ContributionListResponse> {
        return try {
            val token = dsManager.tokenFlow.first()
            if (token.isNullOrEmpty()) return Resource.Error("未登录")

            val response = expertApi.getContributions("Bearer $token", page, size)
            if (response.isSuccessful) {
                Resource.Success(response.body()!!)
            } else {
                Resource.Error("获取列表失败: ${response.code()}")
            }
        } catch (e: Exception) {
            Resource.Error("网络异常: ${e.localizedMessage}")
        }
    }
}
