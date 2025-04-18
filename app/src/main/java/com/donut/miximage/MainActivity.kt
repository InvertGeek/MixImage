package com.donut.miximage

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.donut.miximage.ui.Home
import com.donut.miximage.ui.openImg
import com.donut.miximage.utils.objects.MixActivity
import com.donut.miximage.utils.objects.MixFileSelector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MainActivity : MixActivity("main") {

    companion object {
        lateinit var mixFileSelector: MixFileSelector

    }

    override fun onDestroy() {
        super.onDestroy()
        mixFileSelector.unregister()
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mixFileSelector = MixFileSelector(this)
        enableEdgeToEdge()
        setContent {
            Home()
        }
        appScope.launch(Dispatchers.Main) {
            handleIntent()
        }
    }

    override fun onNewIntent(intent: Intent) {
        setIntent(intent)
        handleIntent()
        super.onNewIntent(intent)
    }

    private fun handleIntent() {
        val action = intent.action
        intent.type ?: return
        when (action) {
            Intent.ACTION_SEND -> {
                val fileUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (fileUri != null) {
                    openImg(fileUri)
                }
            }
        }
    }

}



