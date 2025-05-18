package com.donut.miximage.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.donut.miximage.MainActivity.Companion.mixFileSelector
import com.donut.miximage.R
import com.donut.miximage.app
import com.donut.miximage.appScope
import com.donut.miximage.currentActivity
import com.donut.miximage.ui.theme.MainTheme
import com.donut.miximage.ui.theme.colorScheme
import com.donut.miximage.utils.ImageScrambler
import com.donut.miximage.utils.compose.AsyncEffect
import com.donut.miximage.utils.compose.common.MixDialogBuilder
import com.donut.miximage.utils.getFileName
import com.donut.miximage.utils.objects.ProgressContent
import com.donut.miximage.utils.saveBitmapToStorage
import com.donut.miximage.utils.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.engawapg.lib.zoomable.rememberZoomState
import net.engawapg.lib.zoomable.zoomable
import java.io.File
import java.io.FileOutputStream

@Composable
fun Home() {
    MainTheme {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    fontSize = 20.sp,
                    modifier = Modifier.padding(10.dp)
                )
                Button(
                    {
                        selectImgAndEncrypt()
                    }, modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp, 0.dp, 10.dp, 0.dp)
                ) {
                    Text("选择图片")
                }
                Text(
                    text = "https://github.com/invertgeek/MixImage",
                    color = colorScheme.primary,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clickable {
                            MixDialogBuilder("打开链接?").apply {
                                setDefaultNegative()
                                setPositiveButton("打开") {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        "https://github.com/invertgeek/MixImage".toUri()
                                    )
                                    closeDialog()
                                    currentActivity.startActivity(intent)
                                }
                                show()
                            }
                        }
                )
            }
        }
    }

}

fun openImg(fileUri: Uri) {
    MixDialogBuilder("选择操作").apply {
        setPositiveButton("加密图片") {
            encodeImg(fileUri)
            closeDialog()
        }
        setNegativeButton("解密图片") {
            decodeImg(fileUri)
            closeDialog()
        }
        show()
    }
}

@Composable
fun ErrorMessage(msg: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(200.dp, 600.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = msg,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Red
        )
    }
}


fun showImgDialog(image: Bitmap, fileName: String) {
    MixDialogBuilder("查看图片").apply {
        setContent {
            val zoomState = rememberZoomState()
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .padding(10.dp)
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(image)
                        .crossfade(true)
                        .build(),
                    error = {
                        ErrorMessage(msg = "图片加载失败")
                    },
                    contentDescription = "图片",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(400.dp, 1000.dp)
                        .zoomable(zoomState)

                )
            }
        }
        setPositiveButton("分享图片") {
            appScope.launch(Dispatchers.IO) {
                shareBitmap(image)
            }
        }
        setNegativeButton("保存图片") {
            saveBitmapToStorage(image, fileName)
            showToast("保存成功")
        }
        show()
    }
}

// 枚举类来区分加密和解密操作
private enum class ImageOperation {
    ENCODE, DECODE
}

// 提取公共的图片处理逻辑
private fun processImage(
    imageUri: Uri,
    operation: ImageOperation,
    dialogTitle: String,
    fileNameSuffix: String,
    onResult: (Bitmap, String) -> Unit
) {
    MixDialogBuilder(
        dialogTitle,
        autoClose = false
    ).apply {
        setContent {
            val progress = remember { ProgressContent(dialogTitle) }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                progress.LoadingContent()
            }
            AsyncEffect {
                val stream = app.contentResolver.openInputStream(imageUri)
                val image = stream.use { BitmapFactory.decodeStream(it) }
                val fileName = "${imageUri.getFileName().substringBeforeLast(".")}$fileNameSuffix"
                val processedImage = when (operation) {
                    ImageOperation.ENCODE -> ImageScrambler.scrambleImage(image, progress)
                    ImageOperation.DECODE -> ImageScrambler.unscrambleImage(image, progress)
                }
                withContext(Dispatchers.Main) {
                    closeDialog()
                    onResult(processedImage, fileName)
                }
            }
        }
        setDefaultNegative("取消")
        show()
    }
}

// 重构后的 encodeImg
fun encodeImg(imageUri: Uri) {
    processImage(
        imageUri = imageUri,
        operation = ImageOperation.ENCODE,
        dialogTitle = "加密中",
        fileNameSuffix = "-Mixed",
        onResult = ::showImgDialog
    )
}

// 重构后的 decodeImg
fun decodeImg(imageUri: Uri) {
    processImage(
        imageUri = imageUri,
        operation = ImageOperation.DECODE,
        dialogTitle = "解密中",
        fileNameSuffix = "-Decoded",
        onResult = ::showImgDialog
    )
}


fun selectImgAndEncrypt() {
    appScope.launch(Dispatchers.IO) {
        mixFileSelector.openSelect(arrayOf("image/*")) { file ->
            val imageUri = file.firstOrNull()
            if (imageUri == null) {
                return@openSelect
            }
            openImg(imageUri)
        }
    }
}

fun shareBitmap(bitmap: Bitmap, shareTitle: String = "分享图片", fileName: String = shareTitle) {
    try {
        val context = currentActivity
        // 1. 将 Bitmap 保存到缓存目录
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "${fileName}.jpg")

        // 将 Bitmap 写入文件
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
            out.flush()
        }

        // 2. 获取文件 URI，使用 FileProvider
        val imageUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider", // 需要配置 FileProvider
            file
        )

        // 3. 创建分享 Intent
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // 4. 启动分享选择器
        context.startActivity(Intent.createChooser(shareIntent, shareTitle))

    } catch (e: Exception) {
        e.printStackTrace()
        // 这里可以添加错误处理，比如显示 Toast
    }
}