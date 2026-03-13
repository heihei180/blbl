package blbl.cat3399

import android.app.Application
import android.os.Build
import blbl.cat3399.core.theme.LauncherAliasManager
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.log.CrashTracker
import blbl.cat3399.core.emote.ReplyEmotePanelRepository
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.net.WebCookieMaintainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BlblApp : Application() {

    // 持有一个应用级别的协程作用域：
    // SupervisorJob：子协程失败不会影响父协程和其他兄弟协程
    // Dispatchers.IO：专用于 IO 密集型任务（网络请求、文件读写等）
    // 用于执行不需要立即完成的后台任务（如 Cookie 维护、表情预热）
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 1. 保存单例引用
        instance = this
        // 2. 初始化日志
        AppLog.init(this)
        // 3. 初始化崩溃上报
        CrashTracker.install(this)

        // 4. 记录启动信息（版本号、API 级别、设备型号、ABI 等）
        AppLog.i(
            "Startup",
            "app=${BuildConfig.VERSION_NAME} api=${Build.VERSION.SDK_INT} device=${Build.MANUFACTURER} ${Build.MODEL} abi=${Build.SUPPORTED_ABIS.firstOrNull().orEmpty()}",
        )
        AppLog.i("BlblApp", "onCreate")

        // 5. 初始化 BiliClient
        BiliClient.init(this)

        // 6. 同步桌面图标别名（可能用于多主题图标切换）
        LauncherAliasManager.sync(this)

        // 7. 异步维护 Web Cookie（每日自动维护）
        appScope.launch {
            runCatching { WebCookieMaintainer.ensureDailyMaintenance() }
                .onFailure { AppLog.w("BlblApp", "daily maintenance failed", it) }
        }
        // 8. 异步预热回复表情面板
        appScope.launch {
            runCatching { ReplyEmotePanelRepository.warmup(this@BlblApp) }
                .onFailure { AppLog.w("BlblApp", "reply emote warmup failed", it) }
        }
    }

    // 单例引用
    companion object {
        @JvmStatic
        lateinit var instance: BlblApp
            private set

        // 提供 IO 协程作用域，用于后台任务
        fun launchIo(block: suspend CoroutineScope.() -> Unit) {
            instance.appScope.launch(block = block)
        }
    }
}
