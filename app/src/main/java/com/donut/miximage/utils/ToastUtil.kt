package com.donut.mixfile.util

import android.content.Context
import android.widget.Toast

import com.donut.miximage.app
import com.donut.miximage.appScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


var toast: Toast? = null

fun showToast(msg: String, context: Context = app, length: Int = Toast.LENGTH_LONG) {
    appScope.launch(Dispatchers.Main) {
        toast?.cancel()
        toast = Toast.makeText(context, msg, length).apply {
            show()
        }
    }
}
