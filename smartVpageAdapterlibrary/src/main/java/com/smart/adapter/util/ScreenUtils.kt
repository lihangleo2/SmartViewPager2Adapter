package com.smart.adapter.util

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowManager

/**
 * @Author leo
 * @Date 2023/10/7
 */
object ScreenUtils {

    @JvmStatic
    fun getScreenWidth(context: Context): Int {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            ?: return -1
        val point = Point()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            wm.defaultDisplay.getRealSize(point)
        } else {
            wm.defaultDisplay.getSize(point)
        }
        return point.x
    }
}