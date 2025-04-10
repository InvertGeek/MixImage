package com.donut.miximage


import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Looper
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.decode.VideoFrameDecoder
import com.donut.miximage.utils.compose.showErrorDialog
import com.donut.miximage.utils.objects.MixActivity
import com.donut.miximage.utils.showError
import kotlinx.coroutines.MainScope
import okhttp3.OkHttpClient


val appScope by lazy { MainScope() }


private lateinit var innerApp: Application


val currentActivity: MixActivity
    get() {
        return MixActivity.firstActiveActivity()!!
    }

val app: Application
    get() = innerApp


class App : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            showError(e)
            if (Looper.myLooper() == null) {
                return@setDefaultUncaughtExceptionHandler
            }
            showErrorDialog(e)
        }
        innerApp = this
    }

    override fun newImageLoader(): ImageLoader {
        return genImageLoader(this)
    }


}

fun genImageLoader(
    context: Context,
    initializer: () -> OkHttpClient = { OkHttpClient() },
    sourceListener: (ByteArray) -> Unit = {},
): ImageLoader {
    return ImageLoader.Builder(context).components {
        add { result, _, _ ->
            val source = result.source.source()
            sourceListener(source.peek().readByteArray())
            null
        }
        if (Build.VERSION.SDK_INT >= 28) {
            add(ImageDecoderDecoder.Factory())
        } else {
            add(GifDecoder.Factory())
        }
        add(SvgDecoder.Factory())
        add(VideoFrameDecoder.Factory())
    }.okHttpClient(initializer)
        .crossfade(true).build()
}