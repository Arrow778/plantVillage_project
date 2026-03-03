package edu.geng.plantapp.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object NetworkClient {
    private const val PREFIX_URL = "api/v1/"
    private const val DEFAULT_URL = "https://xztgs21n-5000.usw3.devtunnels.ms/${PREFIX_URL}"
    var BASE_URL = DEFAULT_URL

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private var _retrofit: Retrofit? = null
    private val retrofit: Retrofit
        get() {
            if (_retrofit == null) {
                _retrofit = Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(okHttpClient)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
            }
            return _retrofit!!
        }

    // 当 URL 改变时，清空缓存的实例，下次访问将重新创建
    fun updateBaseUrl(newUrl: String) {
        var url = if (newUrl.endsWith("/")) newUrl else "$newUrl/"
        if (!url.contains("api/v1/")) {
            url += "api/v1/"
        }
        BASE_URL = url
        _retrofit = null
        _authApi = null
        _predictApi = null
        _predictApiExtension = null
        _feedbackApi = null
        _expertApi = null
    }

    private var _authApi: AuthApi? = null
    val authApi: AuthApi get() = _authApi ?: retrofit.create(AuthApi::class.java).also { _authApi = it }

    private var _predictApi: PredictApi? = null
    val predictApi: PredictApi get() = _predictApi ?: retrofit.create(PredictApi::class.java).also { _predictApi = it }

    private var _predictApiExtension: PredictApiExtension? = null
    val predictApiExtension: PredictApiExtension get() = _predictApiExtension ?: retrofit.create(PredictApiExtension::class.java).also { _predictApiExtension = it }

    private var _feedbackApi: FeedbackApi? = null
    val feedbackApi: FeedbackApi get() = _feedbackApi ?: retrofit.create(FeedbackApi::class.java).also { _feedbackApi = it }

    private var _expertApi: ExpertApi? = null
    val expertApi: ExpertApi get() = _expertApi ?: retrofit.create(ExpertApi::class.java).also { _expertApi = it }
}
