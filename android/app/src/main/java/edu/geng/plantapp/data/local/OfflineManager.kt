package edu.geng.plantapp.data.local

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * 离线记录管理器 (毕设版)
 * 用于在无网络时将识别结果保存到本地 JSON 和文件系统
 */
class OfflineManager(private val context: Context) {

    private val offlineDir = File(context.filesDir, "offline_records")
    private val imagesDir = File(offlineDir, "images")
    private val metaFile = File(offlineDir, "metadata.json")

    init {
        if (!imagesDir.exists()) imagesDir.mkdirs()
    }

    /**
     * 保存一条离线记录
     */
    fun saveRecord(bitmap: Bitmap, label: String, confidence: Float): Boolean {
        return try {
            val timestamp = System.currentTimeMillis()
            val timeStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
            val imageName = "img_$timestamp.jpg"
            val imageFile = File(imagesDir, imageName)

            // 1. 保存图片到本地
            val out = FileOutputStream(imageFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
            out.flush()
            out.close()

            // 2. 更新元数据 JSON
            val records = loadRecords()
            val newRecord = JSONObject().apply {
                put("id", timestamp)
                put("label", label)
                put("confidence", confidence)
                put("image_path", imageFile.absolutePath)
                put("time", timeStr)
                put("status", "pending") // 标记为待同步
            }
            records.put(newRecord)

            metaFile.writeText(records.toString())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 加载现有的所有离线记录
     */
    fun loadRecords(): JSONArray {
        return if (metaFile.exists()) {
            try {
                JSONArray(metaFile.readText())
            } catch (e: Exception) {
                JSONArray()
            }
        } else {
            JSONArray()
        }
    }

    /**
     * 获取待同步记录的数量
     */
    fun getPendingCount(): Int {
        return loadRecords().length()
    }

    /**
     * 清空已同步的记录 (通常在批量同步成功后调用)
     */
    fun clearAll() {
        if (offlineDir.exists()) {
            offlineDir.deleteRecursively()
            imagesDir.mkdirs()
        }
    }
}
