package com.timerapp.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

object UpdateChecker {

    const val OWNER = "GodBook"
    const val REPO = "timer-app"
    const val REPO_URL = "https://github.com/$OWNER/$REPO"

    data class ReleaseInfo(val version: String, val notes: String, val apkUrl: String?)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetchLatest(): ReleaseInfo = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$OWNER/$REPO/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("请求失败 HTTP ${resp.code}")
            val root = Json.parseToJsonElement(resp.body!!.string()).jsonObject
            val tag = root["tag_name"]?.jsonPrimitive?.contentOrNull
                ?: throw IOException("响应缺少 tag_name")
            val notes = (root["body"] as? JsonPrimitive)?.contentOrNull ?: ""
            val apkUrl = root["assets"]?.jsonArray
                ?.firstOrNull {
                    it.jsonObject["name"]?.jsonPrimitive?.contentOrNull?.endsWith(".apk") == true
                }
                ?.jsonObject?.get("browser_download_url")?.jsonPrimitive?.contentOrNull
            ReleaseInfo(tag.removePrefix("v"), notes, apkUrl)
        }
    }

    fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".").map { it.trim().toIntOrNull() ?: 0 }
        val l = local.split(".").map { it.trim().toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "apk").apply { mkdirs() }
        val file = File(dir, "update.apk")
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("下载失败 HTTP ${resp.code}")
            val body = resp.body ?: throw IOException("空响应")
            val total = body.contentLength()
            body.byteStream().use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        done += read
                        if (total > 0) onProgress((done * 100 / total).toInt())
                    }
                }
            }
        }
        file
    }
}

object ApkInstaller {

    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun requestInstallPermission(context: Context) {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            )
        )
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
