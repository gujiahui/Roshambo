# Roshambo 项目优化方案

## 1. 摄像头长时间调用导致系统卡死问题

### 1.1 问题根因分析

| 问题点 | 原因 | 影响 |
|--------|------|------|
| **Bitmap 泄漏** | 手写 `toBitmap()` 在 `degrees==0` 时返回的 bitmap 从不 recycle | 内存持续增长，最终 OOM |
| **Landmarker 重复创建** | `createLandmarker()` 在 analyze 中对 null landmarker 反复尝试创建 | 每帧都尝试创建，失败时导致 native 崩溃 |
| **单线程执行器瓶颈** | `Executors.newSingleThreadExecutor()` + 阻塞式 `detectForVideo` | 慢帧积压，最终卡死 |
| **缺少超时保护** | 无单帧超时机制 | 某帧处理异常则阻塞后续所有帧 |
| **内存阈值误触发** | `LOW_MEMORY_THRESHOLD_MB=100` 使用 `availMem`（系统整体可用内存） | 频繁误触发异常，导致相机永久关闭 |
| **相机恢复失效** | `onAnomaly` 调用 `analyzer.release()` 后 `cameraRestartKey++`，但 `AndroidView` 没有 `key` 关联 | 相机永久关闭，再也回不来 |

### 1.2 修复方案

#### 1.2.1 使用官方 `ImageProxy.toBitmap()` 替代手写实现

camera-core 1.3.4 已内置官方 `ImageProxy.toBitmap()`（1.3.0 起），直接用它，零维护、零错误。

```kotlin
@OptIn(ExperimentalUnsafeOptIn::class)
private fun analyze(imageProxy: ImageProxy) {
    // ...
    val src = imageProxy.toBitmap()
    val degrees = imageProxy.imageInfo.rotationDegrees
    val bitmap = if (degrees == 0) src else rotateUpright(src, degrees)
    // ...
    if (degrees != 0) bitmap.recycle()
}
```

#### 1.2.2 删除内存监控，保留帧丢弃和看门狗检测

`availMem` 不代表本 app 内存压力，删除 `LOW_MEMORY` 异常类型，只保留 `FRAME_DROP_STORM` 和 `WATCHDOG_TIMEOUT`。

```kotlin
enum class AnomalyType { FRAME_DROP_STORM, WATCHDOG_TIMEOUT }
```

#### 1.2.3 修复相机自动恢复机制

- `onAnomaly` 回调中删除 `analyzer.release()`，只保留 `cameraRestartKey++`
- `AndroidView` 添加 `key = cameraRestartKey` 使其在异常时重建
- `LaunchedEffect(cameraRestartKey)` 中调用 `analyzer.rebind()` 而非 `setActive(false)`
- 添加 `rebind()` 方法：重置 `closed=false`，重建 executor，重新 `bind()`

#### 1.2.4 添加 `rebind()` 方法

```kotlin
fun rebind(lifecycleOwner: LifecycleOwner, previewView: PreviewView) {
    if (!closed.get()) return
    closed.set(false); active.set(true); processing.set(false)
    lastAnalyzeMs = 0L; consecutiveDrops.set(0)
    lastFrameProcessedMs.set(System.currentTimeMillis())
    analysisExecutor.shutdown()
    analysisExecutor = Executors.newSingleThreadExecutor()
    mainHandler.removeCallbacks(watchdogRunnable)
    mainHandler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS)
    bind(lifecycleOwner, previewView)
}
```

## 2. 音频部分

### 2.1 问题根因

| 问题点 | 原因 | 影响 |
|--------|------|------|
| **主线程阻塞** | `MediaPlayer.create()` 同步解码 3 个文件在 init 中执行 | 首次组合可能轻微卡顿 |
| **加载状态未追踪** | `pool.setOnLoadCompleteListener` 是空实现 | chime 播放依赖隐含假设 |

### 2.2 修复方案

- 使用 `MediaPlayer()` + `setDataSource` + `prepareAsync` 异步加载
- 使用 `setOnPreparedListener` 追踪加载状态
- 语音文件使用 `MediaPlayer` 播放（对 >1s/较大文件比 SoundPool 更稳）
- `onCompletion` 里 `seekTo(0)` 实现可重播

## 3. 资源重生成

### 3.1 音效文件

使用 `gen_sfx.py` 生成多种音效：
- 倒计时提示音（chime）
- 出拳音效（whoosh）
- 胜利/失败/平局语音（童声变调版）
- 环境音（ambient）

### 3.2 图片资源

- 手势图标使用纯 Compose 矢量绘制（`GestureGlyph`），不依赖 PNG
- 删除 15 个死资源 PNG（`ic_gesture_rock/scissors/paper.png` × 5 密度目录）

## 4. 当前代码架构

### 4.1 HandGestureAnalyzer.kt

- 单线程 executor + `KEEP_ONLY_LATEST` 背压策略
- 看门狗定时器（2s 间隔，5s 超时）
- 连续帧丢弃检测（30 帧阈值）
- 官方 `ImageProxy.toBitmap()` + 手动旋转
- `bind()` / `release()` / `rebind()` 三方法管理生命周期

### 4.2 DuelScreen.kt

- `cameraRestartKey` 驱动相机重建
- `previewViewRef` 存储 PreviewView 引用供 rebind 使用
- `DisposableEffect` 在组件销毁时释放资源
- `LaunchedEffect(cameraRestartKey)` 触发 rebind

### 4.3 SoundManager.kt

- `prepareAsync()` 异步加载
- `setOnPreparedListener` 追踪加载状态
- `MediaPlayer` 播放语音，`SoundPool` 播放短音效

## 5. 构建说明

```bash
cd D:\joloDeploy\gradle
.\bin\gradle.bat assembleDebug
```

输出 APK: `app\build\outputs\apk\debug\app-debug.apk`
