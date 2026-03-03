package edu.geng.plantapp.ml

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TFLiteHelper(private val context: Context) {
    
    private var interpreter: Interpreter? = null
    // Temporarily disabled for stability on high-refresh emulators/some devices
    // private var gpuDelegate: GpuDelegate? = null
    private var labels: List<String> = emptyList()

    private val inputImageWidth = 224 
    private val inputImageHeight = 224

    init {
        try {
            val modelBuffer = loadModelFile(context, "model_2_resnet50.tflite")
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(modelBuffer, options)
            labels = loadLabels(context, "labels.txt")
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        context.assets.openFd(modelName).use { fileDescriptor ->
            FileInputStream(fileDescriptor.fileDescriptor).use { inputStream ->
                val fileChannel = inputStream.channel
                val startOffset = fileDescriptor.startOffset
                val declaredLength = fileDescriptor.declaredLength
                return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
            }
        }
    }

    private fun loadLabels(context: Context, fileName: String): List<String> {
        return context.assets.open(fileName).bufferedReader().useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
        }
    }

    data class RecognitionResult(
        val label: String,
        val confidence: Float
    )

    fun classify(bm: Bitmap): RecognitionResult {
        if (interpreter == null || labels.isEmpty()) {
            return RecognitionResult("本地模型未就绪/缺失 assets", 0.0f)
        }

        try {
            // 1. CenterCrop 算法保证构图与训练一致
            val size = kotlin.math.min(bm.width, bm.height)
            val x = (bm.width - size) / 2
            val y = (bm.height - size) / 2
            val croppedBitmap = Bitmap.createBitmap(bm, x, y, size, size)
            
            // 2. Resize 到模型输入尺寸
            val bitmap = Bitmap.createScaledBitmap(croppedBitmap, inputImageWidth, inputImageHeight, true)
            
            val byteBuffer = ByteBuffer.allocateDirect(1 * inputImageWidth * inputImageHeight * 3 * 4)
            byteBuffer.order(ByteOrder.nativeOrder())

            val intValues = IntArray(inputImageWidth * inputImageHeight)
            bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

            var pixel = 0
            for (i in 0 until inputImageWidth) {
                for (j in 0 until inputImageHeight) {
                    val `val` = intValues[pixel++]
                    val r = (`val` shr 16 and 0xFF) / 255.0f
                    val g = (`val` shr 8 and 0xFF) / 255.0f
                    val b = (`val` and 0xFF) / 255.0f

                    // 3. ImageNet 归一化标准化
                    byteBuffer.putFloat((r - 0.485f) / 0.229f)
                    byteBuffer.putFloat((g - 0.456f) / 0.224f)
                    byteBuffer.putFloat((b - 0.406f) / 0.225f)
                }
            }

            val probabilityArray = Array(1) { FloatArray(labels.size) }
            interpreter?.run(byteBuffer, probabilityArray)

            val probabilities = probabilityArray[0]
            
            // Softmax 归一化
            var maxLogit = Float.NEGATIVE_INFINITY
            for (p in probabilities) {
                if (p > maxLogit) maxLogit = p
            }
            
            var sumExp = 0.0f
            for (i in probabilities.indices) {
                probabilities[i] = kotlin.math.exp((probabilities[i] - maxLogit).toDouble()).toFloat()
                sumExp += probabilities[i]
            }

            var maxIdx = -1
            var maxProb = -1f
            for (i in probabilities.indices) {
                val prob = probabilities[i] / sumExp
                if (prob > maxProb) {
                    maxProb = prob
                    maxIdx = i
                }
            }

            return if (maxIdx != -1) {
                var finalProb = maxProb
                if (finalProb >= 0.9999f) {
                    finalProb = 0.9998f
                }
                RecognitionResult(labels[maxIdx], finalProb)
            } else {
                RecognitionResult("推演失败：置信矩阵异常", 0.0f)
            }
        } catch(e: Throwable) {
             return RecognitionResult("本地解析崩溃: ${e.message}", 0.0f)
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    companion object {
        fun getChineseName(label: String): String {
            return when (label) {
                "Apple___Apple_scab" -> "苹果黑星病"
                "Apple___Black_rot" -> "苹果黑腐病"
                "Apple___Cedar_apple_rust" -> "苹果雪松锈病"
                "Apple___healthy" -> "苹果 (健康)"
                "Background_without_leaves" -> "非植物/背景"
                "Blueberry___healthy" -> "蓝莓 (健康)"
                "Cherry___Powdery_mildew" -> "樱桃白粉病"
                "Cherry___healthy" -> "樱桃 (健康)"
                "Corn___Cercospora_leaf_spot Gray_leaf_spot" -> "玉米灰斑病"
                "Corn___Common_rust" -> "玉米锈病"
                "Corn___Northern_Leaf_Blight" -> "玉米大斑病"
                "Corn___healthy" -> "玉米 (健康)"
                "Grape___Black_rot" -> "葡萄黑腐病"
                "Grape___Esca_(Black_Measles)" -> "葡萄黑痘病/埃斯卡病"
                "Grape___Leaf_blight_(Isariopsis_Leaf_Spot)" -> "葡萄褐斑病"
                "Grape___healthy" -> "葡萄 (健康)"
                "Orange___Haunglongbing_(Citrus_greening)" -> "柑橘黄龙病"
                "Peach___Bacterial_spot" -> "桃树细菌性穿孔病"
                "Peach___healthy" -> "桃树 (健康)"
                "Pepper,_bell___Bacterial_spot" -> "甜椒细菌性叶斑病"
                "Pepper,_bell___healthy" -> "甜椒 (健康)"
                "Potato___Early_blight" -> "马铃薯早疫病"
                "Potato___Late_blight" -> "马铃薯晚疫病"
                "Potato___healthy" -> "马铃薯 (健康)"
                "Raspberry___healthy" -> "树莓 (健康)"
                "Soybean___healthy" -> "大豆 (健康)"
                "Squash___Powdery_mildew" -> "南瓜白粉病"
                "Strawberry___Leaf_scorch" -> "草莓褐斑病（蛇眼病）"
                "Strawberry___healthy" -> "草莓 (健康)"
                "Tomato___Bacterial_spot" -> "番茄细菌性斑点病"
                "Tomato___Early_blight" -> "番茄早疫病"
                "Tomato___Late_blight" -> "番茄晚疫病"
                "Tomato___Leaf_Mold" -> "番茄叶霉病"
                "Tomato___Septoria_leaf_spot" -> "番茄斑枯病"
                "Tomato___Spider_mites Two-spotted_spider_mite" -> "番茄红蜘蛛 (二斑叶螨)"
                "Tomato___Target_Spot" -> "番茄靶斑病"
                "Tomato___Tomato_Yellow_Leaf_Curl_Virus" -> "番茄黄化曲叶病毒病"
                "Tomato___Tomato_mosaic_virus" -> "番茄花叶病毒病"
                "Tomato___healthy" -> "番茄 (健康)"
                else -> label
            }
        }
    }
}
