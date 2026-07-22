@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.yuanjieflycontr

import android.Manifest
import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import android.view.TextureView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint
import com.yuanjieflycontr.ml.DetResult
import com.yuanjieflycontr.ml.ObjectDetector
import com.yuanjieflycontr.ml.YoloNcnn
import com.yuanjieflycontr.ml.YoloTFLite
import dji.sdk.media.FetchMediaTask
import dji.sdk.media.FetchMediaTaskContent
import dji.sdk.media.FetchMediaTaskScheduler
import dji.sdk.media.MediaFile
import dji.sdk.media.MediaManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.*
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.*
import dji.common.camera.SettingsDefinitions
import dji.common.error.DJIError
import dji.common.gimbal.Rotation
import dji.common.gimbal.RotationMode
import dji.common.mission.waypoint.*
import dji.common.realname.AircraftBindingState
import dji.common.realname.AppActivationState
import dji.common.useraccount.UserAccountState
import dji.common.util.CommonCallbacks
import dji.sdk.base.BaseComponent
import dji.sdk.base.BaseProduct
import dji.sdk.camera.VideoFeeder
import dji.sdk.codec.DJICodecManager
import dji.sdk.flightcontroller.FlightController
import dji.sdk.mission.waypoint.WaypointMissionOperator
import dji.sdk.products.Aircraft
import dji.sdk.realname.AppActivationManager
import dji.sdk.sdkmanager.DJISDKInitEvent
import dji.sdk.sdkmanager.DJISDKManager
import dji.sdk.useraccount.UserAccountManager

class MainActivity : ComponentActivity() {
    private var flightController: FlightController? = null
    private var permissionsGranted = false
    private var connected by mutableStateOf(false)
    private var ready by mutableStateOf(false)
    private var msg by mutableStateOf("")

    private var aircraftLat by mutableStateOf<Double?>(null)
    private var aircraftLon by mutableStateOf<Double?>(null)
    private var aircraftAltMeters by mutableFloatStateOf(Float.NaN)
    private var aircraftSpeedMS by mutableFloatStateOf(0f)
    private var aircraftVSpeedMS by mutableFloatStateOf(0f)

    // Drone Status State
    private var batteryPercent by mutableIntStateOf(0)
    private var satelliteCount by mutableIntStateOf(0)
    private var flightMode by mutableStateOf("N/A")
    private var uplinkSignal by mutableIntStateOf(0) // 0-100
    private val waypoints = mutableStateListOf<RoutePoint>()
    private var mapType by mutableIntStateOf(AMap.MAP_TYPE_NORMAL)

    private var waypointOperator: WaypointMissionOperator? = null
    private var missionUploaded by mutableStateOf(false)
    private var missionRunning by mutableStateOf(false)

    private var cameraStorageLocation by mutableStateOf(SettingsDefinitions.StorageLocation.SDCARD)
    private var isRecording by mutableStateOf(false)
    private var recordingStartMs by mutableLongStateOf(0L)

    private var activationManager: AppActivationManager? = null
    private var activationStateText by mutableStateOf("")
    private var bindingStateText by mutableStateOf("")
    private var accountStateText by mutableStateOf("")
    private var activationStateListener: AppActivationState.AppActivationStateListener? = null
    private var bindingStateListener: AircraftBindingState.AircraftBindingStateListener? = null

    // FPV & Camera & Gimbal
    private var codecManager: DJICodecManager? = null
    private val videoDataListener = VideoFeeder.VideoDataListener { videoBuffer, size ->
        codecManager?.sendDataToDecoder(videoBuffer, size)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val allGranted = result.values.all { it }
        permissionsGranted = allGranted
        if (allGranted) registerDJI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Force Landscape
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        // Keep screen on
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Immersive Mode
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        
        requestRuntimePermissions()
        setContent {
            androidx.compose.material3.MaterialTheme {
                MainScreen(
                    connected = connected,
                    ready = ready,
                    activationStateText = activationStateText,
                    bindingStateText = bindingStateText,
                    accountStateText = accountStateText,
                    msg = msg,
                    aircraftLat = aircraftLat,
                    aircraftLon = aircraftLon,
                    aircraftAltMeters = aircraftAltMeters,
                    aircraftSpeedMS = aircraftSpeedMS,
                    aircraftVSpeedMS = aircraftVSpeedMS,
                    waypoints = waypoints,
                    mapType = mapType,
                    batteryPercent = batteryPercent,
                    satelliteCount = satelliteCount,
                    flightMode = flightMode,
                    uplinkSignal = uplinkSignal,
                    onMapTypeChange = { type -> mapType = type },
                    onTakeoffClick = { startTakeoff() },
                    onLandClick = { startLanding() },
                    onRegisterClick = { registerDJI() },
                    onLoginClick = { loginAccount() },
                    onLogoutClick = { logoutAccount() },
                    onSurfaceAvailable = { surface, w, h -> initVideoFeed(surface, w, h) },
                    onSurfaceDestroyed = { _: SurfaceTexture -> uninitVideoFeed() },
                    onTakePhoto = { takePhoto() },
                    onStartRecord = { startRecord() },
                    onStopRecord = { stopRecord() },
                    onGimbalPitchChange = { pitch -> rotateGimbal(pitch) },
                    cameraStorageLocation = cameraStorageLocation,
                    isRecording = isRecording,
                    recordingStartMs = recordingStartMs,
                    onCameraStorageLocationChange = { updateCameraStorageLocation(it) },
                    missionUploaded = missionUploaded,
                    missionRunning = missionRunning,
                    onTakeoffAndStartMission = { takeoffAndStartMission() },
                    onPauseMission = { pauseWaypointMission() },
                    onResumeMission = { resumeWaypointMission() },
                    onUploadRoute = { points -> startWaypointMission(points) }
                )
            }
        }
    }

    private fun requestRuntimePermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT <= 32) {
            perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            perms.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= 31) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    private fun registerDJI() {
        try {
            val sdkStatus = DroneApplication.installStatus
            msg = "尝试注册中... (Helper: $sdkStatus)"
            
            DJISDKManager.getInstance().registerApp(applicationContext, object : DJISDKManager.SDKManagerCallback {
                override fun onRegister(djiError: DJIError?) {
                    if (djiError == null || djiError.errorCode == 0) {
                        DJISDKManager.getInstance().startConnectionToProduct()
                        runOnUiThread { msg = "注册成功 (SDK: $sdkStatus)" }
                        attachActivationAndBindingListeners()
                        loginAccount()
                    } else {
                        runOnUiThread { msg = "注册失败: ${djiError.description} (SDK: $sdkStatus)" }
                    }
                }

                override fun onProductConnect(baseProduct: BaseProduct?) {
                    runOnUiThread {
                        connected = baseProduct != null
                        updateFlightController()
                        initBatteryListener()
                        initSignalListener()
                        updateCameraSystemState()
                        ready = flightController != null
                        msg = "设备已连接: ${baseProduct?.model?.displayName ?: "未知型号"}"
                    }
                }

                override fun onProductDisconnect() {
                    runOnUiThread {
                        connected = false
                        flightController = null
                        ready = false
                    }
                }

                override fun onProductChanged(baseProduct: BaseProduct?) {
                    runOnUiThread {
                        connected = baseProduct != null
                        updateFlightController()
                        initBatteryListener()
                        initSignalListener()
                        updateCameraSystemState()
                        ready = flightController != null
                    }
                }

                override fun onComponentChange(
                    key: BaseProduct.ComponentKey?,
                    oldComponent: BaseComponent?,
                    newComponent: BaseComponent?
                ) { }

                override fun onInitProcess(event: DJISDKInitEvent?, totalProcess: Int) { }

                override fun onDatabaseDownloadProgress(current: Long, total: Long) { }
            })
        } catch (t: Throwable) {
            val status = DroneApplication.installStatus
            msg = "SDK加载异常: ${t.message}\nHelper状态: $status"
        }
    }

    private fun attachActivationAndBindingListeners() {
        try {
            val mgr = DJISDKManager.getInstance().getAppActivationManager()
            activationManager = mgr
            runOnUiThread {
                activationStateText = "激活状态: ${mgr.appActivationState}"
                bindingStateText = "绑定状态: ${mgr.aircraftBindingState}"
            }

            val aListener = object : AppActivationState.AppActivationStateListener {
                override fun onUpdate(appActivationState: AppActivationState) {
                    runOnUiThread { activationStateText = "激活状态: $appActivationState" }
                }
            }
            activationStateListener = aListener
            mgr.addAppActivationStateListener(aListener)

            val bListener = object : AircraftBindingState.AircraftBindingStateListener {
                override fun onUpdate(aircraftBindingState: AircraftBindingState) {
                    runOnUiThread { bindingStateText = "绑定状态: $aircraftBindingState" }
                }
            }
            bindingStateListener = bListener
            mgr.addAircraftBindingStateListener(bListener)
        } catch (t: Throwable) {
            runOnUiThread { msg = "激活/绑定监听失败: ${t.message}" }
        }
    }

    private fun detachActivationAndBindingListeners() {
        try {
            activationStateListener?.let { activationManager?.removeAppActivationStateListener(it) }
            bindingStateListener?.let { activationManager?.removeAircraftBindingStateListener(it) }
            activationStateListener = null
            bindingStateListener = null
        } catch (_: Throwable) { }
    }

    private fun loginAccount() {
        try {
            UserAccountManager.getInstance().logIntoDJIUserAccount(
                this,
                object : CommonCallbacks.CompletionCallbackWith<UserAccountState> {
                    override fun onSuccess(userAccountState: UserAccountState) {
                        runOnUiThread {
                            accountStateText = "账号状态: $userAccountState"
                            msg = "账号登录成功"
                        }
                    }

                    override fun onFailure(error: DJIError) {
                        runOnUiThread { msg = "账号登录失败: ${error.description}" }
                    }
                }
            )
        } catch (t: Throwable) {
            runOnUiThread { msg = "触发登录失败: ${t.message}" }
        }
    }

    private fun logoutAccount() {
        try {
            UserAccountManager.getInstance().logoutOfDJIUserAccount(object : CommonCallbacks.CompletionCallback<DJIError> {
                override fun onResult(djiError: DJIError?) {
                    runOnUiThread {
                        if (djiError == null || djiError.errorCode == 0) {
                            accountStateText = "账号状态: 已退出"
                            msg = "账号已退出"
                        } else {
                            msg = "退出账号失败: ${djiError?.description ?: "未知错误"}"
                        }
                    }
                }
            })
        } catch (t: Throwable) {
            runOnUiThread { msg = "触发退出失败: ${t.message}" }
        }
    }

    private fun updateFlightController() {
        try {
            val product = DJISDKManager.getInstance().product
            val aircraft = product as? Aircraft
            flightController = aircraft?.flightController

            flightController?.setStateCallback { state ->
                val loc = state.aircraftLocation
                if (!loc.latitude.isNaN() && !loc.longitude.isNaN()) {
                    aircraftLat = loc.latitude
                    aircraftLon = loc.longitude
                }
                aircraftAltMeters = loc.altitude.toFloat()
                val vx = state.velocityX
                val vy = state.velocityY
                val vz = state.velocityZ
                aircraftSpeedMS = sqrt(vx * vx + vy * vy)
                aircraftVSpeedMS = vz

                // Update flight status
                satelliteCount = state.satelliteCount
                flightMode = state.flightMode.name
            }
        } catch (t: Throwable) {
            msg = "获取飞控失败: ${t.message}"
        }
    }

    private fun initBatteryListener() {
        try {
            val battery = DJISDKManager.getInstance().product?.battery
            battery?.setStateCallback { state ->
                batteryPercent = state.chargeRemainingInPercent
            }
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun initSignalListener() {
        try {
             val airLink = DJISDKManager.getInstance().product?.airLink
             // Simplified signal check, varies by model (OcuSync/Lightbridge/WiFi)
             // Using a generic approach if possible or just skipping if too complex for now
             // For now, we'll just mock it or try to get uplink signal from AirLink if available
             // Note: Specific signal quality listeners depend on the link type.
             // We will leave it as 0 if not easily accessible without model check.
             
             // Attempt to get OcuSync link
             airLink?.ocuSyncLink?.setDownlinkSignalQualityCallback { quality ->
                 uplinkSignal = quality // 0-100
             }
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun updateCameraSystemState() {
        val camera = DJISDKManager.getInstance().product?.camera ?: return
        try {
            camera.setSystemStateCallback { state ->
                runOnUiThread {
                    val recording = state.isRecording
                    isRecording = recording
                    if (recording) {
                        val seconds = state.currentVideoRecordingTimeInSeconds.toLong().coerceAtLeast(0L)
                        recordingStartMs = System.currentTimeMillis() - seconds * 1000L
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun startTakeoff() {
        try {
            flightController?.startTakeoff(object : CommonCallbacks.CompletionCallback<DJIError> {
                override fun onResult(djiError: DJIError?) {
                    msg = djiError?.description ?: "起飞命令已发送"
                }
            })
        } catch (e: Exception) {
            msg = "起飞异常: ${e.message}"
        }
    }

    private fun startLanding() {
        try {
            flightController?.startLanding(object : CommonCallbacks.CompletionCallback<DJIError> {
                override fun onResult(djiError: DJIError?) {
                    msg = djiError?.description ?: "降落命令已发送"
                }
            })
        } catch (e: Exception) {
            msg = "降落异常: ${e.message}"
        }
    }

    private fun takeoffAndStartMission() {
        if (!missionUploaded) {
            msg = "请先上传航线"
            return
        }
        try {
            flightController?.startTakeoff(object : CommonCallbacks.CompletionCallback<DJIError> {
                override fun onResult(djiError: DJIError?) {
                    runOnUiThread {
                        msg = djiError?.description ?: "起飞命令已发送"
                        startUploadedWaypointMission()
                    }
                }
            }) ?: run {
                msg = "飞控未就绪，无法起飞"
            }
        } catch (e: Exception) {
            msg = "起飞异常: ${e.message}"
            startUploadedWaypointMission()
        }
    }

    private fun startUploadedWaypointMission() {
        val operator = waypointOperator
        if (operator == null) {
            msg = "航线未准备好，请先上传"
            return
        }
        operator.startMission(object : CommonCallbacks.CompletionCallback<DJIError> {
            override fun onResult(djiError: DJIError?) {
                runOnUiThread {
                    if (djiError == null) {
                        missionRunning = true
                        msg = "航线任务已启动"
                    } else {
                        msg = "启动航线任务失败: ${djiError.description}"
                    }
                }
            }
        })
    }

    private fun pauseWaypointMission() {
        val operator = waypointOperator
        if (operator == null) {
            msg = "暂无航线任务"
            return
        }
        operator.pauseMission(object : CommonCallbacks.CompletionCallback<DJIError> {
            override fun onResult(djiError: DJIError?) {
                runOnUiThread {
                    if (djiError == null) missionRunning = false
                    msg = djiError?.description ?: "已暂停航线"
                }
            }
        })
    }

    private fun resumeWaypointMission() {
        val operator = waypointOperator
        if (operator == null) {
            msg = "暂无航线任务"
            return
        }
        operator.resumeMission(object : CommonCallbacks.CompletionCallback<DJIError> {
            override fun onResult(djiError: DJIError?) {
                runOnUiThread {
                    if (djiError == null) missionRunning = true
                    msg = djiError?.description ?: "已继续航线"
                }
            }
        })
    }

    private fun initVideoFeed(surface: SurfaceTexture, width: Int, height: Int) {
        try {
            if (codecManager == null) {
                codecManager = DJICodecManager(this, surface, width, height)
            }
            VideoFeeder.getInstance()?.primaryVideoFeed?.addVideoDataListener(videoDataListener)
        } catch (e: Exception) {
            msg = "视频流初始化失败: ${e.message}"
        }
    }

    private fun uninitVideoFeed() {
        try {
            codecManager?.cleanSurface()
            codecManager?.destroyCodec()
            codecManager = null
            VideoFeeder.getInstance()?.primaryVideoFeed?.removeVideoDataListener(videoDataListener)
        } catch (e: Exception) {
            // Ignore
        }
    }

    private fun updateCameraStorageLocation(location: SettingsDefinitions.StorageLocation) {
        cameraStorageLocation = location
        val camera = DJISDKManager.getInstance().product?.camera ?: return
        try {
            camera.setStorageLocation(location) { e ->
                runOnUiThread {
                    if (e == null) {
                        msg = if (location == SettingsDefinitions.StorageLocation.SDCARD) "已切换到SD卡存储" else "已切换到机身存储"
                    } else {
                        msg = "切换存储失败: ${e.description}"
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun postMsg(text: String) {
        runOnUiThread { msg = text }
    }

    private fun isUnsupportedByFirmware(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val t = text.lowercase()
        return t.contains("not supported") ||
            t.contains("unsupported") ||
            t.contains("firmware") ||
            t.contains("当前固件") ||
            t.contains("不支持")
    }

    private fun prepareCameraMode(
        camera: dji.sdk.camera.Camera,
        mode: SettingsDefinitions.CameraMode,
        onReady: (String?) -> Unit
    ) {
        try {
            val doSetMode = {
                try {
                    camera.setMode(mode) { e ->
                        val desc = e?.description
                        onReady(if (isUnsupportedByFirmware(desc)) null else desc)
                    }
                } catch (t: Throwable) {
                    val desc = t.message
                    onReady(if (isUnsupportedByFirmware(desc)) null else (desc ?: "相机模式切换异常"))
                }
            }
            try {
                camera.exitPlayback { _ -> doSetMode() }
            } catch (_: Throwable) {
                doSetMode()
            }
        } catch (t: Throwable) {
            val desc = t.message
            onReady(if (isUnsupportedByFirmware(desc)) null else (desc ?: "相机模式切换异常"))
        }
    }

    private fun ensureCameraStorage(
        camera: dji.sdk.camera.Camera,
        preferred: SettingsDefinitions.StorageLocation,
        onReady: () -> Unit
    ) {
        try {
            camera.setStorageLocation(preferred) { e ->
                if (e == null) {
                    onReady()
                } else {
                    val fallback =
                        if (preferred == SettingsDefinitions.StorageLocation.SDCARD) SettingsDefinitions.StorageLocation.INTERNAL_STORAGE
                        else SettingsDefinitions.StorageLocation.SDCARD
                    try {
                        camera.setStorageLocation(fallback) { _ -> onReady() }
                    } catch (_: Throwable) {
                        onReady()
                    }
                }
            }
        } catch (_: Throwable) {
            onReady()
        }
    }

    private fun takePhoto() {
        val camera = DJISDKManager.getInstance().product?.camera
        if (camera != null) {
            try {
                postMsg("正在拍照...")
                prepareCameraMode(camera, SettingsDefinitions.CameraMode.SHOOT_PHOTO) { modeError ->
                    if (modeError != null) {
                        postMsg("切换拍照模式失败: $modeError，尝试直接拍照")
                    }
                    ensureCameraStorage(camera, cameraStorageLocation) {
                        camera.setShootPhotoMode(SettingsDefinitions.ShootPhotoMode.SINGLE) { error ->
                            if (error == null) {
                                camera.startShootPhoto { e ->
                                    postMsg(if (e == null) "拍照指令已发送" else "拍照失败: ${e.description}")
                                }
                            } else {
                                postMsg("设置拍照模式失败: ${error.description}")
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                postMsg("拍照异常: ${t.message}")
            }
        } else {
            postMsg("相机未连接")
        }
    }

    private fun startRecord() {
        val camera = DJISDKManager.getInstance().product?.camera
        if (camera != null) {
            try {
                if (isRecording) return
                postMsg("正在开始录像...")
                prepareCameraMode(camera, SettingsDefinitions.CameraMode.RECORD_VIDEO) { modeError ->
                    if (modeError != null) {
                        postMsg("切换录像模式失败: $modeError，尝试直接开始录像")
                    }
                    ensureCameraStorage(camera, cameraStorageLocation) {
                        camera.startRecordVideo { e ->
                            runOnUiThread {
                                if (e == null) {
                                    isRecording = true
                                    recordingStartMs = System.currentTimeMillis()
                                    msg = "开始录像"
                                } else {
                                    msg = "开始录像失败: ${e.description}"
                                }
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                postMsg("开始录像异常: ${t.message}")
            }
        } else {
            postMsg("相机未连接")
        }
    }

    private fun stopRecord() {
        val camera = DJISDKManager.getInstance().product?.camera
        if (camera != null) {
            postMsg("正在停止录像...")
            camera.stopRecordVideo { e ->
                runOnUiThread {
                    if (e == null) {
                        isRecording = false
                        msg = "停止录像"
                    } else {
                        msg = "停止录像失败: ${e.description}"
                    }
                }
            }
        } else {
            msg = "相机未连接"
        }
    }

    private fun rotateGimbal(pitch: Float) {
        val gimbal = DJISDKManager.getInstance().product?.gimbal
        if (gimbal != null) {
            val rotation = Rotation.Builder()
                .mode(RotationMode.ABSOLUTE_ANGLE)
                .pitch(pitch)
                .time(0.5)
                .build()
            gimbal.rotate(rotation) { e -> }
        }
    }

    private fun startWaypointMission(points: List<RoutePoint>) {
        try {
            if (points.size < 2) {
                msg = "至少需要两个航点"
                return
            }
            if (satelliteCount in 0..7) {
                msg = "卫星数不足（$satelliteCount），请等待定位稳定后再上传航线"
                return
            }

            val aircraft = DJISDKManager.getInstance().product as? Aircraft
            if (aircraft == null) {
                msg = "未连接飞机，无法执行航线"
                return
            }

            val missionControl = DJISDKManager.getInstance().missionControl
            if (missionControl == null) {
                msg = "任务控制系统未就绪 (MissionControl is null)"
                return
            }

            val operator = missionControl.waypointMissionOperator
            if (operator == null) {
                msg = "航点任务操作器不可用 (Operator is null)"
                return
            }

            missionUploaded = false
            missionRunning = false
            waypointOperator = operator

            val defaultSpeed = points.firstOrNull()?.speedMetersPerSecond?.coerceIn(1f, 15f) ?: 5f
            val maxSpeed = (defaultSpeed + 5f).coerceIn(defaultSpeed, 15f)

            val djiWaypoints = points.map { p ->
                Waypoint(p.lat, p.lon, p.altitudeMeters.coerceIn(5f, 500f)).apply {
                    speed = p.speedMetersPerSecond.coerceIn(1f, 15f)
                }
            }

            val builder = WaypointMission.Builder()
                .finishedAction(WaypointMissionFinishedAction.GO_HOME)
                .headingMode(WaypointMissionHeadingMode.AUTO)
                .autoFlightSpeed(defaultSpeed)
                .maxFlightSpeed(maxSpeed)
                .flightPathMode(WaypointMissionFlightPathMode.NORMAL)
                .waypointList(djiWaypoints)
                .waypointCount(djiWaypoints.size)

            val mission = builder.build()

            val loadError = operator.loadMission(mission)
            if (loadError != null) {
                msg = "载入航线任务失败: ${loadError.description}(${loadError.errorCode})"
                return
            }

            operator.uploadMission { uploadError ->
                runOnUiThread {
                    try {
                        if (uploadError != null) {
                            missionUploaded = false
                            missionRunning = false
                            msg = "上传航线失败: ${uploadError.description}(${uploadError.errorCode})"
                        } else {
                            missionUploaded = true
                            missionRunning = false
                            msg = "航线上传成功，等待一键起飞后开始执行"
                        }
                    } catch (e: Exception) {
                        msg = "任务启动过程异常: ${e.message}"
                        e.printStackTrace()
                    }
                }
            }
        } catch (t: Throwable) {
            msg = "执行航线异常: ${t.message}"
            t.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        detachActivationAndBindingListeners()
    }
}

data class RoutePoint(
    val lat: Double,
    val lon: Double,
    val altitudeMeters: Float = 30f,
    val speedMetersPerSecond: Float = 5f
)

data class NoFlyZone(val lat: Double, val lon: Double, val radiusMeters: Float)

val DefaultNoFlyZones: List<NoFlyZone> = emptyList()

enum class TrajectoryPlanMode { Line, Area }

enum class AreaEditMode { Polygon, Takeoff, Landing }

enum class FlightMainView { Map, Fpv }

enum class Screen {
    Home,
    Flight,
    RoutePlan,
    Profile,
    Album,
    TrajectoryPlan
}

@Composable
fun MainScreen(
    onTakeoffClick: () -> Unit,
    onLandClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit,
    connected: Boolean,
    ready: Boolean,
    activationStateText: String,
    bindingStateText: String,
    accountStateText: String,
    msg: String,
    aircraftLat: Double?,
    aircraftLon: Double?,
    aircraftAltMeters: Float,
    aircraftSpeedMS: Float,
    aircraftVSpeedMS: Float,
    waypoints: SnapshotStateList<RoutePoint>,
    mapType: Int,
    batteryPercent: Int,
    satelliteCount: Int,
    flightMode: String,
    uplinkSignal: Int,
    cameraStorageLocation: SettingsDefinitions.StorageLocation,
    isRecording: Boolean,
    recordingStartMs: Long,
    onCameraStorageLocationChange: (SettingsDefinitions.StorageLocation) -> Unit,
    missionUploaded: Boolean,
    missionRunning: Boolean,
    onTakeoffAndStartMission: () -> Unit,
    onPauseMission: () -> Unit,
    onResumeMission: () -> Unit,
    onMapTypeChange: (Int) -> Unit,
    onUploadRoute: (List<RoutePoint>) -> Unit,
    onSurfaceAvailable: (SurfaceTexture, Int, Int) -> Unit,
    onSurfaceDestroyed: (SurfaceTexture) -> Unit,
    onTakePhoto: () -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onGimbalPitchChange: (Float) -> Unit
) {
    var currentScreen by remember { mutableStateOf(Screen.Home) }
    var hasAutoNavigatedToFlight by remember { mutableStateOf(false) }
    var activeRoute by remember { mutableStateOf<List<RoutePoint>>(emptyList()) }

    LaunchedEffect(connected) {
        if (connected && !hasAutoNavigatedToFlight) {
            currentScreen = Screen.Flight
            hasAutoNavigatedToFlight = true
        }
    }

    when (currentScreen) {
        Screen.Home -> {
            HomePage(
                connected = connected,
                onNavigate = { screen -> currentScreen = screen }
            )
        }
        Screen.Flight -> {
            FlightPage(
                connected = connected,
                msg = msg,
                aircraftLat = aircraftLat,
                aircraftLon = aircraftLon,
                aircraftAltMeters = aircraftAltMeters,
                aircraftSpeedMS = aircraftSpeedMS,
                aircraftVSpeedMS = aircraftVSpeedMS,
                mapType = mapType,
                batteryPercent = batteryPercent,
                uplinkSignal = uplinkSignal,
                flightMode = flightMode,
                satelliteCount = satelliteCount,
                route = activeRoute,
                cameraStorageLocation = cameraStorageLocation,
                isRecording = isRecording,
                recordingStartMs = recordingStartMs,
                onCameraStorageLocationChange = onCameraStorageLocationChange,
                onTakePhoto = onTakePhoto,
                onStartRecord = onStartRecord,
                onStopRecord = onStopRecord,
                missionUploaded = missionUploaded,
                missionRunning = missionRunning,
                onTakeoffAndStartMission = onTakeoffAndStartMission,
                onPauseMission = onPauseMission,
                onResumeMission = onResumeMission,
                onLand = onLandClick,
                onSurfaceAvailable = onSurfaceAvailable,
                onSurfaceDestroyed = onSurfaceDestroyed,
                onBack = { currentScreen = Screen.Home }
            )
        }
        Screen.RoutePlan -> {
            RoutePlanPage(
                aircraftLat = aircraftLat,
                aircraftLon = aircraftLon,
                waypoints = waypoints,
                mapType = mapType,
                batteryPercent = batteryPercent,
                satelliteCount = satelliteCount,
                flightMode = flightMode,
                uplinkSignal = uplinkSignal,
                onMapTypeChange = onMapTypeChange,
                onUploadAndStart = { points ->
                    activeRoute = points
                    onUploadRoute(points)
                    currentScreen = Screen.Flight
                },
                onBack = { currentScreen = Screen.Home }
            )
        }
        Screen.Profile -> {
            ProfilePage(onBack = { currentScreen = Screen.Home })
        }
        Screen.Album -> {
            AlbumPage(onBack = { currentScreen = Screen.Home })
        }
        Screen.TrajectoryPlan -> {
            TrajectoryPlanPage(
                connected = connected,
                aircraftLat = aircraftLat,
                aircraftLon = aircraftLon,
                mapType = mapType,
                onMapTypeChange = onMapTypeChange,
                onUploadAndStart = { points ->
                    activeRoute = points
                    onUploadRoute(points)
                    currentScreen = Screen.Flight
                },
                onBack = { currentScreen = Screen.Home }
            )
        }
    }
}

@Composable
fun HomePage(
    connected: Boolean,
    onNavigate: (Screen) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2C3E50),
                        Color(0xFF4CA1AF)
                    )
                )
            )
    ) {
        // Top Left Search Bar
        Row(
            modifier = Modifier
                .padding(top = 16.dp, start = 16.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.3f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .width(250.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("搜索课程、地点或攻略", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
        }

        // Left Content Column
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
        ) {
            // Safety Map Card
            Card(
                modifier = Modifier
                    .width(300.dp)
                    .height(140.dp)
                    .padding(bottom = 8.dp)
                    .clickable { onNavigate(Screen.RoutePlan) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("飞行安全地图", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("点击查看限飞区数据", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = Color.Green, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("飞行区", color = Color.White, fontSize = 14.sp)
                    }
                }
            }

            // Transfer and Album Row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Quick Transfer
                Card(
                    modifier = Modifier
                        .width(146.dp)
                        .height(100.dp)
                        .clickable { onNavigate(Screen.TrajectoryPlan) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Create, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("航迹规划", color = Color.White)
                    }
                }

                // Album
                Card(
                    modifier = Modifier
                        .width(146.dp)
                        .height(100.dp)
                        .clickable { onNavigate(Screen.Album) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.List, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("相册", color = Color.White)
                    }
                }
            }
        }

        // Bottom Left Tabs - Only "个人中心"
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 16.dp)
                .clickable { onNavigate(Screen.Profile) },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Person, contentDescription = "个人中心", tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("个人中心", color = Color.White, fontSize = 16.sp)
        }

        // Bottom Right Button
        Button(
            onClick = { if (connected) onNavigate(Screen.Flight) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
                .width(200.dp)
                .height(56.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (connected) Color(0xFF007AFF) else Color.White
            )
        ) {
            Text(
                text = if (connected) "开始飞行" else "连接引导",
                color = if (connected) Color.White else Color.Black,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ProfilePage(onBack: () -> Unit) {
    var isLoggedIn by remember { mutableStateOf(false) }
    var userName by remember { mutableStateOf("点击登录") }
    val flightHoursText = remember { "3.45 h" }
    val flightDistanceText = remember { "22.4 km" }
    val flightCountText = remember { "35" }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF2C3E50), Color(0xFF4CA1AF))
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text("我的", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
            }
            
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f))
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isLoggedIn) {
                                        isLoggedIn = true
                                        userName = "飞手用户"
                                    }
                                }
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(28.dp))
                                    .background(Color(0xFFE6E6E6)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = Color(0xFF8A8A8A),
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = if (isLoggedIn) userName else "点击登录  >",
                                color = Color.Black,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Divider(color = Color(0xFFEAEAEA))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 18.dp, horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text(flightHoursText, color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("飞行时长", color = Color(0xFF7A7A7A), fontSize = 12.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(34.dp)
                                    .background(Color(0xFFEAEAEA))
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text(flightDistanceText, color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("飞行里程", color = Color(0xFF7A7A7A), fontSize = 12.sp)
                            }
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(34.dp)
                                    .background(Color(0xFFEAEAEA))
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                Text(flightCountText, color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("飞行次数", color = Color(0xFF7A7A7A), fontSize = 12.sp)
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.95f))
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { }
                                .padding(horizontal = 20.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = null, tint = Color(0xFF4A4A4A))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("设备管理", color = Color.Black, fontSize = 16.sp)
                        }
                        Divider(color = Color(0xFFEAEAEA))
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumPage(onBack: () -> Unit) {
    data class DroneMediaItem(val file: MediaFile, val isVideo: Boolean)

    val scope = rememberCoroutineScope()
    var storageLocation by remember { mutableStateOf(SettingsDefinitions.StorageLocation.SDCARD) }
    var loading by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val items = remember { mutableStateListOf<DroneMediaItem>() }
    val thumbnails = remember { mutableStateMapOf<String, android.graphics.Bitmap>() }

    suspend fun ensureDownloadMode(camera: dji.sdk.camera.Camera): String? {
        return suspendCancellableCoroutine { cont ->
            try {
                val isFlatSupported = try {
                    camera.isFlatCameraModeSupported
                } catch (_: Throwable) {
                    false
                }

                if (isFlatSupported) {
                    camera.enterPlayback { e -> cont.resume(e?.description) }
                } else {
                    camera.setMode(SettingsDefinitions.CameraMode.MEDIA_DOWNLOAD) { e ->
                        if (e == null) {
                            cont.resume(null)
                        } else {
                            camera.enterPlayback { playbackError ->
                                cont.resume((playbackError ?: e).description)
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                cont.resume(t.message ?: "进入相册模式失败")
            }
        }
    }

    suspend fun refreshFileList(mediaManager: MediaManager, loc: SettingsDefinitions.StorageLocation): Pair<DJIError?, List<MediaFile>> {
        val refreshError = suspendCancellableCoroutine<DJIError?> { cont ->
            mediaManager.refreshFileListOfStorageLocation(loc) { e -> cont.resume(e) }
        }

        if (refreshError != null) return refreshError to emptyList()

        val list = when (loc) {
            SettingsDefinitions.StorageLocation.SDCARD -> mediaManager.getSDCardFileListSnapshot()
            SettingsDefinitions.StorageLocation.INTERNAL_STORAGE -> mediaManager.getInternalStorageFileListSnapshot()
            else -> mediaManager.getSDCardFileListSnapshot()
        }.orEmpty()

        val filtered = list
            .filter { it.mediaType != MediaFile.MediaType.PHOTO_FOLDER && it.mediaType != MediaFile.MediaType.VIDEO_FOLDER }
            .sortedByDescending { it.timeCreated }

        return null to filtered
    }

    fun loadDroneAlbum() {
        scope.launch {
            loading = true
            errorText = null
            items.clear()
            thumbnails.clear()

            val camera = DJISDKManager.getInstance().product?.camera
            if (camera == null) {
                loading = false
                errorText = "未连接无人机相机"
                return@launch
            }

            if (!camera.isMediaDownloadModeSupported) {
                loading = false
                errorText = "当前机型不支持相册读取"
                return@launch
            }

            val modeErrorText = ensureDownloadMode(camera)
            if (modeErrorText != null) {
                loading = false
                errorText = modeErrorText
                return@launch
            }

            val mediaManager = camera.mediaManager
            if (mediaManager == null) {
                loading = false
                errorText = "媒体管理器不可用"
                return@launch
            }

            val scheduler: FetchMediaTaskScheduler? = try {
                mediaManager.scheduler
            } catch (_: Throwable) {
                try {
                    mediaManager.getScheduler()
                } catch (_: Throwable) {
                    null
                }
            }
            try {
                scheduler?.resume { }
            } catch (_: Throwable) {
            }

            val (refreshError, list) = withContext(Dispatchers.IO) {
                refreshFileList(mediaManager, storageLocation)
            }
            if (refreshError != null) {
                loading = false
                errorText = refreshError.description
                return@launch
            }

            items.addAll(
                list.map { file ->
                    val mt = file.mediaType
                    val isVideo = mt == MediaFile.MediaType.MP4 || mt == MediaFile.MediaType.MOV
                    DroneMediaItem(file = file, isVideo = isVideo)
                }
            )
            loading = false
        }
    }

    fun requestThumbnail(mediaManager: MediaManager, file: MediaFile) {
        val key = file.fileName ?: return
        if (thumbnails.containsKey(key)) return

        val scheduler: FetchMediaTaskScheduler? = try {
            mediaManager.scheduler
        } catch (_: Throwable) {
            try {
                mediaManager.getScheduler()
            } catch (_: Throwable) {
                null
            }
        }

        val callback = object : FetchMediaTask.Callback {
            override fun onUpdate(mediaFile: MediaFile, content: FetchMediaTaskContent, error: DJIError?) {
                if (error != null) return
                if (content != FetchMediaTaskContent.THUMBNAIL) return
                val bmp = mediaFile.thumbnail ?: return
                thumbnails[key] = bmp
            }
        }

        try {
            scheduler?.moveTaskToEnd(FetchMediaTask(file, FetchMediaTaskContent.THUMBNAIL, callback))
        } catch (_: Throwable) {
        }
    }

    LaunchedEffect(storageLocation) {
        loadDroneAlbum()
    }

    DisposableEffect(Unit) {
        onDispose {
            val camera = DJISDKManager.getInstance().product?.camera ?: return@onDispose
            try {
                val isFlatSupported = try {
                    camera.isFlatCameraModeSupported
                } catch (_: Throwable) {
                    false
                }
                if (isFlatSupported) {
                    camera.exitPlayback { }
                } else {
                    camera.setMode(SettingsDefinitions.CameraMode.SHOOT_PHOTO) { }
                }
            } catch (_: Throwable) {
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.Home, contentDescription = "Back", tint = Color.White)
                }
                Text("无人机相册", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text("${items.size} 个文件", color = Color.White.copy(alpha = 0.7f))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { storageLocation = SettingsDefinitions.StorageLocation.SDCARD },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (storageLocation == SettingsDefinitions.StorageLocation.SDCARD) Color(0xFF2E7DFF) else Color(0xFF3A3A3A)
                    )
                ) {
                    Text("SD卡", color = Color.White)
                }
                Button(
                    onClick = { storageLocation = SettingsDefinitions.StorageLocation.INTERNAL_STORAGE },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (storageLocation == SettingsDefinitions.StorageLocation.INTERNAL_STORAGE) Color(0xFF2E7DFF) else Color(0xFF3A3A3A)
                    )
                ) {
                    Text("机身", color = Color.White)
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { loadDroneAlbum() }, enabled = !loading) {
                    Text(if (loading) "加载中..." else "刷新", color = Color.White)
                }
            }

            when {
                loading && items.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
                errorText != null && items.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = errorText ?: "加载失败", color = Color.White)
                    }
                }
                items.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "没有找到照片/视频", color = Color.White)
                    }
                }
                else -> {
                    val mediaManager = DJISDKManager.getInstance().product?.camera?.mediaManager
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items, key = { it.file.fileName ?: it.file.index.toString() }) { item ->
                            val key = item.file.fileName
                            val bmp = if (key != null) thumbnails[key] else null
                            if (bmp == null && key != null && mediaManager != null) {
                                LaunchedEffect(key) {
                                    requestThumbnail(mediaManager, item.file)
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.DarkGray)
                            ) {
                                if (bmp != null) {
                                    Image(
                                        bitmap = bmp.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (item.isVideo) Icons.Filled.PlayArrow else Icons.Filled.List,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(36.dp)
                                    )
                                }

                                if (item.isVideo) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(Color.Black.copy(alpha = 0.45f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Filled.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrajectoryPlanPage(
    connected: Boolean,
    aircraftLat: Double?,
    aircraftLon: Double?,
    mapType: Int,
    onMapTypeChange: (Int) -> Unit,
    onUploadAndStart: (List<RoutePoint>) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var aMap by remember { mutableStateOf<AMap?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }

    val noFlyZones = remember { mutableStateListOf<NoFlyZone>() }
    LaunchedEffect(Unit) {
        if (noFlyZones.isEmpty()) {
            noFlyZones.add(NoFlyZone(39.9042, 116.4074, 1000f))
            noFlyZones.add(NoFlyZone(22.5431, 114.0579, 1000f))
            if (aircraftLat != null && aircraftLon != null) {
                noFlyZones.add(NoFlyZone(aircraftLat + 0.002, aircraftLon + 0.002, 500f))
            }
        }
    }

    var planMode by remember { mutableStateOf(TrajectoryPlanMode.Line) }

    var startPoint by remember { mutableStateOf<RoutePoint?>(null) } // GPS84
    var endPoint by remember { mutableStateOf<RoutePoint?>(null) } // GPS84
    var startPointGcj by remember { mutableStateOf<LatLng?>(null) }
    var endPointGcj by remember { mutableStateOf<LatLng?>(null) }

    val areaPolygon = remember { mutableStateListOf<RoutePoint>() } // GPS84 vertices
    var areaClosed by remember { mutableStateOf(false) }
    var areaEditMode by remember { mutableStateOf(AreaEditMode.Polygon) }

    var takeoffPoint by remember { mutableStateOf<RoutePoint?>(null) } // GPS84
    var landingPoint by remember { mutableStateOf<RoutePoint?>(null) } // GPS84
    var takeoffPointGcj by remember { mutableStateOf<LatLng?>(null) }
    var landingPointGcj by remember { mutableStateOf<LatLng?>(null) }

    var plannedPath by remember { mutableStateOf<List<RoutePoint>>(emptyList()) }

    var altitudeText by remember { mutableStateOf("30") }
    var speedText by remember { mutableStateOf("5") }
    var gridSizeText by remember { mutableStateOf("40") }
    var safetyMarginText by remember { mutableStateOf("50") }
    var segmentLenText by remember { mutableStateOf("40") }
    var laneSpacingText by remember { mutableStateOf("40") }
    var laneAngleText by remember { mutableStateOf("0") }
    var autoAngle by remember { mutableStateOf(false) }
    var smoothPath by remember { mutableStateOf(true) }

    var planning by remember { mutableStateOf(false) }
    var planError by remember { mutableStateOf<String?>(null) }
    var usedGridMeters by remember { mutableStateOf<Double?>(null) }
    var showWaypoints by remember { mutableStateOf(false) }
    var showAllWaypoints by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    fun resetPlan() {
        startPoint = null
        endPoint = null
        startPointGcj = null
        endPointGcj = null
        areaPolygon.clear()
        areaClosed = false
        areaEditMode = AreaEditMode.Polygon
        takeoffPoint = null
        landingPoint = null
        takeoffPointGcj = null
        landingPointGcj = null
        plannedPath = emptyList()
        planError = null
        usedGridMeters = null
        showWaypoints = false
        showAllWaypoints = false
        autoAngle = false
    }

    fun requestPlan() {
        val altitude = altitudeText.toFloatOrNull()?.coerceIn(5f, 500f) ?: 30f
        val speed = speedText.toFloatOrNull()?.coerceIn(1f, 15f) ?: 5f
        val gridMeters = gridSizeText.toDoubleOrNull()?.coerceIn(10.0, 200.0) ?: 40.0
        val safetyMargin = safetyMarginText.toDoubleOrNull()?.coerceIn(0.0, 500.0) ?: 50.0
        val segmentLen = segmentLenText.toDoubleOrNull()?.coerceIn(10.0, 200.0) ?: 40.0
        val laneSpacing = laneSpacingText.toDoubleOrNull()?.coerceIn(10.0, 500.0) ?: 40.0
        val laneAngle = if (planMode == TrajectoryPlanMode.Area && autoAngle && areaPolygon.size >= 2) {
            val poly = areaPolygon.toList()
            val centerLat = poly.map { it.lat }.average()
            val centerLon = poly.map { it.lon }.average()
            val projector = LocalProjector(centerLat, centerLon)
            val polyXY = poly.map { p -> projector.toXYMeters(p.lat, p.lon) }
            var bestLen2 = -1.0
            var bestAngleDeg = 0.0
            for (i in polyXY.indices) {
                val a = polyXY[i]
                val b = polyXY[(i + 1) % polyXY.size]
                val dx = b.first - a.first
                val dy = b.second - a.second
                val len2 = dx * dx + dy * dy
                if (len2 > bestLen2) {
                    bestLen2 = len2
                    bestAngleDeg = Math.toDegrees(atan2(dy, dx))
                }
            }
            val angle = ((bestAngleDeg % 360.0) + 360.0) % 360.0
            laneAngleText = angle.format(1)
            angle
        } else {
            laneAngleText.toDoubleOrNull()?.let { ((it % 360.0) + 360.0) % 360.0 } ?: 0.0
        }

        planning = true
        planError = null
        usedGridMeters = null

        scope.launch {
            val result = withContext(Dispatchers.Default) {
                when (planMode) {
                    TrajectoryPlanMode.Line -> {
                        val s = startPoint
                        val e = endPoint
                        if (s == null || e == null) {
                            TrajectoryPlanResult(emptyList(), null, "请在地图上先选择起点和终点")
                        } else {
                            planTrajectoryAStar(
                                start = s,
                                end = e,
                                noFlyZones = noFlyZones,
                                gridSizeMeters = gridMeters,
                                safetyMarginMeters = safetyMargin,
                                segmentLengthMeters = segmentLen,
                                smooth = smoothPath
                            )
                        }
                    }
                    TrajectoryPlanMode.Area -> {
                        if (!areaClosed || areaPolygon.size < 3) {
                            TrajectoryPlanResult(emptyList(), null, "请先绘制并完成区域（至少3个点）")
                        } else {
                            val coverage = planTrajectoryAreaCoverage(
                                polygon = areaPolygon.toList(),
                                initialPoint = takeoffPoint,
                                noFlyZones = noFlyZones,
                                safetyMarginMeters = safetyMargin,
                                laneSpacingMeters = laneSpacing,
                                laneAngleDeg = laneAngle,
                                waypointSpacingMeters = segmentLen,
                                smooth = smoothPath
                            )
                            if (coverage.error != null || coverage.path.isEmpty()) {
                                coverage
                            } else {
                                val connectorGrid = segmentLen.coerceIn(10.0, 120.0)
                                var path = coverage.path
                                val t = takeoffPoint
                                if (t != null) {
                                    val pre = planTrajectoryAStar(
                                        start = t,
                                        end = path.first(),
                                        noFlyZones = noFlyZones,
                                        gridSizeMeters = connectorGrid,
                                        safetyMarginMeters = safetyMargin,
                                        segmentLengthMeters = segmentLen,
                                        smooth = smoothPath
                                    )
                                    if (pre.error == null && pre.path.isNotEmpty()) {
                                        path = (pre.path.dropLast(1) + path)
                                    }
                                }
                                val l = landingPoint
                                if (l != null) {
                                    val post = planTrajectoryAStar(
                                        start = path.last(),
                                        end = l,
                                        noFlyZones = noFlyZones,
                                        gridSizeMeters = connectorGrid,
                                        safetyMarginMeters = safetyMargin,
                                        segmentLengthMeters = segmentLen,
                                        smooth = smoothPath
                                    )
                                    if (post.error == null && post.path.isNotEmpty()) {
                                        path = (path + post.path.drop(1))
                                    }
                                }
                                TrajectoryPlanResult(path = path, usedGridSizeMeters = null, error = null)
                            }
                        }
                    }
                }
            }

            planning = false
            if (result.error != null) {
                plannedPath = emptyList()
                planError = result.error
                usedGridMeters = result.usedGridSizeMeters
                return@launch
            }

            usedGridMeters = result.usedGridSizeMeters
            plannedPath = result.path.map { p ->
                p.copy(altitudeMeters = altitude, speedMetersPerSecond = speed)
            }
        }
    }

    LaunchedEffect(aMap, mapType) {
        val map = aMap ?: return@LaunchedEffect
        if (map.mapType != mapType) map.mapType = mapType
    }

    LaunchedEffect(
        aMap,
        planMode,
        startPoint,
        endPoint,
        plannedPath,
        noFlyZones.size,
        usedGridMeters,
        areaPolygon.size,
        areaClosed,
        takeoffPoint,
        landingPoint
    ) {
        val map = aMap ?: return@LaunchedEffect
        map.clear(true)

        noFlyZones.forEach { zone ->
            val (gcjLat, gcjLon) = GpsUtils.gps84ToGcj02(zone.lat, zone.lon)
            map.addCircle(
                CircleOptions()
                    .center(LatLng(gcjLat, gcjLon))
                    .radius(zone.radiusMeters.toDouble())
                    .strokeColor(android.graphics.Color.RED)
                    .fillColor(0x30FF0000)
                    .strokeWidth(2f)
            )
        }

        startPoint?.let { s ->
            val (gcjLat, gcjLon) = GpsUtils.gps84ToGcj02(s.lat, s.lon)
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(gcjLat, gcjLon))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                    .title("起点")
                    .snippet(formatLatLng(s.lat, s.lon))
            )
        }

        endPoint?.let { e ->
            val (gcjLat, gcjLon) = GpsUtils.gps84ToGcj02(e.lat, e.lon)
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(gcjLat, gcjLon))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                    .title("终点")
                    .snippet(formatLatLng(e.lat, e.lon))
            )
        }

        takeoffPoint?.let { t ->
            val (gcjLat, gcjLon) = GpsUtils.gps84ToGcj02(t.lat, t.lon)
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(gcjLat, gcjLon))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                    .title("起飞点")
                    .snippet(formatLatLng(t.lat, t.lon))
            )
        }

        landingPoint?.let { l ->
            val (gcjLat, gcjLon) = GpsUtils.gps84ToGcj02(l.lat, l.lon)
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(gcjLat, gcjLon))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET))
                    .title("降落点")
                    .snippet(formatLatLng(l.lat, l.lon))
            )
        }

        if (planMode == TrajectoryPlanMode.Area && areaPolygon.isNotEmpty()) {
            val polygonGcj = areaPolygon.map { p ->
                val (gcjLat, gcjLon) = GpsUtils.gps84ToGcj02(p.lat, p.lon)
                LatLng(gcjLat, gcjLon)
            }

            polygonGcj.forEachIndexed { idx, ll ->
                map.addMarker(
                    MarkerOptions()
                        .position(ll)
                        .anchor(0.5f, 1f)
                        .title("A${idx + 1}")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                )
            }

            if (polygonGcj.size >= 2) {
                map.addPolyline(
                    PolylineOptions()
                        .addAll(polygonGcj + if (areaClosed && polygonGcj.size >= 3) listOf(polygonGcj.first()) else emptyList())
                        .width(6f)
                        .color(android.graphics.Color.YELLOW)
                )
            }
        }

        if (plannedPath.isNotEmpty()) {
            val polylinePoints = plannedPath.map { p ->
                val (gcjLat, gcjLon) = GpsUtils.gps84ToGcj02(p.lat, p.lon)
                LatLng(gcjLat, gcjLon)
            }
            map.addPolyline(
                PolylineOptions()
                    .addAll(polylinePoints)
                    .width(8f)
                    .color(android.graphics.Color.BLUE)
            )
            polylinePoints.forEachIndexed { idx, ll ->
                map.addMarker(
                    MarkerOptions()
                        .position(ll)
                        .anchor(0.5f, 0.5f)
                        .title("P${idx + 1}")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                )
            }
        }
    }

    val MapContent = @Composable {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val mapViewInstance = remember { MapView(context).apply { onCreate(Bundle()) } }

        DisposableEffect(lifecycleOwner, mapViewInstance) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> mapViewInstance.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapViewInstance.onPause()
                    Lifecycle.Event.ON_DESTROY -> mapViewInstance.onDestroy()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)

            mapView = mapViewInstance
            val map = mapViewInstance.map
            aMap = map

            map.uiSettings.isZoomControlsEnabled = false
            map.uiSettings.isMyLocationButtonEnabled = true
            val myLocationStyle = MyLocationStyle()
            myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATE)
            myLocationStyle.interval(2000)
            map.myLocationStyle = myLocationStyle
            map.isMyLocationEnabled = true
            map.moveCamera(CameraUpdateFactory.zoomTo(16f))
            map.mapType = mapType

            map.setOnMapClickListener { latLng ->
                val (gpsLat, gpsLon) = GpsUtils.gcj02ToGps84(latLng.latitude, latLng.longitude)
                planError = null

                when (planMode) {
                    TrajectoryPlanMode.Line -> {
                        when {
                            startPoint == null -> {
                                startPoint = RoutePoint(gpsLat, gpsLon)
                                startPointGcj = latLng
                                plannedPath = emptyList()
                            }
                            endPoint == null -> {
                                endPoint = RoutePoint(gpsLat, gpsLon)
                                endPointGcj = latLng
                                plannedPath = emptyList()
                            }
                            else -> {
                                endPoint = RoutePoint(gpsLat, gpsLon)
                                endPointGcj = latLng
                                plannedPath = emptyList()
                            }
                        }
                    }
                    TrajectoryPlanMode.Area -> {
                        when (areaEditMode) {
                            AreaEditMode.Polygon -> {
                                if (areaClosed) {
                                    planError = "区域已完成，如需修改请点“重画”"
                                    return@setOnMapClickListener
                                }
                                areaPolygon.add(RoutePoint(gpsLat, gpsLon))
                                plannedPath = emptyList()
                            }
                            AreaEditMode.Takeoff -> {
                                takeoffPoint = RoutePoint(gpsLat, gpsLon)
                                takeoffPointGcj = latLng
                                plannedPath = emptyList()
                            }
                            AreaEditMode.Landing -> {
                                landingPoint = RoutePoint(gpsLat, gpsLon)
                                landingPointGcj = latLng
                                plannedPath = emptyList()
                            }
                        }
                    }
                }
            }

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                try {
                    map.isMyLocationEnabled = false
                    mapViewInstance.onPause()
                } catch (_: Throwable) {
                }
                mapView = null
                aMap = null
            }
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { mapViewInstance }
        )
    }

    val altitude = altitudeText.toFloatOrNull() ?: 30f
    val speed = speedText.toFloatOrNull() ?: 5f
    val pathDistanceMeters = remember(plannedPath) { polylineLengthMeters(plannedPath) }
    val computedStart = plannedPath.firstOrNull()
    val computedEnd = plannedPath.lastOrNull()

    @Composable
    fun PanelScrollableContent(modifier: Modifier, scrollState: ScrollState) {
        Column(
            modifier = modifier.verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        planMode = TrajectoryPlanMode.Line
                        planError = null
                        plannedPath = emptyList()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (planMode == TrajectoryPlanMode.Line) Color(0xFF2E7DFF) else Color(0xFF2A2A2A)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("线状", color = Color.White)
                }
                Button(
                    onClick = {
                        planMode = TrajectoryPlanMode.Area
                        areaEditMode = AreaEditMode.Polygon
                        autoAngle = true
                        planError = null
                        plannedPath = emptyList()
                        startPoint = null
                        endPoint = null
                        startPointGcj = null
                        endPointGcj = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (planMode == TrajectoryPlanMode.Area) Color(0xFF2E7DFF) else Color(0xFF2A2A2A)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("面状", color = Color.White)
                }
            }

            Text("任务参数", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = altitudeText,
                    onValueChange = { altitudeText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("高度(m)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                    )
                )
                OutlinedTextField(
                    value = speedText,
                    onValueChange = { speedText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("速度(m/s)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                    )
                )
            }

            Text("规划参数", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (planMode == TrajectoryPlanMode.Line) {
                    OutlinedTextField(
                        value = gridSizeText,
                        onValueChange = { gridSizeText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("网格(m)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                        )
                    )
                } else {
                    OutlinedTextField(
                        value = laneSpacingText,
                        onValueChange = { laneSpacingText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("航线间距(m)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                        )
                    )
                }
                OutlinedTextField(
                    value = safetyMarginText,
                    onValueChange = { safetyMarginText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("避障边距(m)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                    )
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = segmentLenText,
                    onValueChange = { segmentLenText = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("航点间距(m)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedLabelColor = Color.White,
                        unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                    )
                )
                if (planMode == TrajectoryPlanMode.Area) {
                    OutlinedTextField(
                        value = laneAngleText,
                        onValueChange = { laneAngleText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == '-' } },
                        label = { Text("方向(°)") },
                        enabled = !autoAngle,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                            focusedLabelColor = Color.White,
                            unfocusedLabelColor = Color.White.copy(alpha = 0.7f)
                        )
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = smoothPath, onCheckedChange = { smoothPath = it })
                    Text("平滑", color = Color.White)
                }
            }

            if (planMode == TrajectoryPlanMode.Area) {
                Text("区域编辑", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { areaEditMode = AreaEditMode.Polygon },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (areaEditMode == AreaEditMode.Polygon) Color(0xFF2E7DFF) else Color(0xFF2A2A2A)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("绘制区域", color = Color.White, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { areaEditMode = AreaEditMode.Takeoff },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (areaEditMode == AreaEditMode.Takeoff) Color(0xFF2E7DFF) else Color(0xFF2A2A2A)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("起飞点", color = Color.White, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { areaEditMode = AreaEditMode.Landing },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (areaEditMode == AreaEditMode.Landing) Color(0xFF2E7DFF) else Color(0xFF2A2A2A)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("降落点", color = Color.White, fontSize = 12.sp)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { autoAngle = false; laneAngleText = "0" },
                        modifier = Modifier.weight(1f)
                    ) { Text("水平扫描") }
                    OutlinedButton(
                        onClick = { autoAngle = false; laneAngleText = "90" },
                        modifier = Modifier.weight(1f)
                    ) { Text("垂直扫描") }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(checked = autoAngle, onCheckedChange = { autoAngle = it })
                    Text("自动方向(最长边)", color = Color.White)
                }

                val takeoffGcjDisplay = takeoffPointGcj ?: takeoffPoint?.let { (lat, lon) ->
                    val (gcjLat, gcjLon) = GpsUtils.gps84ToGcj02(lat, lon)
                    LatLng(gcjLat, gcjLon)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "起飞点: ${takeoffPoint?.let { formatLatLng(it.lat, it.lon) } ?: "未设置"}",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            val p = takeoffPoint ?: return@TextButton
                            clipboard.setText(AnnotatedString("${p.lat.format(8)},${p.lon.format(8)}"))
                        },
                        enabled = takeoffPoint != null
                    ) { Text("复制", color = Color.White) }
                    TextButton(
                        onClick = {
                            takeoffPoint = null
                            takeoffPointGcj = null
                            plannedPath = emptyList()
                        },
                        enabled = takeoffPoint != null
                    ) { Text("清除", color = Color.White.copy(alpha = 0.9f)) }
                }
                if (takeoffGcjDisplay != null) {
                    Text(
                        "起飞点(GCJ02): ${takeoffGcjDisplay.latitude.format(8)}, ${takeoffGcjDisplay.longitude.format(8)}",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp
                    )
                }

                val landingGcjDisplay = landingPointGcj ?: landingPoint?.let { (lat, lon) ->
                    val (gcjLat, gcjLon) = GpsUtils.gps84ToGcj02(lat, lon)
                    LatLng(gcjLat, gcjLon)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "降落点: ${landingPoint?.let { formatLatLng(it.lat, it.lon) } ?: "未设置"}",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            val p = landingPoint ?: return@TextButton
                            clipboard.setText(AnnotatedString("${p.lat.format(8)},${p.lon.format(8)}"))
                        },
                        enabled = landingPoint != null
                    ) { Text("复制", color = Color.White) }
                    TextButton(
                        onClick = {
                            landingPoint = null
                            landingPointGcj = null
                            plannedPath = emptyList()
                        },
                        enabled = landingPoint != null
                    ) { Text("清除", color = Color.White.copy(alpha = 0.9f)) }
                }
                if (landingGcjDisplay != null) {
                    Text(
                        "降落点(GCJ02): ${landingGcjDisplay.latitude.format(8)}, ${landingGcjDisplay.longitude.format(8)}",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 12.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (areaPolygon.isNotEmpty() && !areaClosed) {
                                areaPolygon.removeAt(areaPolygon.lastIndex)
                                plannedPath = emptyList()
                            }
                        },
                        enabled = areaPolygon.isNotEmpty() && !areaClosed,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("撤销点")
                    }
                    Button(
                        onClick = {
                            if (areaPolygon.size >= 3) {
                                areaClosed = true
                                plannedPath = emptyList()
                                planError = null
                            } else {
                                planError = "区域至少需要3个点"
                            }
                        },
                        enabled = !areaClosed,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (areaClosed) "已完成" else "完成区域")
                    }
                    OutlinedButton(
                        onClick = {
                            areaPolygon.clear()
                            areaClosed = false
                            plannedPath = emptyList()
                            planError = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("重画")
                    }
                }
                Text(
                    "区域点数: ${areaPolygon.size}  状态: ${if (areaClosed) "已完成" else "绘制中（点击地图加点）"}",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { requestPlan() },
                    enabled = !planning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (planning) "规划中..." else "开始规划")
                }
                OutlinedButton(
                    onClick = { resetPlan() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("清空")
                }
            }

            if (planError != null) {
                Text(planError ?: "", color = Color(0xFFFF6B6B))
            }

            val startGps = when (planMode) {
                TrajectoryPlanMode.Line -> startPoint
                TrajectoryPlanMode.Area -> takeoffPoint ?: computedStart
            }
            val endGps = when (planMode) {
                TrajectoryPlanMode.Line -> endPoint
                TrajectoryPlanMode.Area -> landingPoint ?: computedEnd
            }
            val startGcj = when (planMode) {
                TrajectoryPlanMode.Line -> startPointGcj
                TrajectoryPlanMode.Area -> (takeoffPointGcj ?: startGps?.let { (lat, lon) ->
                    val (gcjLat, gcjLon) = GpsUtils.gps84ToGcj02(lat, lon)
                    LatLng(gcjLat, gcjLon)
                })
            }
            val endGcj = when (planMode) {
                TrajectoryPlanMode.Line -> endPointGcj
                TrajectoryPlanMode.Area -> (landingPointGcj ?: endGps?.let { (lat, lon) ->
                    val (gcjLat, gcjLon) = GpsUtils.gps84ToGcj02(lat, lon)
                    LatLng(gcjLat, gcjLon)
                })
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "起点(GPS84): ${startGps?.let { formatLatLng(it.lat, it.lon) } ?: "未选择"}",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        val s = startGps ?: return@TextButton
                        clipboard.setText(AnnotatedString("${s.lat.format(8)},${s.lon.format(8)}"))
                    },
                    enabled = startGps != null
                ) {
                    Text("复制", color = Color.White)
                }
            }
            if (startGcj != null) {
                Text(
                    "起点(GCJ02): ${startGcj.latitude.format(8)}, ${startGcj.longitude.format(8)}",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "终点(GPS84): ${endGps?.let { formatLatLng(it.lat, it.lon) } ?: "未选择"}",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = {
                        val e = endGps ?: return@TextButton
                        clipboard.setText(AnnotatedString("${e.lat.format(8)},${e.lon.format(8)}"))
                    },
                    enabled = endGps != null
                ) {
                    Text("复制", color = Color.White)
                }
            }
            if (endGcj != null) {
                Text(
                    "终点(GCJ02): ${endGcj.latitude.format(8)}, ${endGcj.longitude.format(8)}",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp
                )
            }
            Text(
                "航点: ${plannedPath.size}  距离: ${(pathDistanceMeters / 1000.0).format(2)} km  高度: ${altitude.format(1)} m  速度: ${speed.format(1)} m/s",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp
            )
            if (usedGridMeters != null) {
                Text(
                    "实际网格: ${usedGridMeters!!.format(1)} m",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }

            if (plannedPath.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("航点坐标", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showWaypoints = !showWaypoints }) {
                        Text(if (showWaypoints) "收起" else "展开", color = Color.White)
                    }
                }
            }

            if (showWaypoints && plannedPath.isNotEmpty()) {
                val gpsText = remember(plannedPath) {
                    plannedPath.mapIndexed { idx, p ->
                        "${idx + 1}\t${p.lat.format(8)},${p.lon.format(8)}\talt=${p.altitudeMeters.format(1)}\tspeed=${p.speedMetersPerSecond.format(1)}"
                    }.joinToString("\n")
                }
                val gcjText = remember(plannedPath) {
                    plannedPath.mapIndexed { idx, p ->
                        val (gcjLat, gcjLon) = GpsUtils.gps84ToGcj02(p.lat, p.lon)
                        "${idx + 1}\t${gcjLat.format(8)},${gcjLon.format(8)}\talt=${p.altitudeMeters.format(1)}\tspeed=${p.speedMetersPerSecond.format(1)}"
                    }.joinToString("\n")
                }
                val geoJsonText = remember(plannedPath) {
                    val coords = plannedPath.joinToString(",") { p ->
                        "[${p.lon.format(8)},${p.lat.format(8)},${p.altitudeMeters.format(1)}]"
                    }
                    """{"type":"FeatureCollection","features":[{"type":"Feature","properties":{"name":"trajectory"},"geometry":{"type":"LineString","coordinates":[$coords]}}]}"""
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(gpsText)) },
                        modifier = Modifier.weight(1f)
                    ) { Text("复制GPS84") }
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(gcjText)) },
                        modifier = Modifier.weight(1f)
                    ) { Text("复制GCJ02") }
                }
                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(geoJsonText)) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("导出GeoJSON(复制)") }

                val showCount = if (showAllWaypoints) plannedPath.size else min(30, plannedPath.size)
                Text(
                    "预览: $showCount / ${plannedPath.size}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                if (!showAllWaypoints && plannedPath.size > 30) {
                    TextButton(onClick = { showAllWaypoints = true }) {
                        Text("显示全部", color = Color.White)
                    }
                } else if (showAllWaypoints && plannedPath.size > 30) {
                    TextButton(onClick = { showAllWaypoints = false }) {
                        Text("收起预览", color = Color.White)
                    }
                }
                for (i in 0 until showCount) {
                    val p = plannedPath[i]
                    Text(
                        "P${i + 1}: ${p.lat.format(8)}, ${p.lon.format(8)}",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    val panelBg = Color(0xFF101010)
    var panelCollapsed by remember { mutableStateOf(false) }
    val panelWidth = if (panelCollapsed) 72.dp else 380.dp

    Row(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            MapContent()
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Home, contentDescription = null, tint = Color.White)
                }
                Text("航迹规划", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(6.dp))
                TextButton(onClick = { onMapTypeChange(if (mapType == AMap.MAP_TYPE_NORMAL) AMap.MAP_TYPE_SATELLITE else AMap.MAP_TYPE_NORMAL) }) {
                    Text(if (mapType == AMap.MAP_TYPE_NORMAL) "卫星" else "地图", color = Color.White)
                }
            }
        }

        Surface(
            modifier = Modifier
                .widthIn(min = if (panelCollapsed) 72.dp else 320.dp, max = if (panelCollapsed) 72.dp else 420.dp)
                .width(panelWidth)
                .fillMaxHeight(),
            color = panelBg
        ) {
            if (panelCollapsed) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(onClick = { panelCollapsed = false }) {
                        Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = null, tint = Color.White)
                    }
                    Divider(color = Color.White.copy(alpha = 0.12f))
                    IconButton(onClick = { planMode = TrajectoryPlanMode.Line; planError = null; plannedPath = emptyList() }) {
                        Icon(Icons.Filled.List, contentDescription = null, tint = if (planMode == TrajectoryPlanMode.Line) Color(0xFF2E7DFF) else Color.White)
                    }
                    IconButton(onClick = {
                        planMode = TrajectoryPlanMode.Area
                        areaEditMode = AreaEditMode.Polygon
                        autoAngle = true
                        planError = null
                        plannedPath = emptyList()
                        startPoint = null
                        endPoint = null
                        startPointGcj = null
                        endPointGcj = null
                    }) {
                        Icon(Icons.Filled.Create, contentDescription = null, tint = if (planMode == TrajectoryPlanMode.Area) Color(0xFF2E7DFF) else Color.White)
                    }
                    Divider(color = Color.White.copy(alpha = 0.12f))
                    IconButton(onClick = { requestPlan() }, enabled = !planning) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                    }
                    IconButton(onClick = { resetPlan() }) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color.White)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { if (plannedPath.size >= 2) onUploadAndStart(plannedPath) },
                        enabled = connected && plannedPath.size >= 2 && !planning
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = null, tint = if (connected) Color(0xFF2E7DFF) else Color.White.copy(alpha = 0.5f))
                    }
                }
            } else {
                val scrollState = rememberScrollState()
                val density = LocalDensity.current
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("参数面板", color = Color.White, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${if (planMode == TrajectoryPlanMode.Line) "线状" else "面状"}  航点:${plannedPath.size}  ${(pathDistanceMeters / 1000.0).format(2)}km",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                        IconButton(onClick = { panelCollapsed = true }) {
                            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White)
                        }
                    }
                    Divider(color = Color.White.copy(alpha = 0.12f))
                    Box(modifier = Modifier.weight(1f)) {
                        PanelScrollableContent(modifier = Modifier.fillMaxSize().padding(16.dp), scrollState = scrollState)
                        val max = scrollState.maxValue
                        if (max > 0) {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(10.dp)
                                    .align(Alignment.CenterEnd)
                            ) {
                                val trackHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                                val viewport = scrollState.viewportSize.toFloat().coerceAtLeast(1f)
                                val content = viewport + max.toFloat()
                                val thumbHeightPx = (trackHeightPx * (viewport / content)).coerceAtLeast(with(density) { 22.dp.toPx() })
                                val maxOffsetPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
                                val thumbOffsetPx = (scrollState.value.toFloat() / max.toFloat()) * maxOffsetPx
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                                        .padding(end = 2.dp)
                                        .width(4.dp)
                                        .height(with(density) { thumbHeightPx.toDp() })
                                        .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                                )
                            }
                        }
                    }
                    Divider(color = Color.White.copy(alpha = 0.12f))
                    Box(modifier = Modifier.padding(16.dp)) {
                        Button(
                            onClick = { if (plannedPath.size >= 2) onUploadAndStart(plannedPath) },
                            enabled = connected && plannedPath.size >= 2 && !planning,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (connected) "上传并开始" else "未连接无人机")
                        }
                    }
                }
            }
        }
    }
}

private fun planTrajectoryAreaCoverage(
    polygon: List<RoutePoint>,
    initialPoint: RoutePoint?,
    noFlyZones: List<NoFlyZone>,
    safetyMarginMeters: Double,
    laneSpacingMeters: Double,
    laneAngleDeg: Double,
    waypointSpacingMeters: Double,
    smooth: Boolean
): TrajectoryPlanResult {
    if (polygon.size < 3) return TrajectoryPlanResult(emptyList(), null, "区域至少需要3个点")

    val centerLat = polygon.map { it.lat }.average()
    val centerLon = polygon.map { it.lon }.average()
    val projector = LocalProjector(centerLat, centerLon)
    val angleRad = laneAngleDeg * Math.PI / 180.0
    val cosA = cos(angleRad)
    val sinA = sin(angleRad)

    fun rot(x: Double, y: Double): Pair<Double, Double> = (x * cosA - y * sinA) to (x * sinA + y * cosA)
    fun invRot(x: Double, y: Double): Pair<Double, Double> = (x * cosA + y * sinA) to (-x * sinA + y * cosA)

    val polyXY = polygon.map { p -> projector.toXYMeters(p.lat, p.lon) }
    val polyR = polyXY.map { (x, y) -> rot(x, y) }

    val zones = noFlyZones
        .filter { it.radiusMeters > 0f }
        .map { z ->
            val (zx, zy) = projector.toXYMeters(z.lat, z.lon)
            Triple(zx, zy, z.radiusMeters.toDouble() + safetyMarginMeters)
        }
    val zonesR = zones.map { (x, y, r) ->
        val (xr, yr) = rot(x, y)
        Triple(xr, yr, r)
    }

    fun pointInPolygon(p: Pair<Double, Double>, poly: List<Pair<Double, Double>>): Boolean {
        fun pointOnSegment(
            px: Double,
            py: Double,
            ax: Double,
            ay: Double,
            bx: Double,
            by: Double,
            eps: Double
        ): Boolean {
            val abx = bx - ax
            val aby = by - ay
            val apx = px - ax
            val apy = py - ay
            val cross = abx * apy - aby * apx
            if (abs(cross) > eps) return false
            val dot = apx * abx + apy * aby
            if (dot < -eps) return false
            val ab2 = abx * abx + aby * aby
            if (dot - ab2 > eps) return false
            return true
        }

        var inside = false
        val x = p.first
        val y = p.second
        var j = poly.lastIndex
        for (i in poly.indices) {
            val xi = poly[i].first
            val yi = poly[i].second
            val xj = poly[j].first
            val yj = poly[j].second
            if (pointOnSegment(x, y, xi, yi, xj, yj, eps = 1e-9)) return true
            val intersect = ((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi + 1e-15) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    fun mergeIntervals(intervals: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (intervals.isEmpty()) return emptyList()
        val sorted = intervals.map { min(it.first, it.second) to max(it.first, it.second) }.sortedBy { it.first }
        val out = mutableListOf<Pair<Double, Double>>()
        var cur = sorted.first()
        for (i in 1 until sorted.size) {
            val nxt = sorted[i]
            if (nxt.first <= cur.second) {
                cur = cur.first to max(cur.second, nxt.second)
            } else {
                out.add(cur)
                cur = nxt
            }
        }
        out.add(cur)
        return out
    }

    fun subtractIntervals(base: Pair<Double, Double>, blocks: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        val a = min(base.first, base.second)
        val b = max(base.first, base.second)
        val merged = mergeIntervals(blocks)
        val out = mutableListOf<Pair<Double, Double>>()
        var curStart = a
        for (blk in merged) {
            val bs = blk.first
            val be = blk.second
            if (be <= curStart) continue
            if (bs >= b) break
            if (bs > curStart) out.add(curStart to min(bs, b))
            curStart = max(curStart, be)
            if (curStart >= b) break
        }
        if (curStart < b) out.add(curStart to b)
        return out.filter { (it.second - it.first) > 0.5 }
    }

    fun connectWithinPolygon(
        from: Pair<Double, Double>,
        to: Pair<Double, Double>,
        gridMeters: Double,
        segmentMeters: Double
    ): List<Pair<Double, Double>> {
        if (from == to) return emptyList()

        val minX = min(from.first, to.first) - 80.0
        val maxX = max(from.first, to.first) + 80.0
        val minY = min(from.second, to.second) - 80.0
        val maxY = max(from.second, to.second) + 80.0

        var usedGrid = gridMeters.coerceIn(5.0, 80.0)
        var width = ((maxX - minX) / usedGrid).roundToInt() + 1
        var height = ((maxY - minY) / usedGrid).roundToInt() + 1

        val maxSide = 260
        while ((width > maxSide || height > maxSide) && usedGrid < 160.0) {
            usedGrid *= 1.5
            width = ((maxX - minX) / usedGrid).roundToInt() + 1
            height = ((maxY - minY) / usedGrid).roundToInt() + 1
        }
        if (width > maxSide || height > maxSide) return emptyList()

        fun toGrid(x: Double, y: Double): Pair<Int, Int> {
            val gx = ((x - minX) / usedGrid).roundToInt().coerceIn(0, width - 1)
            val gy = ((y - minY) / usedGrid).roundToInt().coerceIn(0, height - 1)
            return gx to gy
        }

        fun toMeters(gx: Int, gy: Int): Pair<Double, Double> {
            val x = minX + gx * usedGrid
            val y = minY + gy * usedGrid
            return x to y
        }

        fun blocked(x: Double, y: Double): Boolean {
            if (!pointInPolygon(x to y, polyXY)) return true
            for ((zx, zy, rr) in zones) {
                val dx = x - zx
                val dy = y - zy
                if (dx * dx + dy * dy <= rr * rr) return true
            }
            return false
        }

        val (sx, sy) = toGrid(from.first, from.second)
        val (ex, ey) = toGrid(to.first, to.second)
        val sMeters = toMeters(sx, sy)
        val eMeters = toMeters(ex, ey)
        if (blocked(sMeters.first, sMeters.second) || blocked(eMeters.first, eMeters.second)) return emptyList()

        val pathGrid = aStarGrid(
            width = width,
            height = height,
            startX = sx,
            startY = sy,
            endX = ex,
            endY = ey,
            isBlocked = { gx, gy ->
                val (mx, my) = toMeters(gx, gy)
                blocked(mx, my)
            }
        )
        if (pathGrid.isEmpty()) return emptyList()

        val path = pathGrid.map { (gx, gy) -> toMeters(gx, gy) }
        val simplified = if (smooth) simplifyPathMeters(path, usedGrid, ::blocked) else path
        val sampled = resamplePathMeters(simplified, segmentMeters.coerceAtLeast(10.0))
        return if (sampled.size <= 2) sampled else sampled.drop(1).dropLast(1)
    }

    val minY = polyR.minOf { it.second }
    val maxY = polyR.maxOf { it.second }
    val laneStep = laneSpacingMeters.coerceAtLeast(5.0)
    val eps = 1e-9

    fun intersectionsAtY(y: Double): List<Double> {
        val xs = mutableListOf<Double>()
        for (i in polyR.indices) {
            val a = polyR[i]
            val b = polyR[(i + 1) % polyR.size]
            val y1 = a.second
            val y2 = b.second
            if (abs(y1 - y2) < eps) continue
            val minEdgeY = min(y1, y2)
            val maxEdgeY = max(y1, y2)
            val include = (y >= minEdgeY && y < maxEdgeY) || (y == maxY && y > minEdgeY && y <= maxEdgeY)
            if (!include) continue
            val t = (y - y1) / (y2 - y1)
            xs.add(a.first + t * (b.first - a.first))
        }
        return xs.sorted()
    }

    fun blockedIntervalsAtY(y: Double, xMin: Double, xMax: Double): List<Pair<Double, Double>> {
        val blocks = mutableListOf<Pair<Double, Double>>()
        for ((cx, cy, rr) in zonesR) {
            val dy = y - cy
            if (abs(dy) >= rr) continue
            val dx = sqrt(rr * rr - dy * dy)
            val a = cx - dx
            val b = cx + dx
            val s = max(min(a, b), xMin)
            val e = min(max(a, b), xMax)
            if (e > s) blocks.add(s to e)
        }
        return blocks
    }

    val segmentMeters = waypointSpacingMeters.coerceAtLeast(10.0)
    val connectorGrid = min(laneStep, segmentMeters).coerceAtLeast(8.0) / 2.0

    fun blockedOriginal(x: Double, y: Double): Boolean {
        for ((zx, zy, rr) in zones) {
            val dx = x - zx
            val dy = y - zy
            if (dx * dx + dy * dy <= rr * rr) return true
        }
        return false
    }

    data class Strip(val points: List<Pair<Double, Double>>) {
        val start: Pair<Double, Double> get() = points.first()
        val end: Pair<Double, Double> get() = points.last()
    }

    fun allowed(p: Pair<Double, Double>): Boolean {
        if (!pointInPolygon(p, polyXY)) return false
        if (blockedOriginal(p.first, p.second)) return false
        return true
    }

    val strips = mutableListOf<Strip>()
    var y = minY
    while (y <= maxY + eps) {
        val xs = intersectionsAtY(y)
        if (xs.size >= 2) {
            val rawSegments = xs.chunked(2).mapNotNull { pair ->
                if (pair.size == 2) pair[0] to pair[1] else null
            }
            val segments = rawSegments.mapNotNull { seg ->
                val x0 = min(seg.first, seg.second)
                val x1 = max(seg.first, seg.second)
                val mid = ((x0 + x1) / 2.0) to y
                if (pointInPolygon(mid, polyR)) x0 to x1 else null
            }.sortedBy { it.first }

            for ((segStart, segEnd) in segments) {
                val xMin = min(segStart, segEnd)
                val xMax = max(segStart, segEnd)
                val blocks = blockedIntervalsAtY(y, xMin, xMax)
                val remain = subtractIntervals(xMin to xMax, blocks).sortedBy { it.first }

                for (interval in remain) {
                    val intervalStartRot = interval.first to y
                    val intervalEndRot = interval.second to y
                    val dx = intervalEndRot.first - intervalStartRot.first
                    val dist = abs(dx)
                    val steps = max(1, ceil(dist / segmentMeters).toInt())
                    val pts = mutableListOf<Pair<Double, Double>>()
                    for (i in 0..steps) {
                        val t = i.toDouble() / steps.toDouble()
                        val xr = intervalStartRot.first + dx * t
                        val (xo, yo) = invRot(xr, y)
                        val p = xo to yo
                        if (!allowed(p)) continue
                        if (pts.isEmpty()) {
                            pts.add(p)
                        } else {
                            val last = pts.last()
                            val ddx = p.first - last.first
                            val ddy = p.second - last.second
                            if (ddx * ddx + ddy * ddy >= 1.0) pts.add(p)
                        }
                    }
                    if (pts.size >= 2) strips.add(Strip(pts))
                }
            }
        }
        y += laneStep
    }

    if (strips.isEmpty()) return TrajectoryPlanResult(emptyList(), null, "未生成有效航线（可能区域过小或被禁飞区完全覆盖）")

    fun distance(a: Pair<Double, Double>, b: Pair<Double, Double>): Double {
        val dx = a.first - b.first
        val dy = a.second - b.second
        return sqrt(dx * dx + dy * dy)
    }

    val startCursor = initialPoint?.let { projector.toXYMeters(it.lat, it.lon) }
    var cursor = startCursor
    val remaining = strips.toMutableList()
    val orderedMeters = mutableListOf<Pair<Double, Double>>()

    while (remaining.isNotEmpty()) {
        var bestIdx = 0
        var bestReverse = false
        var bestDist = Double.POSITIVE_INFINITY

        val cur = cursor
        if (cur == null) {
            bestIdx = 0
            bestReverse = false
        } else {
            for (i in remaining.indices) {
                val s = remaining[i]
                val dStart = distance(cur, s.start)
                val dEnd = distance(cur, s.end)
                if (dStart < bestDist) {
                    bestDist = dStart
                    bestIdx = i
                    bestReverse = false
                }
                if (dEnd < bestDist) {
                    bestDist = dEnd
                    bestIdx = i
                    bestReverse = true
                }
            }
        }

        val strip = remaining.removeAt(bestIdx)
        val points = if (bestReverse) strip.points.asReversed() else strip.points

        if (orderedMeters.isNotEmpty()) {
            val last = orderedMeters.last()
            val first = points.first()
            if (distance(last, first) > segmentMeters * 1.5) {
                val connector = connectWithinPolygon(last, first, connectorGrid, segmentMeters)
                if (connector.isNotEmpty()) orderedMeters.addAll(connector)
            }
            if (orderedMeters.isNotEmpty() && distance(orderedMeters.last(), first) < 1.0) {
                orderedMeters.addAll(points.drop(1))
            } else {
                orderedMeters.addAll(points)
            }
        } else {
            orderedMeters.addAll(points)
        }

        cursor = orderedMeters.lastOrNull()
    }

    if (orderedMeters.size < 2) return TrajectoryPlanResult(emptyList(), null, "未生成有效航线（可能区域过小或被禁飞区完全覆盖）")

    val smoothedMeters = if (smooth) simplifyPathMeters(orderedMeters, segmentMeters, ::blockedOriginal) else orderedMeters
    val sampled = resamplePathMeters(smoothedMeters, segmentMeters)

    val pathLatLon = sampled.map { (x, y2) ->
        val (lat, lon) = projector.toLatLon(x, y2)
        RoutePoint(lat = lat, lon = lon)
    }

    return TrajectoryPlanResult(pathLatLon, null, null)
}

@Composable
fun FlightPage(
    connected: Boolean,
    msg: String,
    aircraftLat: Double?,
    aircraftLon: Double?,
    aircraftAltMeters: Float,
    aircraftSpeedMS: Float,
    aircraftVSpeedMS: Float,
    mapType: Int,
    batteryPercent: Int,
    uplinkSignal: Int,
    flightMode: String,
    satelliteCount: Int,
    route: List<RoutePoint>,
    cameraStorageLocation: SettingsDefinitions.StorageLocation,
    isRecording: Boolean,
    recordingStartMs: Long,
    onCameraStorageLocationChange: (SettingsDefinitions.StorageLocation) -> Unit,
    onTakePhoto: () -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    missionUploaded: Boolean,
    missionRunning: Boolean,
    onTakeoffAndStartMission: () -> Unit,
    onPauseMission: () -> Unit,
    onResumeMission: () -> Unit,
    onLand: () -> Unit,
    onSurfaceAvailable: (SurfaceTexture, Int, Int) -> Unit,
    onSurfaceDestroyed: (SurfaceTexture) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var toastVisible by remember { mutableStateOf(false) }
    var toastText by remember { mutableStateOf("") }

    LaunchedEffect(msg) {
        val trimmed = msg.trim()
        if (trimmed.isNotEmpty()) {
            toastText = trimmed
            toastVisible = true
            delay(2200)
            toastVisible = false
        }
    }

    var aMap by remember { mutableStateOf<AMap?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }

    var followDrone by remember { mutableStateOf(true) }
    var currentWpIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(route) {
        currentWpIndex = 0
    }

    LaunchedEffect(aircraftLat, aircraftLon, route) {
        val lat = aircraftLat ?: return@LaunchedEffect
        val lon = aircraftLon ?: return@LaunchedEffect
        if (route.size < 2) return@LaunchedEffect

        var bestIdx = 0
        var bestDist = Double.POSITIVE_INFINITY
        for (i in route.indices) {
            val d = haversineMeters(lat, lon, route[i].lat, route[i].lon)
            if (d < bestDist) {
                bestDist = d
                bestIdx = i
            }
        }
        if (bestIdx > currentWpIndex) currentWpIndex = bestIdx
    }

    LaunchedEffect(aMap, mapType, route, aircraftLat, aircraftLon, currentWpIndex, followDrone) {
        val map = aMap ?: return@LaunchedEffect
        map.clear(true)
        map.mapType = mapType

        val droneLat = aircraftLat
        val droneLon = aircraftLon
        val droneGcj = if (droneLat != null && droneLon != null) {
            val (gcjLat, gcjLon) = GpsUtils.gps84ToGcj02(droneLat, droneLon)
            LatLng(gcjLat, gcjLon)
        } else null

        if (route.size >= 2) {
            val routeGcj = route.map {
                val (gcjLat, gcjLon) = GpsUtils.gps84ToGcj02(it.lat, it.lon)
                LatLng(gcjLat, gcjLon)
            }

            map.addPolyline(
                PolylineOptions()
                    .addAll(routeGcj)
                    .color(android.graphics.Color.argb(220, 46, 125, 255))
                    .width(6f)
            )

            val completed = routeGcj.take((currentWpIndex + 1).coerceAtMost(routeGcj.size))
            if (completed.size >= 2) {
                map.addPolyline(
                    PolylineOptions()
                        .addAll(completed)
                        .color(android.graphics.Color.argb(230, 46, 255, 120))
                        .width(8f)
                )
            }

            map.addMarker(
                MarkerOptions()
                    .position(routeGcj.first())
                    .title("起点")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
            map.addMarker(
                MarkerOptions()
                    .position(routeGcj.last())
                    .title("终点")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
        }

        if (droneGcj != null) {
            map.addMarker(
                MarkerOptions()
                    .position(droneGcj)
                    .anchor(0.5f, 0.5f)
                    .title("无人机")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
            if (followDrone) {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(droneGcj, 17f))
            }
        }
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapViewInstance = remember { MapView(context).apply { onCreate(Bundle()) } }

    DisposableEffect(lifecycleOwner, mapViewInstance) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapViewInstance.onResume()
                Lifecycle.Event.ON_PAUSE -> mapViewInstance.onPause()
                Lifecycle.Event.ON_DESTROY -> mapViewInstance.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        mapView = mapViewInstance
        val map = mapViewInstance.map
        aMap = map
        map.uiSettings.isZoomControlsEnabled = false
        map.uiSettings.isMyLocationButtonEnabled = true

        val myLocationStyle = MyLocationStyle()
        myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATE)
        myLocationStyle.interval(2000)
        map.myLocationStyle = myLocationStyle
        map.isMyLocationEnabled = true

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                map.isMyLocationEnabled = false
                mapViewInstance.onPause()
            } catch (_: Exception) {
            }
            mapView = null
            aMap = null
        }
    }

    val nextWp = route.getOrNull((currentWpIndex + 1).coerceAtMost(route.lastIndex))
    val distToNext = if (aircraftLat != null && aircraftLon != null && nextWp != null) {
        haversineMeters(aircraftLat, aircraftLon, nextWp.lat, nextWp.lon)
    } else null

    var mainView by remember { mutableStateOf(FlightMainView.Fpv) }
    var panelCollapsed by remember { mutableStateOf(false) }
    val panelWidth = 280.dp
    val overlayEndPadding = (if (panelCollapsed) 12.dp else (panelWidth + 12.dp))
    val swapView = {
        mainView = if (mainView == FlightMainView.Map) FlightMainView.Fpv else FlightMainView.Map
    }
    var recordingText by remember { mutableStateOf("00:00") }
    LaunchedEffect(isRecording, recordingStartMs) {
        if (!isRecording) {
            recordingText = "00:00"
            return@LaunchedEffect
        }
        while (isActive) {
            val seconds = ((System.currentTimeMillis() - recordingStartMs).coerceAtLeast(0L)) / 1000L
            recordingText = formatDurationSeconds(seconds)
            delay(500)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val pipModifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(12.dp)
            .padding(end = if (panelCollapsed) 0.dp else panelWidth)
            .size(180.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.9f))
            .zIndex(2f)

        val fullModifier = Modifier.fillMaxSize().zIndex(0f)

        val mapBoxModifier = if (mainView == FlightMainView.Map) fullModifier else pipModifier
        val fpvBoxModifier = if (mainView == FlightMainView.Fpv) fullModifier else pipModifier

        Box(modifier = mapBoxModifier) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { mapViewInstance })
            if (mainView == FlightMainView.Fpv) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(onClick = swapView)
                )
            }
            if (isRecording && mainView == FlightMainView.Fpv) {
                Text(
                    "REC $recordingText",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color(0xFFFF5252),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
        var detEnabled by remember { mutableStateOf(false) }
        var detConf by remember { mutableFloatStateOf(0.25f) }
        var detIou by remember { mutableFloatStateOf(0.45f) }
        var detFps by remember { mutableIntStateOf(8) }
        val detResults = remember { mutableStateListOf<DetResult>() }
        val appContext = LocalContext.current
        var detInitError by remember { mutableStateOf<String?>(null) }
        val detBackend = remember(appContext) {
            val rootFiles = runCatching { appContext.assets.list("")?.toList() ?: emptyList() }.getOrDefault(emptyList())
            val preferredNcnnDirs = listOf("v8n_ncnn_model", "best_ncnn_model")
            val ncnnDir = preferredNcnnDirs.firstOrNull { dir ->
                val files = runCatching { appContext.assets.list(dir)?.toList() ?: emptyList() }.getOrDefault(emptyList())
                files.any { it == "model.ncnn.param" } && files.any { it == "model.ncnn.bin" }
            }
            val preferredTflite = "yolov8n_fp16.tflite"
            val tfliteAsset = when {
                rootFiles.any { it.equals(preferredTflite, ignoreCase = true) } -> preferredTflite
                else -> rootFiles.firstOrNull { it.endsWith(".tflite", ignoreCase = true) }
            }
            when {
                ncnnDir != null -> "ncnn" to ncnnDir
                tfliteAsset != null -> "tflite" to tfliteAsset
                else -> null
            }
        }
        val detModelReady = detBackend != null
        val detBackendLabel = when (detBackend?.first) {
            "ncnn" -> "NCNN"
            "tflite" -> "TFLite"
            else -> "无"
        }
        val detector: ObjectDetector? = remember(detEnabled, detBackend) {
            if (!detEnabled) null else try {
                detInitError = null
                val backend = detBackend ?: error("未找到可用模型")
                when (backend.first) {
                    "ncnn" -> YoloNcnn(appContext, backend.second)
                    "tflite" -> YoloTFLite(appContext, backend.second)
                    else -> error("未找到可用模型")
                }
            } catch (t: Throwable) {
                detInitError = t.message ?: "模型初始化失败"
                null
            }
        }
        DisposableEffect(detector) {
            onDispose {
                detector?.close()
            }
        }
        LaunchedEffect(detEnabled, detector, detConf, detIou, detFps, mainView) {
            while (detEnabled && detector != null) {
                val tv = textureViewRef
                if (tv != null && tv.width > 0 && tv.height > 0) {
                    try {
                        val bmp = tv.bitmap ?: continue
                        val res = withContext(kotlinx.coroutines.Dispatchers.Default) {
                            detector.detect(bmp, detConf, detIou)
                        }
                        detResults.clear()
                        detResults.addAll(res)
                    } catch (_: Throwable) { }
                }
                val interval = (1000L / detFps.coerceIn(1, 30))
                delay(interval)
            }
            if (!detEnabled) detResults.clear()
        }
        Box(modifier = fpvBoxModifier) {
            FPVWidget(
                onSurfaceCreated = onSurfaceAvailable,
                onSurfaceDestroyed = onSurfaceDestroyed,
                onSurfaceChanged = { _, _, _ -> },
                modifier = Modifier.fillMaxSize(),
                onTextureViewReady = { textureViewRef = it }
            )
            if (mainView == FlightMainView.Map) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable(onClick = swapView)
                )
            }
            if (detEnabled && detResults.isNotEmpty()) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val scaleX = size.width / (textureViewRef?.width?.toFloat() ?: size.width)
                    val scaleY = size.height / (textureViewRef?.height?.toFloat() ?: size.height)
                    val stroke = Stroke(width = 2f)
                    val red = androidx.compose.ui.graphics.Color(0xFFFF5252)
                    for (d in detResults) {
                        val l = d.box.left * scaleX
                        val t = d.box.top * scaleY
                        val r = d.box.right * scaleX
                        val b = d.box.bottom * scaleY
                        drawRect(color = red, topLeft = Offset(l, t), size = androidx.compose.ui.geometry.Size(r - l, b - t), style = stroke)
                    }
                }
            }
            if (isRecording && mainView == FlightMainView.Map) {
                Text(
                    "REC $recordingText",
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    color = Color(0xFFFF5252),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .size(40.dp)
            ) {
                Icon(Icons.Outlined.Home, contentDescription = null, tint = Color.White)
            }

            Card(colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.45f))) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("电量 ${batteryPercent}%", color = Color.White, fontSize = 12.sp)
                    Text("信号 ${uplinkSignal}%", color = Color.White, fontSize = 12.sp)
                    Text("卫星 $satelliteCount", color = Color.White, fontSize = 12.sp)
                    Text("模式 $flightMode", color = Color.White, fontSize = 12.sp)
                    val altText = if (aircraftAltMeters.isFinite()) "高 ${"%.1f".format(aircraftAltMeters)}m" else "高 --"
                    Text(altText, color = Color.White, fontSize = 12.sp)
                    Text("速 ${"%.1f".format(aircraftSpeedMS)}m/s", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        if (connected && batteryPercent in 1..19) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C).copy(alpha = 0.92f))
            ) {
                Text(
                    "电量不足（$batteryPercent%），请立即返航/降落",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }

        if (toastVisible) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 110.dp)
                    .widthIn(max = 520.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.72f))
            ) {
                Text(
                    toastText,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = overlayEndPadding, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.45f))) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = followDrone, onCheckedChange = { followDrone = it })
                    Text("跟随无人机", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        Card(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
                .widthIn(max = 520.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.55f))
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("航线状态", color = Color.White, fontWeight = FontWeight.SemiBold)
                if (route.isEmpty()) {
                    Text("暂无航线：请先在航迹规划中生成并上传", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp)
                } else {
                    Text("航点 ${currentWpIndex + 1}/${route.size}", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    if (distToNext != null) {
                        Text("距下一航点 ${(distToNext).format(1)}m", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                    if (aircraftLat != null && aircraftLon != null) {
                        Text("无人机: ${formatLatLng(aircraftLat, aircraftLon)}", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                    }
                }
                if (!connected) {
                    Text("未连接无人机", color = Color(0xFFFFB300), fontSize = 12.sp)
                }
            }
        }

        if (panelCollapsed) {
            Card(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 6.dp)
                    .zIndex(3f),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.72f))
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    IconButton(
                        onClick = { panelCollapsed = false },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = null, tint = Color.White)
                    }

                    IconButton(
                        onClick = onTakePhoto,
                        enabled = connected,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = Color.White)
                    }

                    IconButton(
                        onClick = { if (isRecording) onStopRecord() else onStartRecord() },
                        enabled = connected,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            if (isRecording) Icons.Filled.StopCircle else Icons.Filled.Videocam,
                            contentDescription = null,
                            tint = if (isRecording) Color(0xFFFF5252) else Color.White
                        )
                    }

                    IconButton(
                        onClick = onPauseMission,
                        enabled = connected && missionUploaded,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Filled.Pause, contentDescription = null, tint = Color.White)
                    }

                    IconButton(
                        onClick = onResumeMission,
                        enabled = connected && missionUploaded,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                    }

                    IconButton(
                        onClick = onTakeoffAndStartMission,
                        enabled = connected && missionUploaded && !missionRunning,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Filled.FlightTakeoff, contentDescription = null, tint = Color.White)
                    }

                    IconButton(
                        onClick = onLand,
                        enabled = connected,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(Icons.Filled.FlightLand, contentDescription = null, tint = Color(0xFFFF5252))
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(panelWidth)
                    .zIndex(3f),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.78f))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("紧急控制", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.weight(1f))
                        IconButton(onClick = { panelCollapsed = true }) {
                            Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.White)
                        }
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f))) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "存储: ${if (cameraStorageLocation == SettingsDefinitions.StorageLocation.SDCARD) "SD卡" else "机身"}",
                                color = Color.White,
                                fontSize = 12.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("目标识别", color = Color.White, fontSize = 12.sp)
                                Switch(checked = detEnabled, onCheckedChange = {
                                    detInitError = null
                                    if (it && !detModelReady) {
                                        android.widget.Toast.makeText(
                                            appContext,
                                            "未找到可用检测模型。\n请放置 NCNN：assets/v8n_ncnn_model/model.ncnn.param + model.ncnn.bin\n或放置 TFLite：assets/*.tflite",
                                            android.widget.Toast.LENGTH_LONG
                                        ).show()
                                        detEnabled = false
                                    } else {
                                        detEnabled = it
                                    }
                                })
                            }
                            if (detEnabled) {
                                Text("后端 $detBackendLabel", color = Color.White, fontSize = 12.sp)
                                Text("置信 ${"%.2f".format(detConf)}  阈值IoU ${"%.2f".format(detIou)}  ${detFps}FPS", color = Color.White, fontSize = 12.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Slider(value = detConf, onValueChange = { detConf = it }, valueRange = 0.1f..0.7f, modifier = Modifier.weight(1f))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Slider(value = detIou, onValueChange = { detIou = it }, valueRange = 0.3f..0.7f, modifier = Modifier.weight(1f))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("FPS", color = Color.White, fontSize = 12.sp)
                                    Slider(value = detFps.toFloat(), onValueChange = { detFps = it.toInt() }, valueRange = 1f..20f, steps = 18, modifier = Modifier.weight(1f))
                                }
                                Text("检测数 ${detResults.size}", color = Color.White, fontSize = 12.sp)
                                if (!detInitError.isNullOrBlank()) {
                                    Text("初始化失败: $detInitError", color = Color(0xFFFF8A80), fontSize = 11.sp)
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { onCameraStorageLocationChange(SettingsDefinitions.StorageLocation.INTERNAL_STORAGE) },
                                    enabled = connected,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (cameraStorageLocation == SettingsDefinitions.StorageLocation.INTERNAL_STORAGE) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.Smartphone, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("机身", color = Color.White)
                                }
                                OutlinedButton(
                                    onClick = { onCameraStorageLocationChange(SettingsDefinitions.StorageLocation.SDCARD) },
                                    enabled = connected,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (cameraStorageLocation == SettingsDefinitions.StorageLocation.SDCARD) Color.White.copy(alpha = 0.18f) else Color.Transparent,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Filled.SdCard, contentDescription = null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("SD卡", color = Color.White)
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onTakePhoto,
                            enabled = connected,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("拍照")
                        }

                        Button(
                            onClick = { if (isRecording) onStopRecord() else onStartRecord() },
                            enabled = connected,
                            colors = if (isRecording) {
                                ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C))
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(if (isRecording) Icons.Filled.StopCircle else Icons.Filled.Videocam, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isRecording) "停止 $recordingText" else "开始录像")
                        }
                    }

                    Card(colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.35f))) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("任务: ${if (missionUploaded) "已上传" else "未上传"}", color = Color.White, fontSize = 12.sp)
                            Text("状态: ${if (missionRunning) "执行中" else "未开始/已暂停"}", color = Color.White, fontSize = 12.sp)
                        }
                    }

                    Button(
                        onClick = onTakeoffAndStartMission,
                        enabled = connected && missionUploaded && !missionRunning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("一键起飞并开始航线")
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onPauseMission,
                            enabled = connected && missionUploaded,
                            modifier = Modifier.weight(1f)
                        ) { Text("暂停飞行") }

                        OutlinedButton(
                            onClick = onResumeMission,
                            enabled = connected && missionUploaded,
                            modifier = Modifier.weight(1f)
                        ) { Text("继续飞行") }
                    }

                    Button(
                        onClick = onLand,
                        enabled = connected,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB71C1C)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("一键降落", color = Color.White)
                    }

                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun RoutePlanPage(
    aircraftLat: Double?,
    aircraftLon: Double?,
    waypoints: SnapshotStateList<RoutePoint>,
    mapType: Int,
    batteryPercent: Int,
    satelliteCount: Int,
    flightMode: String,
    uplinkSignal: Int,
    onMapTypeChange: (Int) -> Unit,
    onUploadAndStart: (List<RoutePoint>) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var aMap by remember { androidx.compose.runtime.mutableStateOf<AMap?>(null) }
    var mapView by remember { mutableStateOf<MapView?>(null) }

    // Map Logic
    if (aMap != null && aMap?.mapType != mapType) {
        aMap?.mapType = mapType
    }
    val noFlyZones = remember { mutableStateListOf<NoFlyZone>() }
    LaunchedEffect(Unit) {
         if (noFlyZones.isEmpty()) {
             // Add sample zones
             noFlyZones.add(NoFlyZone(39.9042, 116.4074, 1000f)) // Beijing
             noFlyZones.add(NoFlyZone(22.5431, 114.0579, 1000f)) // Shenzhen
         }
    }
    
    LaunchedEffect(noFlyZones.size, aMap, mapType) {
        val map = aMap ?: return@LaunchedEffect
        map.clear(true)
        noFlyZones.forEach { zone ->
            val (gcjLat, gcjLon) = GpsUtils.gps84ToGcj02(zone.lat, zone.lon)
            val center = LatLng(gcjLat, gcjLon)
            map.addCircle(
                CircleOptions().center(center).radius(zone.radiusMeters.toDouble())
                    .strokeColor(android.graphics.Color.RED).fillColor(0x30FF0000).strokeWidth(2f)
            )
        }
        map.mapType = mapType
    }

    val MapContent = @Composable {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val mapViewInstance = remember {
            MapView(context).apply {
                onCreate(Bundle())
            }
        }
        
        DisposableEffect(lifecycleOwner, mapViewInstance) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> mapViewInstance.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapViewInstance.onPause()
                    Lifecycle.Event.ON_DESTROY -> mapViewInstance.onDestroy()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)

            mapView = mapViewInstance
            val map = mapViewInstance.map
            aMap = map

            map.uiSettings.isZoomControlsEnabled = false
            map.uiSettings.isMyLocationButtonEnabled = true

            val myLocationStyle = MyLocationStyle()
            myLocationStyle.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATE)
            myLocationStyle.interval(2000)
            map.myLocationStyle = myLocationStyle
            map.isMyLocationEnabled = true
            map.moveCamera(CameraUpdateFactory.zoomTo(17f))
            map.mapType = mapType

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                try {
                    map.isMyLocationEnabled = false
                    mapViewInstance.onPause()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                mapView = null
                aMap = null
            }
        }
        
        AndroidView(
            modifier = Modifier.fillMaxSize().zIndex(-1f),
            factory = { mapViewInstance }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Full Screen Map
        MapContent()

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                .size(40.dp)
        ) {
            Icon(Icons.Outlined.Home, contentDescription = null, tint = Color.White)
        }
    }
}

private data class TrajectoryPlanResult(
    val path: List<RoutePoint>,
    val usedGridSizeMeters: Double?,
    val error: String?
)

private fun planTrajectoryAStar(
    start: RoutePoint,
    end: RoutePoint,
    noFlyZones: List<NoFlyZone>,
    gridSizeMeters: Double,
    safetyMarginMeters: Double,
    segmentLengthMeters: Double,
    smooth: Boolean
): TrajectoryPlanResult {
    val zones = noFlyZones
        .filter { it.radiusMeters > 0f }
        .toList()

    val centerLat = (start.lat + end.lat) / 2.0
    val projector = LocalProjector(centerLat, (start.lon + end.lon) / 2.0)

    val (sx, sy) = projector.toXYMeters(start.lat, start.lon)
    val (ex, ey) = projector.toXYMeters(end.lat, end.lon)

    val zoneXY = zones.map { z ->
        val (zx, zy) = projector.toXYMeters(z.lat, z.lon)
        Triple(zx, zy, z.radiusMeters.toDouble() + safetyMarginMeters)
    }

    val baseMargin = max(300.0, (zoneXY.maxOfOrNull { it.third } ?: 0.0) + 50.0)
    val minX = min(sx, ex) - baseMargin
    val maxX = max(sx, ex) + baseMargin
    val minY = min(sy, ey) - baseMargin
    val maxY = max(sy, ey) + baseMargin

    var usedGrid = gridSizeMeters
    var width = ((maxX - minX) / usedGrid).roundToInt() + 1
    var height = ((maxY - minY) / usedGrid).roundToInt() + 1

    val maxSide = 260
    while ((width > maxSide || height > maxSide) && usedGrid < 250.0) {
        usedGrid *= 1.5
        width = ((maxX - minX) / usedGrid).roundToInt() + 1
        height = ((maxY - minY) / usedGrid).roundToInt() + 1
    }

    if (width > maxSide || height > maxSide) {
        return TrajectoryPlanResult(
            path = emptyList(),
            usedGridSizeMeters = usedGrid,
            error = "规划范围过大，请提高网格(m)或缩小起终点距离"
        )
    }

    fun isBlocked(xMeters: Double, yMeters: Double): Boolean {
        for ((zx, zy, rr) in zoneXY) {
            val dx = xMeters - zx
            val dy = yMeters - zy
            if (dx * dx + dy * dy <= rr * rr) return true
        }
        return false
    }

    fun toGrid(xMeters: Double, yMeters: Double): Pair<Int, Int> {
        val gx = ((xMeters - minX) / usedGrid).roundToInt().coerceIn(0, width - 1)
        val gy = ((yMeters - minY) / usedGrid).roundToInt().coerceIn(0, height - 1)
        return gx to gy
    }

    fun toMeters(gx: Int, gy: Int): Pair<Double, Double> {
        val xMeters = minX + gx * usedGrid
        val yMeters = minY + gy * usedGrid
        return xMeters to yMeters
    }

    val (sgx, sgy) = toGrid(sx, sy)
    val (egx, egy) = toGrid(ex, ey)

    val startMeters = toMeters(sgx, sgy)
    val endMeters = toMeters(egx, egy)
    if (isBlocked(startMeters.first, startMeters.second) || isBlocked(endMeters.first, endMeters.second)) {
        return TrajectoryPlanResult(
            path = emptyList(),
            usedGridSizeMeters = usedGrid,
            error = "起点或终点落在禁飞区内（含避障边距）"
        )
    }

    val pathGrid = aStarGrid(
        width = width,
        height = height,
        startX = sgx,
        startY = sgy,
        endX = egx,
        endY = egy,
        isBlocked = { x, y ->
            val (mx, my) = toMeters(x, y)
            isBlocked(mx, my)
        }
    )

    if (pathGrid.isEmpty()) {
        return TrajectoryPlanResult(
            path = emptyList(),
            usedGridSizeMeters = usedGrid,
            error = "未找到可行路径（可尝试减小避障边距/增大网格）"
        )
    }

    val pathMeters = pathGrid.map { (gx, gy) -> toMeters(gx, gy) }
    val simplified = if (smooth) simplifyPathMeters(pathMeters, usedGrid, ::isBlocked) else pathMeters
    val sampled = resamplePathMeters(simplified, segmentLengthMeters)

    val pathLatLon = sampled.map { (x, y) ->
        val (lat, lon) = projector.toLatLon(x, y)
        RoutePoint(lat = lat, lon = lon)
    }

    return TrajectoryPlanResult(
        path = pathLatLon,
        usedGridSizeMeters = usedGrid,
        error = null
    )
}

private class LocalProjector(
    private val lat0: Double,
    private val lon0: Double
) {
    private val metersPerDegLat = 111320.0
    private val metersPerDegLon = 111320.0 * cos(lat0 * Math.PI / 180.0)

    fun toXYMeters(lat: Double, lon: Double): Pair<Double, Double> {
        val x = (lon - lon0) * metersPerDegLon
        val y = (lat - lat0) * metersPerDegLat
        return x to y
    }

    fun toLatLon(xMeters: Double, yMeters: Double): Pair<Double, Double> {
        val lat = lat0 + (yMeters / metersPerDegLat)
        val lon = lon0 + (xMeters / metersPerDegLon)
        return lat to lon
    }
}

private fun aStarGrid(
    width: Int,
    height: Int,
    startX: Int,
    startY: Int,
    endX: Int,
    endY: Int,
    isBlocked: (Int, Int) -> Boolean
): List<Pair<Int, Int>> {
    data class Node(val x: Int, val y: Int, val g: Double, val f: Double)

    val dirs = listOf(
        -1 to -1, 0 to -1, 1 to -1,
        -1 to 0, 1 to 0,
        -1 to 1, 0 to 1, 1 to 1
    )

    fun heuristic(x: Int, y: Int): Double {
        val dx = (endX - x).toDouble()
        val dy = (endY - y).toDouble()
        return sqrt(dx * dx + dy * dy)
    }

    val open = java.util.PriorityQueue<Node>(compareBy<Node> { it.f }.thenBy { it.g })
    val cameFrom = Array(height) { Array<Pair<Int, Int>?>(width) { null } }
    val gScore = Array(height) { DoubleArray(width) { Double.POSITIVE_INFINITY } }
    val inOpen = Array(height) { BooleanArray(width) { false } }

    gScore[startY][startX] = 0.0
    open.add(Node(startX, startY, g = 0.0, f = heuristic(startX, startY)))
    inOpen[startY][startX] = true

    var iterations = 0
    val maxIterations = width * height * 20

    while (open.isNotEmpty() && iterations < maxIterations) {
        iterations++
        val cur = open.poll()
        inOpen[cur.y][cur.x] = false

        if (cur.x == endX && cur.y == endY) {
            val path = mutableListOf<Pair<Int, Int>>()
            var cx = endX
            var cy = endY
            path.add(cx to cy)
            while (!(cx == startX && cy == startY)) {
                val prev = cameFrom[cy][cx] ?: break
                cx = prev.first
                cy = prev.second
                path.add(cx to cy)
            }
            path.reverse()
            return path
        }

        for ((dx, dy) in dirs) {
            val nx = cur.x + dx
            val ny = cur.y + dy
            if (nx !in 0 until width || ny !in 0 until height) continue
            if (isBlocked(nx, ny)) continue

            val stepCost = if (dx == 0 || dy == 0) 1.0 else 1.41421356237
            val tentativeG = gScore[cur.y][cur.x] + stepCost
            if (tentativeG < gScore[ny][nx]) {
                cameFrom[ny][nx] = cur.x to cur.y
                gScore[ny][nx] = tentativeG
                val f = tentativeG + heuristic(nx, ny)
                if (!inOpen[ny][nx]) {
                    open.add(Node(nx, ny, tentativeG, f))
                    inOpen[ny][nx] = true
                } else {
                    open.add(Node(nx, ny, tentativeG, f))
                }
            }
        }
    }

    return emptyList()
}

private fun simplifyPathMeters(
    pts: List<Pair<Double, Double>>,
    gridMeters: Double,
    isBlocked: (Double, Double) -> Boolean
): List<Pair<Double, Double>> {
    if (pts.size <= 2) return pts

    fun segmentClear(a: Pair<Double, Double>, b: Pair<Double, Double>): Boolean {
        val (ax, ay) = a
        val (bx, by) = b
        val dx = bx - ax
        val dy = by - ay
        val dist = sqrt(dx * dx + dy * dy)
        if (dist <= 0.0) return true
        val step = max(3.0, gridMeters / 2.0)
        val steps = max(1, ceil(dist / step).toInt())
        for (i in 0..steps) {
            val t = i.toDouble() / steps.toDouble()
            val x = ax + dx * t
            val y = ay + dy * t
            if (isBlocked(x, y)) return false
        }
        return true
    }

    val reduced = mutableListOf<Pair<Double, Double>>()
    reduced.add(pts.first())
    var anchorIdx = 0
    var probeIdx = 2
    while (probeIdx < pts.size) {
        val anchor = pts[anchorIdx]
        val probe = pts[probeIdx]
        if (segmentClear(anchor, probe)) {
            probeIdx++
        } else {
            val keep = pts[probeIdx - 1]
            reduced.add(keep)
            anchorIdx = probeIdx - 1
            probeIdx = anchorIdx + 2
        }
    }
    reduced.add(pts.last())
    return reduced
}

private fun resamplePathMeters(
    pts: List<Pair<Double, Double>>,
    segmentLengthMeters: Double
): List<Pair<Double, Double>> {
    if (pts.size <= 2) return pts
    if (segmentLengthMeters <= 0.0) return pts

    val out = mutableListOf<Pair<Double, Double>>()
    out.add(pts.first())
    var carry = 0.0

    for (i in 1 until pts.size) {
        val (x0, y0) = out.last()
        val (x1, y1) = pts[i]
        val dx = x1 - x0
        val dy = y1 - y0
        val dist = sqrt(dx * dx + dy * dy)
        if (dist <= 0.0) continue

        var traveled = 0.0
        while (carry + (dist - traveled) >= segmentLengthMeters) {
            val need = segmentLengthMeters - carry
            traveled += need
            val t = traveled / dist
            out.add(x0 + dx * t to y0 + dy * t)
            carry = 0.0
        }
        carry += (dist - traveled)
    }

    if (out.last() != pts.last()) out.add(pts.last())
    return out
}

private fun polylineLengthMeters(points: List<RoutePoint>): Double {
    if (points.size < 2) return 0.0
    var sum = 0.0
    for (i in 1 until points.size) {
        sum += haversineMeters(points[i - 1].lat, points[i - 1].lon, points[i].lat, points[i].lon)
    }
    return sum
}

private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371000.0
    val dLat = (lat2 - lat1) * Math.PI / 180.0
    val dLon = (lon2 - lon1) * Math.PI / 180.0
    val a = sin(dLat / 2.0).pow(2.0) +
        cos(lat1 * Math.PI / 180.0) * cos(lat2 * Math.PI / 180.0) * sin(dLon / 2.0).pow(2.0)
    val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
    return r * c
}

private fun formatLatLng(lat: Double, lon: Double): String {
    return "${lat.format(8)}, ${lon.format(8)}"
}

private fun Double.format(digits: Int): String = "%.${digits}f".format(this)

private fun Float.format(digits: Int): String = "%.${digits}f".format(this)

private fun formatDurationSeconds(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0L)
    val mm = (s / 60L).toInt()
    val ss = (s % 60L).toInt()
    return "%02d:%02d".format(mm, ss)
}

@Composable
fun ControlPage(
    connected: Boolean,
    ready: Boolean,
    activationStateText: String,
    bindingStateText: String,
    accountStateText: String,
    msg: String,
    onTakeoffClick: () -> Unit,
    onLandClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    Card { Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (connected) "已连接" else "未连接")
        Text(if (ready) "飞控就绪" else "飞控未就绪")
        if (accountStateText.isNotEmpty()) Text(accountStateText)
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onTakeoffClick, enabled = connected && ready) { Text("起飞") }
            Button(onClick = onLandClick, enabled = connected && ready) { Text("降落") }
        }
        Divider()
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRegisterClick) { Text("注册") }
            OutlinedButton(onClick = onLoginClick) { Text("登录") }
        }
    } }

    Card { Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("系统消息")
        if (msg.isNotEmpty()) Text(msg)
    } }
}

@Composable
fun FPVPage(
    onSurfaceAvailable: (SurfaceTexture, Int, Int) -> Unit,
    onSurfaceDestroyed: (SurfaceTexture) -> Unit,
    onTakePhoto: () -> Unit,
    onStartRecord: () -> Unit,
    onStopRecord: () -> Unit,
    onGimbalPitchChange: (Float) -> Unit
) {
    // FPV Widget
    Card(modifier = Modifier.fillMaxWidth().height(300.dp)) {
        FPVWidget(
            onSurfaceCreated = onSurfaceAvailable,
            onSurfaceDestroyed = onSurfaceDestroyed,
            onSurfaceChanged = { _, _, _ -> }
        )
    }

    // Camera & Gimbal Controls
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("相机与云台")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTakePhoto) { Text("拍照") }
                Button(onClick = onStartRecord) { Text("录像") }
                Button(onClick = onStopRecord) { Text("停录") }
            }
            Text("云台俯仰")
            var sliderPosition by remember { mutableFloatStateOf(0f) }
            Slider(
                value = sliderPosition,
                onValueChange = { 
                    sliderPosition = it
                    onGimbalPitchChange(it) 
                },
                valueRange = -90f..30f
            )
        }
    }
}

@Composable
fun FPVWidget(
    onSurfaceCreated: (SurfaceTexture, Int, Int) -> Unit,
    onSurfaceDestroyed: (SurfaceTexture) -> Unit,
    onSurfaceChanged: (SurfaceTexture, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    onTextureViewReady: (TextureView) -> Unit = {}
) {
    AndroidView(
        factory = { context ->
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        onSurfaceCreated(surface, width, height)
                        onTextureViewReady(this@apply)
                    }
                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                        onSurfaceChanged(surface, width, height)
                    }
                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        onSurfaceDestroyed(surface)
                        return true
                    }
                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
                onTextureViewReady(this)
            }
        },
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    )
}
