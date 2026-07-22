# FLY

`FLY` 是一个基于 Android 的无人机地面站应用，面向 DJI 飞控设备，集成了飞行控制、航线规划、地图展示、相册管理、相机拍照录像以及本地目标识别能力。

当前工程以 `Jetpack Compose + DJI Mobile SDK 4.x + 高德地图 SDK + TensorFlow Lite` 为核心技术栈，主业务逻辑集中在 `MainActivity.kt`，适合在现有单 Activity 架构上继续迭代。

## 主要能力

- 无人机连接、激活状态展示与飞行界面切换
- 实时飞行状态展示，包括电量、卫星数、飞行模式、高度、速度
- FPV 与地图双视图飞行页面，支持主副窗口切换
- 航线规划与航点任务上传、暂停、恢复
- 轨迹规划与区域航线编辑
- 相机拍照、开始录像、停止录像、存储位置切换
- 媒体相册浏览
- 基于 TensorFlow Lite 的本地目标检测

## 技术栈

- Android Gradle Plugin: `8.6.0`
- Kotlin: `1.9.0`
- Android SDK:
  - `compileSdk 34`
  - `targetSdk 33`
  - `minSdk 24`
- UI: `Jetpack Compose`、`Material 3`
- 地图: `高德地图 3D Map SDK 9.8.3`
- 无人机 SDK: `DJI Mobile SDK 4.16.4`
- 模型推理: `TensorFlow Lite 2.12.0`
- 图片加载: `Coil 2.6.0`
- MultiDex: `androidx.multidex`

版本来源可见 `gradle/libs.versions.toml` 与 `app/build.gradle`。

## 运行环境

- Windows 开发环境已验证可构建
- JDK 17 或 Android Studio 自带 JBR
- Android Studio / Trae / 其他支持 Gradle 的 IDE
- 物理 Android 设备
- DJI 遥控器 / 飞机硬件

## 工程结构

```text
FLY/
|-- app/
|   |-- src/main/
|   |   |-- java/com/yuanjieflycontr/
|   |   |   |-- MainActivity.kt
|   |   |   |-- DroneApplication.kt
|   |   |   |-- GpsUtils.kt
|   |   |   `-- ml/YoloTFLite.kt
|   |   |-- assets/
|   |   |   `-- best_ncnn_model/
|   |   `-- AndroidManifest.xml
|   `-- build.gradle
|-- gradle/
|   |-- libs.versions.toml
|   `-- wrapper/
|-- build.gradle
|-- settings.gradle
|-- gradlew
`-- gradlew.bat
```

## 架构说明

### 1. 应用入口

- `DroneApplication.kt`
  - 负责 `MultiDex` 初始化
  - 调用 `com.secneo.sdk.Helper.install(this)` 完成 DJI SDK 运行前准备
  - 调用高德隐私合规接口 `MapsInitializer.updatePrivacyShow/Agree`

- `MainActivity.kt`
  - 项目唯一主页面入口
  - 承担 DJI 设备连接、飞控数据订阅、相机控制、任务管理和 Compose UI 组装

### 2. UI 组织

`MainActivity.kt` 中使用 `enum class Screen` 组织页面：

- `Home`
- `Flight`
- `RoutePlan`
- `Profile`
- `Album`
- `TrajectoryPlan`

其中：

- `MainScreen()` 负责页面状态切换
- `FlightPage()` 是飞行核心页面
- `RoutePlan` 和 `TrajectoryPlan` 负责航线与区域编辑

### 3. 地图与坐标

- 地图组件使用高德 `MapView + AMap`
- 飞机原始坐标通过 `GpsUtils.gps84ToGcj02()` 转换为高德地图使用的 GCJ-02 坐标
- 飞行页面支持：
  - 航线绘制
  - 已执行航段高亮
  - 无人机位置标记
  - 跟随无人机视角

### 4. 飞控与任务

核心飞行任务数据结构定义在 `MainActivity.kt`：

- `RoutePoint`
  - `lat`
  - `lon`
  - `altitudeMeters`
  - `speedMetersPerSecond`

- `NoFlyZone`
- `TrajectoryPlanMode`
- `AreaEditMode`

航点任务通过 `startWaypointMission(points: List<RoutePoint>)` 发起，当前逻辑包含：

- 航点列表构建
- 卫星数检查
- 任务上传与启动流程
- 任务暂停、继续控制

### 5. 相机控制

相机相关控制逻辑位于 `MainActivity.kt`：

- `prepareCameraMode(...)`
- `takePhoto()`
- `startRecord()`

当前实现兼容部分机型上“切模式失败但直接拍照/录像仍可执行”的场景，UI 侧会同步显示录像状态与录制时长。

### 6. 模型推理

目标检测封装位于 `app/src/main/java/com/yuanjieflycontr/ml/YoloTFLite.kt`：

- `YoloTFLite`
  - 从 `assets` 读取 `.tflite` 模型
  - 执行输入预处理
  - 解码输出
  - 执行 NMS

当前版本仅支持 `TFLite` 模型，不直接支持 `NCNN` 推理。

## 模型支持说明

工程当前会在 `assets` 根目录优先查找：

- `yolov8n_fp16.tflite`

如果该文件不存在，会继续尝试查找任意 `.tflite` 文件。

当前仓库中已放置：

- `app/src/main/assets/best_ncnn_model/model.ncnn.param`
- `app/src/main/assets/best_ncnn_model/model.ncnn.bin`

但这组文件仅作为模型资源留存，当前代码不会直接加载它们。若要启用目标识别，仍需提供 `.tflite` 模型文件。

建议做法：

1. 将训练产物导出为 `.tflite`
2. 放入 `app/src/main/assets/`
3. 保持文件名为 `yolov8n_fp16.tflite`，或至少保证 assets 根目录存在一个 `.tflite`

## 关键配置

### 1. DJI Key

`AndroidManifest.xml` 中配置了：

```xml
<meta-data
    android:name="com.dji.sdk.API_KEY"
    android:value="8c98fa74b036d522abd3b744" />
```

发布前应替换为你自己的有效 Key。

### 2. 高德 Key

`AndroidManifest.xml` 中配置了：

```xml
<meta-data
    android:name="com.amap.api.v2.apikey"
    android:value="2611acc2e4ed2ab36db299323d43f12a" />
```

发布前应替换为你自己的地图服务 Key。

### 3. 权限

Manifest 已声明的关键权限包括：

- 蓝牙扫描与连接
- 网络访问
- 精确/粗略定位
- 外部存储读写
- 媒体读取
- USB Accessory

Android 12+ 的蓝牙权限和 Android 13+ 的媒体权限仍需要在运行时正确申请。

## 构建说明

### 推荐构建方式

本工程已经调整为优先使用项目内 Gradle 缓存与构建目录，避免系统全局缓存污染：

- 项目内 Gradle Home: `D:\FLY\.gradle_user_home`
- 项目构建输出目录: `D:\FLY\.project-build`

推荐直接执行：

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
```

### 重要说明

- 已不建议依赖 `C:\Users\Administrator\.gradle` 这类全局缓存目录
- 若同时打开多个 IDE，可能出现 Java/Gradle 进程占用构建产物的情况
- 当前工程已经把默认输出从 `app/build` 挪到 `.project-build`，用来降低锁文件概率

## 安装与启动

### 1. 克隆项目

```powershell
git clone <your-repo-url>
cd FLY
```

### 2. 构建 Debug 包

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
```

### 3. 安装到设备

可以通过 Android Studio 直接运行，也可以通过 `adb` 安装生成的 APK。

## 开发建议

### 推荐修改入口

- 飞行逻辑与大部分业务状态：`MainActivity.kt`
- 模型推理：`ml/YoloTFLite.kt`
- 坐标转换：`GpsUtils.kt`
- Application 初始化：`DroneApplication.kt`

### 适合后续拆分的模块

当前业务大量集中在 `MainActivity.kt`，后续建议逐步拆分为：

- `dji/` 设备接入层
- `mission/` 航线与任务层
- `camera/` 相机控制层
- `map/` 地图展示层
- `ml/` 识别推理层
- `ui/` 页面与组件层

## 已知限制

- 当前目标识别仅支持 `TFLite`，不支持直接加载 `NCNN`
- 业务逻辑集中在单个 Activity 中，维护成本较高
- 某些 DJI 固件版本上，相机模式切换存在兼容性差异
- 航线上传依赖飞控状态、卫星数量和设备连接情况
- 当前测试覆盖较少，主要依赖真机联调

## 常见问题

### 1. 构建时报 Gradle transforms 或 metadata 错误

优先使用项目封装后的命令：

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
```

不要优先依赖系统全局 `Gradle User Home`。

### 2. 构建时报 `R.jar` 删除失败

通常是 IDE 的 Java/Gradle 后台进程锁住了构建文件。当前工程已通过以下方式降低该问题：

- 使用项目内 `.gradle_user_home`
- 使用 `.project-build` 作为构建输出目录

如果仍出现，可关闭多余 IDE 后重试。

### 3. 目标识别提示找不到模型

请确认：

- `app/src/main/assets/` 根目录下存在 `.tflite`
- 文件不是放在子目录而是根目录
- 模型输入输出格式与 `YoloTFLite.kt` 的解码逻辑兼容

## 后续建议

- 将 `MainActivity.kt` 拆分为 ViewModel + Service + Compose Screen
- 引入统一日志模块，方便排查 DJI 回调与任务错误
- 为航线任务、相机控制、模型加载增加更清晰的错误码与状态提示
- 若需要使用 NCNN，单独接入 Android NCNN 运行时并新增推理适配层

## 许可证与注意事项

本项目依赖 DJI SDK、高德地图 SDK、TensorFlow Lite 等第三方组件，使用时请遵守各自许可证与平台接入规范。

