package com.donut.miximage.utils


import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import com.donut.mixfile.util.showToast
import com.donut.miximage.app
import com.donut.miximage.appScope
import com.donut.miximage.utils.compose.showErrorDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okio.IOException
import java.io.EOFException
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.net.Inet4Address
import java.net.NetworkInterface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.log10
import kotlin.math.pow


fun String.copyToClipboard(showToast: Boolean = true) {
    val clipboard = getClipBoard()
    val clip = ClipData.newPlainText("Copied Text", this)
    clipboard.setPrimaryClip(clip)
    if (showToast) showToast("复制成功")
}

fun getClipBoard(context: Context = app.applicationContext): ClipboardManager {
    return context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
}

fun readClipBoardText(): String {
    val clipboard = getClipBoard()
    val clip = clipboard.primaryClip
    if (clip != null && clip.itemCount > 0) {
        val text = clip.getItemAt(0).text
        return text?.toString() ?: ""
    }
    return ""
}

fun String.sanitizeFileName(): String {
    // 定义非法字符的正则表达式
    val illegalChars = "[/\\\\:*?\"<>|]".toRegex()


    return this
        .replace(illegalChars, " ")
        .trim()
        .takeLast(255)
        .ifEmpty { "unnamed_file" }
}

fun saveBitmapToStorage(bitmap: Bitmap, fileName: String) {
    // 检查外部存储是否可用


    // 获取 Pictures 目录并创建文件
    val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
    val file = File(directory, "$fileName.jpg")

    try {
        FileOutputStream(file).use { fos ->
            // 将 Bitmap 压缩为 JPEG 格式并写入文件
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos)
            fos.flush()
            // 通知媒体库更新
            MediaStore.Images.Media.insertImage(
                app.contentResolver,
                file.absolutePath,
                fileName.sanitizeFileName(),
                null
            )
        }
    } catch (e: IOException) {
        e.printStackTrace()

    }
}


fun formatFileSize(bytes: Long, mb: Boolean = false): String {
    if (bytes <= 0) return "0 B"
    if (mb && bytes > 1024 * 1024) {
        return String.format(
            Locale.US,
            "%.2f MB",
            bytes / 1024.0 / 1024.0
        )
    }
    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB")
    val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()

    return String.format(
        Locale.US,
        "%.2f %s",
        bytes / 1024.0.pow(digitGroups.toDouble()),
        units.getOrNull(digitGroups)
    )
}

fun getAppVersion(context: Context): Pair<String, Long> {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName ?: ""
        val versionCode =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else packageInfo.versionCode.toLong()
        Pair(versionName, versionCode)
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
        Pair("Unknown", -1L)
    }
}


class CachedDelegate<T>(val getKeys: () -> Array<Any?>, private val initializer: () -> T) {
    private var cache: T? = null
    private var keys: Array<Any?> = arrayOf()

    operator fun getValue(thisRef: Any?, property: Any?): T {
        val newKeys = getKeys()
        if (cache == null || !keys.contentEquals(newKeys)) {
            keys = newKeys
            cache = initializer()
        }
        return cache!!
    }

    operator fun setValue(thisRef: Any?, property: Any?, value: T) {
        cache = value
    }
}


@OptIn(ExperimentalEncodingApi::class)
fun ByteArray.encodeToBase64() = Base64.encode(this)

@OptIn(ExperimentalEncodingApi::class)
fun String.decodeBase64() = Base64.decode(this)

@OptIn(ExperimentalEncodingApi::class)
fun String.decodeBase64String() = Base64.decode(this).decodeToString()

fun String.encodeToBase64() = this.toByteArray().encodeToBase64()

fun <T> List<T>.at(index: Long): T {
    var fixedIndex = index % this.size
    if (fixedIndex < 0) {
        fixedIndex += this.size
    }
    return this[fixedIndex.toInt()]
}

fun String.parseSortNum(): BigInteger {
    val numberStr = this.filter { it.isDigit() }.ifEmpty { "0" }
    return BigInteger(numberStr)
}

fun extractNumber(str: String, start: Int): Long {
    var result = 0L
    var i = start
    while (i < str.length && str[i].isDigit()) {
        result = result * 10 + (str[i] - '0')
        i++
    }
    return result
}

fun String.compareByName(str2: String): Int {
    var i1 = 0
    var i2 = 0
    val str1 = this
    while (i1 < str1.length && i2 < str2.length) {
        // 处理数字部分
        val char1 = str1[i1]
        val char2 = str2[i2]
        if (char1.isDigit() && char2.isDigit()) {
            val num1 =
                extractNumber(str1, i1).also { i1 += it.toString().length }
            val num2 =
                extractNumber(str2, i2).also { i2 += it.toString().length }
            //相等则继续提取下个数字进行比较
            if (num1 != num2) return num1.compareTo(num2)
        }
        // 处理非数字部分
        else {
            if (char1 != char2) return char1.compareTo(char2)
            i1++
            i2++
        }
    }
    return str1.length.compareTo(str2.length)
}

fun <T> List<T>.at(index: Int): T {
    return this.at(index.toLong())
}

fun getAppVersionName(context: Context): String? {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName
    } catch (e: PackageManager.NameNotFoundException) {
        e.printStackTrace()
        null
    }
}

infix fun <T> List<T>.elementEquals(other: List<T>): Boolean {
    if (this.size != other.size) return false

    val tracker = BooleanArray(this.size)
    var counter = 0

    root@ for (value in this) {
        destination@ for ((i, o) in other.withIndex()) {
            if (tracker[i]) {
                continue@destination
            } else if (value?.equals(o) == true) {
                counter++
                tracker[i] = true
                continue@root
            }
        }
    }

    return counter == this.size
}


fun debug(text: String?, tag: String = "test") {
    Log.d(tag, text ?: "null")
}

inline fun catchError(tag: String = "", onError: () -> Unit = {}, block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        showError(e, tag)
    }
}

fun getCurrentDate(reverseDays: Long = 0): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    return formatter.format(Date(System.currentTimeMillis() - (reverseDays * 86400 * 1000)))
}

fun getCurrentTime(): String {
    val currentTime = Date()
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    return formatter.format(currentTime)
}


fun readRawFile(id: Int) = app.resources.openRawResource(id).readBytes()


fun showError(e: Throwable, tag: String = "") {
    Log.e(
        "error",
        "${tag}发生错误: ${e.message} ${e.stackTraceToString()}"
    )
}


fun getIpAddressInLocalNetwork(): String {
    return NetworkInterface.getNetworkInterfaces()?.asSequence()
        ?.filter { it.isUp }
        ?.flatMap { it.inetAddresses.asSequence() }
        ?.find { addr ->
            addr is Inet4Address &&
                    addr.isSiteLocalAddress &&
                    addr.hostAddress != "127.0.0.1"
        }?.hostAddress ?: "127.0.0.1"
}

inline fun errorDialog(title: String, block: () -> Unit) {
    try {
        block()
    } catch (e: Exception) {
        when (e) {
            is CancellationException,
            is EOFException,
                -> return
        }
        appScope.launch(Dispatchers.Main) {
            showErrorDialog(e, title)
        }
    }
}

fun formatTime(date: Date, format: String = "yyyy-MM-dd HH:mm:ss"): String {
    val formatter = SimpleDateFormat(format, Locale.US)
    return formatter.format(date)
}

fun Uri.getFileName(): String {
    var fileName = ""
    app.contentResolver.query(this, null, null, null, null)?.use {
        val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        it.moveToFirst()
        fileName = it.getString(nameIndex)
    }
    return fileName
}

fun Uri.getFileSize() =
    app.contentResolver.openAssetFileDescriptor(this, "r")?.use { it.length } ?: 0