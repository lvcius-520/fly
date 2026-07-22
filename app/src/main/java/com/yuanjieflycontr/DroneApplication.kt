package com.yuanjieflycontr

import android.content.Context
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication
import android.app.Application
import com.amap.api.maps.MapsInitializer
import com.secneo.sdk.Helper

class DroneApplication : MultiDexApplication() {
    companion object {
        var installStatus: String = "未初始化"
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
        try {
            Helper.install(this)
            installStatus = "SDK Helper 加载成功"
        } catch (e: Throwable) {
            e.printStackTrace()
            installStatus = "Helper加载失败[${e.javaClass.simpleName}]: ${e.message}"
        }
    }

    override fun onCreate() {
        super.onCreate()
        // 高德地图隐私合规检查 (必须在地图初始化前调用)
        try {
            MapsInitializer.updatePrivacyShow(this, true, true)
            MapsInitializer.updatePrivacyAgree(this, true)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}