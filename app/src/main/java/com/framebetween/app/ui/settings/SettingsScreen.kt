package com.framebetween.app.ui.settings
import com.framebetween.app.core.L10n

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.framebetween.app.R
import com.framebetween.app.core.AiChatStore
import com.framebetween.app.core.AiClient
import com.framebetween.app.core.AiProvider
import com.framebetween.app.core.AiSettings
import com.framebetween.app.core.AppBrand
import com.framebetween.app.core.AppState
import com.framebetween.app.core.CodecCapabilities
import com.framebetween.app.core.CodecEntry
import com.framebetween.app.core.DeviceCodecReport
import com.framebetween.app.core.FFmpegEngine
import com.framebetween.app.core.LibrarySettings
import com.framebetween.app.core.MediaLibrary
import com.framebetween.app.core.MediaRepository
import com.framebetween.app.core.OutputTarget
import com.framebetween.app.core.Settings
import com.framebetween.app.core.SettingsBackup
import com.framebetween.app.core.ThemeMode
import com.framebetween.app.ui.common.ProviderLogo
import com.framebetween.app.ui.common.ScreenTopBar
import com.framebetween.app.ui.common.SectionCard
import com.framebetween.app.util.ApiKeyMask
import com.framebetween.app.util.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val GITHUB_PROJECT = "https://github.com/Snowix/FrameBetween"
private const val GITHUB_AUTHOR = "https://github.com/Snowix"

private fun openLink(context: Context, url: String) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure { AppState.notify(L10n.t("未找到可打开链接的应用")) }
}

private fun appVersion(context: Context): String = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(0)
        ).versionName
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }
}.getOrNull() ?: "1.0.0"

/** 设置页层次：主页 →【高级】（含日志）/【关于】。 */
enum class SettingsPage { MAIN, ADVANCED, ABOUT, LOGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenFolderSettings: () -> Unit,
    page: SettingsPage = SettingsPage.MAIN,
    onOpenPage: (SettingsPage) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val version = remember(context) { appVersion(context) }

    val theme by Settings.theme.collectAsState()
    val language by Settings.language.collectAsState()
    val background by Settings.background.collectAsState()
    val target by Settings.outputTarget.collectAsState()
    val customTree by Settings.customTree.collectAsState()
    val aiProvider by AiSettings.provider.collectAsState()
    val aiBaseUrl by AiSettings.baseUrl.collectAsState()
    val aiModel by AiSettings.model.collectAsState()
    val aiKey by AiSettings.apiKey.collectAsState()
    val hardwareCodec by Settings.hardwareCodec.collectAsState()

    var showAi by remember { mutableStateOf(false) }
    var permissionGranted by remember { mutableStateOf(MediaLibrary.hasPermission(context)) }
    var allFilesGranted by remember { mutableStateOf(MediaLibrary.hasAllFilesAccess()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionGranted = MediaLibrary.hasPermission(context) }
    // 「所有文件访问权限」只能在系统设置页开关，返回时重新读取状态并重扫媒体库
    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        allFilesGranted = MediaLibrary.hasAllFilesAccess()
        permissionGranted = MediaLibrary.hasPermission(context)
        LibrarySettings.rescan()
    }

    var showTheme by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var showOutput by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showClear by remember { mutableStateOf(false) }
    var showDeps by remember { mutableStateOf(false) }
    var showTechStack by remember { mutableStateOf(false) }
    var showCodecs by remember { mutableStateOf(false) }
    var codecReport by remember { mutableStateOf<DeviceCodecReport?>(null) }
    var codecLoading by remember { mutableStateOf(false) }
    var cacheBytes by remember { mutableStateOf(0L) }
    var showReward by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            Settings.setCustomTree(uri.toString())
            Settings.setOutputTarget(OutputTarget.CUSTOM)
            AppState.notify(L10n.t("已设置自定义输出目录"))
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = DocumentFile.fromTreeUri(context, uri) ?: error("tree")
                    val doc = dir.createFile(
                        "text/xml",
                        SettingsBackup.fileName().substringBeforeLast('.')
                    ) ?: error("createFile")
                    context.contentResolver.openOutputStream(doc.uri)?.use { out ->
                        out.write(SettingsBackup.export(context).toByteArray(Charsets.UTF_8))
                    } ?: error("stream")
                    true
                }.getOrDefault(false)
            }
            AppState.notify(
                if (ok) L10n.t("设置已导出") else L10n.t("导出失败，请重试")
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val outcome: Result<Int> = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.use { input ->
                        val sb = StringBuilder()
                        val buf = CharArray(8192)
                        var count = 0
                        val limit = 4 * 1024 * 1024
                        val reader = input.bufferedReader()
                        while (true) {
                            val n = reader.read(buf)
                            if (n < 0) break
                            count += n
                            if (count > limit) error(L10n.t("备份文件过大"))
                            sb.append(buf, 0, n)
                        }
                        sb.toString()
                    } ?: error("stream")
                    SettingsBackup.import(context, text).getOrThrow()
                }
            }
            if (outcome.isSuccess) {
                Settings.init(context)
                LibrarySettings.init(context)
                AiSettings.init(context)
                AiChatStore.init(context)
                LibrarySettings.rescan()
                AppState.notify(L10n.t("设置已恢复"))
                activity?.recreate()
            } else {
                AppState.notify(
                    outcome.exceptionOrNull()?.message ?: L10n.t("导入失败")
                )
            }
        }
    }

    // 能力探测会实际执行几条 FFmpeg 信息命令，放到 IO 线程；结果缓存到组合状态里，
    // 同一次打开只探测一次。
    LaunchedEffect(showCodecs) {
        if (!showCodecs || codecReport != null) return@LaunchedEffect
        codecLoading = true
        try {
            codecReport = withContext(Dispatchers.IO) { CodecCapabilities.report() }
        } finally {
            codecLoading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        val (pageTitle, pageSubtitle) = when (page) {
            SettingsPage.MAIN -> L10n.t("设置") to L10n.t("偏好、存储与关于")
            SettingsPage.ADVANCED -> L10n.t("高级") to L10n.t("权限、编解码器、备份与日志")
            SettingsPage.ABOUT -> L10n.t("关于") to L10n.t("版本与简介、项目与许可、捐赠")
            SettingsPage.LOGS -> L10n.t("详细日志") to L10n.t("实时日志与导出")
        }
        ScreenTopBar(
            pageTitle,
            pageSubtitle,
            onBack = if (page == SettingsPage.MAIN) null else onBack
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (page) {
                SettingsPage.MAIN -> {
                    BrandHeader()

            // 外观放最前，其余顺序：外观 → 文件管理 → AI 助手 → 高级与关于。
            SectionCard(L10n.t("外观")) {
                SettingsRow(
                    Icons.Default.DarkMode,
                    L10n.t("主题样式"),
                    themeLabel(theme),
                    onClick = { showTheme = true }
                )
                SettingsRow(
                    Icons.Default.Language,
                    L10n.t("界面语言"),
                    if (language == "zh") L10n.t("简体中文") else "English",
                    onClick = { showLanguage = true }
                )
            }

            SectionCard(L10n.t("文件管理")) {
                SettingsRow(
                    Icons.Default.Folder,
                    L10n.t("更改默认输出目录"),
                    outputDetail(target, customTree),
                    onClick = { showOutput = true }
                )
                SettingsRow(
                    Icons.Default.Folder,
                    L10n.t("文件夹设置"),
                    L10n.t("内容发现与过滤、目录规则、媒体库维护"),
                    onClick = onOpenFolderSettings
                )
                SettingsRow(
                    Icons.Default.DeleteSweep,
                    L10n.t("清除软件缓存"),
                    L10n.t("回收输入副本、临时产物与已完成任务的输出"),
                    onClick = {
                        cacheBytes = MediaRepository.cacheSize()
                        showClear = true
                    }
                )
            }

            SectionCard(L10n.t("AI 助手"), icon = Icons.Default.SmartToy) {
                SettingsRow(
                    Icons.Default.SmartToy,
                    L10n.t("模型提供商与 API"),
                    L10n.t(
                        "%1\$s · %2\$s",
                        AiSettings.presets.firstOrNull { it.id == aiProvider }?.label ?: "Custom",
                        aiModel
                    ),
                    onClick = { showAi = true },
                    logo = aiProvider
                )
                Text(
                    L10n.t("支持云端与本地模型（Ollama、LM Studio 等）。密钥仅存本机且本地模型可留空；命令行页可用「求助 AI」生成命令建议。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
                    SectionCard(L10n.t("高级与关于")) {
                        SettingsRow(
                            Icons.Default.Tune,
                            L10n.t("高级"),
                            L10n.t("权限、编解码器、备份与恢复、日志"),
                            onClick = { onOpenPage(SettingsPage.ADVANCED) }
                        )
                        SettingsRow(
                            Icons.Default.AutoAwesome,
                            L10n.t("关于"),
                            L10n.t("版本、依赖库、GitHub 与捐赠"),
                            onClick = { onOpenPage(SettingsPage.ABOUT) }
                        )
                    }
                }

                SettingsPage.ADVANCED -> {
            SectionCard(L10n.t("权限"), icon = Icons.Default.Security) {
                SettingsRow(
                    Icons.Default.Notifications,
                    L10n.t("允许后台任务"),
                    L10n.t("开启后使用前台服务保活；关闭后切到后台可能被系统暂停"),
                    trailing = {
                        Switch(
                            checked = background,
                            onCheckedChange = { Settings.setBackground(it) }
                        )
                    }
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    L10n.t("授权访问系统音乐与视频后，媒体库可浏览系统文件与封面缩略图。FFmpeg 会优先直接读取原始文件路径，只有系统不允许直接访问时才复制一份到应用缓存。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                SettingsRow(
                    Icons.Default.Lock,
                    L10n.t("媒体读取权限"),
                    if (permissionGranted) L10n.t("已授权 · 视频 / 音频 / 图片") else L10n.t("未授权 · 仅能浏览应用自身产物")
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { permissionLauncher.launch(MediaLibrary.requiredPermissions()) },
                        modifier = Modifier.weight(1f)
                    ) { Text(L10n.t("授予权限")) }
                    OutlinedButton(
                        onClick = { MediaLibrary.openPermissionSettings(context) },
                        modifier = Modifier.weight(1f)
                    ) { Text(L10n.t("系统设置")) }
                }
                Spacer(Modifier.height(14.dp))
                SettingsRow(
                    Icons.Default.Folder,
                    L10n.t("所有文件访问权限"),
                    if (allFilesGranted) L10n.t("已授权 · 可直读任意目录") else L10n.t("未授权 · 仅能访问系统索引的媒体")
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            allFilesLauncher.launch(
                                Intent(
                                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        } else {
                            MediaLibrary.openPermissionSettings(context)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (allFilesGranted) L10n.t("管理所有文件访问权限")
                        else L10n.t("去开启所有文件访问权限")
                    )
                }
                Text(
                    L10n.t("Android 11+ 需在系统设置中手动开启。开启后可直接读写任意目录，媒体库能扫描更多文件夹，也支持文件夹重命名与删除。文件仍只在本机处理，不会上传。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard(L10n.t("设备编解码器支持"), icon = Icons.Default.Bolt) {
                SettingsRow(
                    Icons.Default.Hub,
                    L10n.t("查看设备能力"),
                    L10n.t("硬件加速与软件解码能力、FFmpeg 构建能力"),
                    onClick = { showCodecs = true }
                )
                SettingsRow(
                    Icons.Default.Bolt,
                    L10n.t("硬件加速"),
                    L10n.t("开启后为需要解码的命令注入 -hwaccel mediacodec；编码仍使用 libx264 / libx265"),
                    trailing = {
                        Switch(
                            checked = hardwareCodec,
                            onCheckedChange = { Settings.setHardwareCodec(it) }
                        )
                    }
                )
            }

            SectionCard(L10n.t("备份与恢复"), icon = Icons.Default.Cloud) {
                SettingsRow(
                    Icons.Default.Cloud,
                    L10n.t("导出设置"),
                    L10n.t("将全部偏好、AI 配置与媒体库规则导出为 XML"),
                    onClick = { exportLauncher.launch(null) }
                )
                SettingsRow(
                    Icons.Default.Cloud,
                    L10n.t("导入设置"),
                    L10n.t("选择 XML 备份文件恢复全部设置，导入会覆盖当前配置"),
                    onClick = {
                        importLauncher.launch(
                            arrayOf("text/xml", "application/xml", "text/plain", "*/*")
                        )
                    }
                )
            }

                    SectionCard(L10n.t("日志"), icon = Icons.AutoMirrored.Filled.Article) {
                        SettingsRow(
                            Icons.AutoMirrored.Filled.Article,
                            L10n.t("详细日志"),
                            L10n.t("实时日志与导出本机日志"),
                            onClick = { onOpenPage(SettingsPage.LOGS) }
                        )
                    }
                }

                SettingsPage.ABOUT -> {
                    SectionCard(L10n.t("版本与简介")) {
                        SettingsRow(
                            Icons.Default.Info,
                            L10n.t("版本"),
                            "version $version release"
                        )
                        SettingsRow(
                            Icons.Default.AutoAwesome,
                            L10n.t("应用简介"),
                            L10n.t("功能特色与免责声明"),
                            onClick = { showAbout = true }
                        )
                        SettingsRow(
                            Icons.Default.Lock,
                            L10n.t("隐私政策"),
                            L10n.t("查看本地处理与数据说明"),
                            onClick = { showPrivacy = true }
                        )
                    }
                    SectionCard(L10n.t("项目与许可")) {
                        SettingsRow(
                            Icons.Default.Computer,
                            L10n.t("技术栈"),
                            L10n.t("Kotlin · Java（JNI）· C/C++（FFmpeg 原生层）"),
                            onClick = { showTechStack = true }
                        )
                        SettingsRow(
                            Icons.Default.Hub,
                            L10n.t("依赖库"),
                            L10n.t("本软件使用的开源组件与许可证"),
                            onClick = { showDeps = true }
                        )
                        SettingsRow(
                            Icons.Default.Computer,
                            "GitHub",
                            L10n.t("打开项目仓库"),
                            onClick = { openLink(context, GITHUB_PROJECT) }
                        )
                        SettingsRow(
                            Icons.Default.Email,
                            L10n.t("联系开发者"),
                            L10n.t("打开 GitHub 主页"),
                            onClick = { openLink(context, GITHUB_AUTHOR) }
                        )
                    }

                    SectionCard(L10n.t("捐赠")) {
                        SettingsRow(
                            Icons.Default.Favorite,
                            L10n.t("微信赞赏"),
                            L10n.t("请作者喝杯咖啡，支持持续开发"),
                            onClick = { showReward = true }
                        )
                    }
                }

                SettingsPage.LOGS -> {
                    LogPanel()
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showTheme) {
        ChoiceDialog(
            title = L10n.t("主题样式"),
            options = listOf(
                ThemeMode.SYSTEM.name to L10n.t("跟随系统"),
                ThemeMode.LIGHT.name to L10n.t("日间模式"),
                ThemeMode.DARK.name to L10n.t("夜间模式")
            ),
            selected = theme.name,
            onSelect = {
                Settings.setTheme(ThemeMode.valueOf(it))
                showTheme = false
                activity?.recreate()
            },
            onDismiss = { showTheme = false }
        )
    }

    if (showLanguage) {
        ChoiceDialog(
            title = L10n.t("界面语言"),
            options = listOf("zh" to L10n.t("简体中文"), "en" to "English"),
            selected = language,
            onSelect = {
                Settings.setLanguage(it)
                showLanguage = false
                activity?.recreate()
            },
            onDismiss = { showLanguage = false }
        )
    }

    if (showOutput) {
        // 「重新选择自定义文件夹」已合并进「自定义文件夹」：点它即可随时改，
        // 并在后面显示当前所选文件夹的完整路径。
        val customLabel = if (customTree == null) {
            L10n.t("自定义文件夹（点击选择）")
        } else {
            L10n.t("自定义文件夹 · %1\$s", MediaRepository.treeDisplayPath(customTree))
        }
        ChoiceDialog(
            title = L10n.t("默认输出目录"),
            options = listOf(
                OutputTarget.EXTERNAL.name to L10n.t("应用外部目录（Android/data/…/FrameBetween）"),
                OutputTarget.GALLERY.name to L10n.t("系统媒体库（相册 / 音乐）"),
                OutputTarget.CUSTOM.name to customLabel
            ),
            selected = target.name,
            onSelect = { value ->
                showOutput = false
                if (value == OutputTarget.CUSTOM.name) {
                    treeLauncher.launch(null)
                } else {
                    Settings.setOutputTarget(OutputTarget.valueOf(value))
                }
            },
            onDismiss = { showOutput = false }
        )
    }

    if (showDeps) {
        AlertDialog(
            onDismissRequest = { showDeps = false },
            title = { Text(L10n.t("依赖库")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        L10n.t("本软件使用的开源组件（均在设备本地运行，许可证归各自作者）："),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(8.dp))
                    dependencies.forEach { (name, license) ->
                        Text(
                            "• $name",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "   $license",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        L10n.t("完整许可证文本与源码请访问项目 GitHub 仓库。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { openLink(context, GITHUB_PROJECT) }) { Text("GitHub") }
            },
            dismissButton = { TextButton(onClick = { showDeps = false }) { Text(L10n.t("关闭")) } }
        )
    }

    if (showTechStack) {
        AlertDialog(
            onDismissRequest = { showTechStack = false },
            title = { Text(L10n.t("技术栈")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        L10n.t("使用的编程语言：Kotlin、Java、C / C++、Python、Shell。\n\n") +
                            L10n.t("· 界面与应用逻辑：Kotlin 2.0.21 + Jetpack Compose / Material 3\n") +
                            L10n.t("· 音视频引擎：C / C++（FFmpeg、FFprobe、libx264、libx265、原生 drawtext、libass、freetype、MediaCodec JNI）\n") +
                            L10n.t("· JNI 桥接：Java / Kotlin（ffmpeg-kit-next 9.0.2，FFmpeg 9.0.2）\n") +
                            L10n.t("· 构建与工具链：Gradle Kotlin DSL、Python（静态自检与多语言文案）、Shell（NDK 交叉编译脚本）\n\n") +
                            L10n.t("所有处理都在设备本地完成，原生库以 GPL-3.0 授权随应用分发。完整依赖清单见顶部「依赖库」。"),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showTechStack = false }) { Text(L10n.t("关闭")) } }
        )
    }

    if (showCodecs) {
        CodecCapabilitiesDialog(
            report = codecReport,
            loading = codecLoading,
            onDismiss = { showCodecs = false }
        )
    }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(L10n.t("应用简介")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Image(
                        painter = painterResource(R.drawable.framebetween_icon),
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        L10n.t("帧间（FrameBetween）是一款完全在设备本地运行的音视频处理工作台：以 FFmpeg / FFprobe 为引擎，把桌面级的命令行剪辑能力装进手机。没有账号、没有云端服务，媒体文件从不离开你的设备。\n\n") +
                            L10n.t("【功能特色】\n") +
                            L10n.t("· 一站式本地处理：压缩与格式转换、时长与画面裁剪、删除帧（音画同步）、视频合并、翻转镜像、视频变速、消音与音频提取、音频拼接 / 剪切、GIF 生成，全部在手机端完成\n") +
                            L10n.t("· 字幕与水印：字幕提取 / 嵌入 / 烧录；文字与图片水印可在预览里拖动定位、双指缩放，自定义字体颜色、透明度与显示时间段，支持随机显示、边缘反弹等动态效果；去水印提供区域模糊、马赛克与智能填补的可视化选框\n") +
                            L10n.t("· 命令行的透明度：直接执行 FFmpeg 参数，提供快捷模板与光标处插入路径，实时日志、进度追踪与失败原因末尾三行摘要；每一项处理都能看到真正执行的命令\n") +
                            L10n.t("· 媒体库与文件管理：只收录音视频的文件夹 / 树形 / 文件三视图，可选嵌套文件夹逐级展开、右侧滚动滑块、新增媒体角标与增量刷新；标题旁【全部文件】可直接浏览手机目录\n") +
                            L10n.t("· 画质与性能：默认 H.265 编码、沿用源素材平均码率避免「越编越大」、可选 MediaCodec 硬件解码、产物统一按「原文件名 + 时间戳」命名，仅构建 64 位安装包\n") +
                            L10n.t("· AI 助手（可选）：可对接云端或本地 OpenAI 兼容模型，结合 ffprobe 实测媒体信息生成命令建议；执行失败可一键【AI 解析】，处理完成可【AI效果评估】对比前后文件判断是否达成剪辑要求；真实路径与命令均经脱敏后才发送，支持关闭提示词与连续对话\n") +
                            L10n.t("· 隐私优先：无账号、无统计、无广告 SDK、不上传媒体内容；只有你主动使用 AI 功能时才会联网\n\n") +
                            L10n.t("【免责声明】本应用仅供学习与个人合法使用。你应自行确认对所用媒体内容拥有相应权利，并遵守所在地法律法规；转码、裁剪、删除等操作均由你主动发起，处理前请自行备份重要文件。对因使用或无法使用本应用导致的设备性能、耗电、数据丢失或任何间接损失，开发者不承担责任。FFmpeg / FFprobe 与第三方 AI 服务归其各自权利人所有，其可用性及 AI 生成命令的准确性不由本应用保证，执行命令前请自行核对。项目源码与问题反馈见 https://github.com/Snowix/FrameBetween 。"),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text(L10n.t("我知道了")) } }
        )
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text(L10n.t("隐私政策")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        L10n.t("帧间（FrameBetween）隐私政策 · 生效日期 2026-11-11 · 适用版本 %1\$s\n\n", version) +
                            L10n.t("一、概述\n") +
                            L10n.t("帧间（FrameBetween）是一款完全在设备本地运行的音视频处理工具，包名 com.framebetween.app。应用没有账号体系、没有云端服务、没有自有服务器，媒体内容的转码、裁剪、合并、删除帧、变速、字幕与水印处理及 GIF 生成全部由设备上的 FFmpeg 引擎完成。\n\n") +
                            L10n.t("二、我们不收集的数据\n") +
                            L10n.t("我们不收集姓名、邮箱、手机号、设备标识符、广告 ID、位置、通讯录、剪贴板或应用使用行为；不内置统计、广告、崩溃上报或行为追踪 SDK；应用不存在把你选中的文件上传到任何服务器的代码路径。\n\n") +
                            L10n.t("三、媒体文件如何被处理\n") +
                            L10n.t("1. 通过文件选择器、其他应用「打开方式」、分享或媒体库选中的文件，应用会优先还原其真实文件路径，由 FFprobe / FFmpeg 直接读取原文件，不复制、不移动到应用私有目录。\n\n") +
                            L10n.t("2. 仅当内容提供者无法提供可直接访问的路径（例如部分网盘、下载管理器）或原生层读取失败时，才会在应用缓存目录创建一份临时输入副本；更换文件或进程重启时自动回收。\n\n") +
                            L10n.t("3. 输出默认写入应用外部私有目录（Android/data/包名/files/Movies/FrameBetween），也可由你选择保存到系统媒体库或自定义文件夹。输出文件名由「原文件名 + MMddHHmmss 时间戳」构成，不包含任何个人信息。\n\n") +
                            L10n.t("四、权限及其用途\n") +
                            L10n.t("媒体读取权限（Android 13 及以上的 READ_MEDIA_VIDEO / AUDIO / IMAGES，旧版 READ_EXTERNAL_STORAGE）：用于【全部文件】浏览手机目录、导入所选文件并生成缩略图与音频封面。媒体库三个视图只收录音视频，图片不会混进媒体列表。\n\n") +
                            L10n.t("存储写入权限（仅 Android 9 及以下）：仅用于把你主动保存的文件导出到公共目录。\n\n") +
                            L10n.t("INTERNET：仅用于「求助 AI」「AI 解析」「AI效果评估」「查询可用模型」「测试连接」以及你主动点击的 GitHub / 依赖库链接。POST_NOTIFICATIONS 用于显示前台任务进度；FOREGROUND_SERVICE / DATA_SYNC 用于长时间处理时保活。\n\n") +
                            L10n.t("文件夹授权：你通过系统文件夹选择器指定的自定义输出目录与扫描路径，其访问授权保存在本机，不会共享给第三方。所有权限均可拒绝，拒绝后仅对应功能不可用，不影响其他功能。\n\n") +
                            L10n.t("五、网络请求与 AI 助手\n") +
                            L10n.t("1. 应用不会在后台自动联网。使用 AI 功能时，只向你填写的 OpenAI 兼容端点发送：你输入的问题文本、ffprobe 读取到的媒体参数（时长、编码、码率、分辨率等）、处理前后文件的参数摘要、剪辑要求以及经脱敏的命令（真实路径替换为「输入路径 / 输出路径」）；执行失败时还会附带失败原因、错误日志与 FFmpeg 版本；【AI效果评估】会同时发送输出产物的 ffprobe 摘要，用于判断是否达成剪辑要求。选择「关闭提示词」时只发送你输入的原文；连续对话会把本轮对话的上下文发给同一端点。绝不发送媒体文件内容或设备信息。\n\n") +
                            L10n.t("2. 提供商、API 地址、模型名称与 API 密钥只保存在本机 SharedPreferences，不内置任何密钥，不代理或中转请求。开启连续对话时，对话记录也仅保存在本机，清空对话或清除软件缓存即删除；本地模型（Ollama、LM Studio 等）可以留空密钥。密钥在界面上只显示首尾各 4 位。\n\n") +
                            L10n.t("3. 为支持局域网内的本地模型地址（形如 http://192.168.x.x:11434），应用允许明文 HTTP 流量。该设置只在你主动发起上述请求时生效，请在可信网络中使用，并自行评估明文传输的风险。\n\n") +
                            L10n.t("六、本地存储与数据保留\n") +
                            L10n.t("设置项、语言与主题、媒体库排序 / 视图偏好、嵌套文件夹开关、文件夹黑白名单与扫描路径均保存在本机应用私有目录。缩略图与元数据缓存位于应用 cache 目录，可在「文件夹设置 → 媒体库维护」中清空；输入副本与临时产物在任务结束、更换文件或冷启动时回收。输出产物会一直保留，直到你删除任务、清空已完成任务、清除软件缓存或卸载应用。卸载应用会删除其私有目录下的全部数据；已导出到系统媒体库或自定义文件夹的文件由你自行管理。\n\n") +
                            L10n.t("七、第三方开源组件\n") +
                            L10n.t("FFmpeg 9.0.2 / ffmpeg-kit-next 9.0.2（GPL-3.0，仅在本地执行，不联网）、AndroidX、Jetpack Compose、Material 3、Media3 ExoPlayer、DocumentFile、kotlinx-coroutines。完整许可证文本与源码见 https://github.com/Snowix/FrameBetween 。\n\n") +
                            L10n.t("八、儿童与未成年人\n") +
                            L10n.t("本应用不面向 13 岁以下儿童，也不会主动收集儿童个人信息。监护人如发现相关情况，可通过「设置 → 联系开发者」与我们联系。\n\n") +
                            L10n.t("九、你的控制权\n") +
                            L10n.t("你可以随时在系统设置中撤销权限、清除应用数据，在应用内删除任务与输出文件、清空缓存与对话记录，或直接卸载应用。上述操作均不需要向我们提出申请。\n\n") +
                            L10n.t("十、政策更新与联系\n") +
                            L10n.t("隐私政策如有变更，会随新版本在应用内展示。如有问题或建议，请通过「设置 → 联系开发者」打开项目 GitHub 主页 https://github.com/Snowix/FrameBetween 反馈。"),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text(L10n.t("我知道了")) } }
        )
    }

    if (showClear) {
        AlertDialog(
            onDismissRequest = { showClear = false },
            title = { Text(L10n.t("清除软件缓存")) },
            text = {
                Column {
                    Text(L10n.t("当前占用：%1\$s", formatBytes(cacheBytes)))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        L10n.t("将清空应用缓存目录、旧版 files 残留与默认输出目录中的产物，并删除空目录；已保存到系统媒体库或自定义目录的文件不会被删除。"),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        L10n.t("当前选中的文件是缓存中的输入副本，清除后会失效，需要重新选择。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (FFmpegEngine.hasRunningTasks()) {
                        AppState.notify(L10n.t("有任务正在执行，请等待完成后再清除缓存"))
                        showClear = false
                        return@TextButton
                    }
                    FFmpegEngine.clearFinished()
                    val result = MediaRepository.clearCache(FFmpegEngine.runningRefs())
                    // 磁盘快照已随 cacheDir 一起被清掉，内存里的媒体清单也必须按当前
                    // 过滤规则重筛并重新扫描，否则关掉的 .nomedia 目录还会留在媒体库
                    LibrarySettings.rescan()
                    // 输入副本已被回收，当前选择不再可用
                    AppState.setCurrent(null)
                    AppState.notify(
                        L10n.t(
                            "已清除 %1\$s 个文件，释放 %2\$s；剩余 %3\$s",
                            result.deletedFiles,
                            formatBytes(result.freedBytes),
                            formatBytes(result.remainingBytes)
                        )
                    )
                    showClear = false
                }) { Text(L10n.t("清除")) }
            },
            dismissButton = { TextButton(onClick = { showClear = false }) { Text(L10n.t("取消")) } }
        )
    }

    if (showAi) {
        AiConfigDialog(
            providerId = aiProvider,
            baseUrl = aiBaseUrl,
            model = aiModel,
            apiKey = aiKey,
            onDismiss = { showAi = false }
        )
    }

    if (showReward) {
        AlertDialog(
            onDismissRequest = { showReward = false },
            title = { Text(L10n.t("微信捐赠支持开发")) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(R.drawable.reward_qrcode),
                        contentDescription = null,
                        modifier = Modifier.size(200.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        L10n.t("点击确认保存二维码，从微信「扫一扫」的相册中识别"),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            MediaRepository.saveRewardQrToGallery()
                        }
                        AppState.notify(
                            if (ok) L10n.t("二维码已保存到相册")
                            else L10n.t("保存失败，请检查存储权限")
                        )
                    }
                    showReward = false
                }) { Text(L10n.t("确认")) }
            },
            dismissButton = {
                TextButton(onClick = { showReward = false }) { Text(L10n.t("取消")) }
            }
        )
    }
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> L10n.t("跟随系统")
    ThemeMode.LIGHT -> L10n.t("日间模式")
    ThemeMode.DARK -> L10n.t("夜间模式")
}

private fun outputDetail(target: OutputTarget, customTree: String?): String = when (target) {
    OutputTarget.EXTERNAL -> L10n.t("应用外部目录")
    OutputTarget.GALLERY -> L10n.t("系统媒体库")
    OutputTarget.CUSTOM -> if (customTree == null) {
        L10n.t("自定义文件夹（未选择）")
    } else {
        L10n.t("自定义文件夹 · %1\$s", MediaRepository.treeDisplayPath(customTree))
    }
}

private val dependencies = listOf(
    "FFmpeg 9.0.2 / ffmpeg-kit-next 9.0.2" to L10n.t("GPL-3.0 · 本地执行，含 libx264 / libx265 / 原生 drawtext / libass / freetype"),
    "Kotlin 2.0.21" to "Apache-2.0",
    "Android Gradle Plugin 8.7.3 / Gradle 8.9" to "Apache-2.0",
    "Jetpack Compose + Material 3 (AndroidX)" to "Apache-2.0",
    "Navigation Compose 2.8.5" to "Apache-2.0",
    "Media3 ExoPlayer 1.5.1" to "Apache-2.0",
    "AndroidX Core KTX / Lifecycle / Activity Compose / DocumentFile" to "Apache-2.0",
    "kotlinx-coroutines-android" to "Apache-2.0",
    "JUnit 4.13.2" to L10n.t("EPL-1.0 · 仅用于单元测试")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    logo: String? = null
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
        supportingContent = {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        leadingContent = {
            if (logo != null) ProviderLogo(logo, 24.dp)
            else Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        trailingContent = trailing,
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
    logoKeys: Set<String> = emptySet()
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (value in logoKeys) {
                            ProviderLogo(value, 20.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        RadioButton(selected = selected == value, onClick = { onSelect(value) })
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(L10n.t("关闭")) } }
    )
}

/**
 * 等长脱敏显示：只改变展示文本，输入框持有的仍是真实密钥。
 * 掩码长度与原串一致，因此用恒等偏移映射即可保持光标与选区位置正确。
 */
private class MaskedKeyVisualTransformation(
    private val reveal: Boolean
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val shown = if (reveal || text.isEmpty()) text
        else AnnotatedString(ApiKeyMask.mask(text.text))
        return TransformedText(shown, IdentityMapping)
    }

    private companion object {
        val IdentityMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = offset
            override fun transformedToOriginal(offset: Int): Int = offset
        }
    }
}

@Composable
private fun AiConfigDialog(
    providerId: String,
    baseUrl: String,
    model: String,
    apiKey: String,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val presets: List<AiProvider> = AiSettings.presets
    val current = presets.firstOrNull { it.id == providerId } ?: presets.last()
    var urlText by remember { mutableStateOf(baseUrl) }
    var modelText by remember { mutableStateOf(model) }
    var keyText by remember { mutableStateOf(apiKey) }
    var keyRevealed by remember { mutableStateOf(false) }
    var showProvider by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf("") }
    var testOk by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf(listOf<String>()) }
    var querying by remember { mutableStateOf(false) }
    var modelHint by remember { mutableStateOf("") }

    // 切换供应商后由各自的 StateFlow 带出独立保存的配置，绝不串值
    LaunchedEffect(providerId, baseUrl, model, apiKey) {
        urlText = baseUrl
        modelText = model
        keyText = apiKey
    }
    // 只有换供应商才清空模型查询结果，保存或点选模型时列表保持可见
    LaunchedEffect(providerId) {
        models = emptyList()
        modelHint = ""
        keyRevealed = false
    }

    fun persist() {
        AiSettings.setBaseUrl(urlText.trim())
        AiSettings.setApiKey(keyText.trim())
        AiSettings.setModel(modelText.trim())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.t("AI 助手配置")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(L10n.t("模型提供商"), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Button(onClick = { showProvider = true }, modifier = Modifier.fillMaxWidth()) {
                    ProviderLogo(current.icon, 22.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(current.label)
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text(L10n.t("API 地址（OpenAI 兼容）")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    label = { Text(L10n.t("API 密钥（本地模型可留空）")) },
                    singleLine = true,
                    visualTransformation = MaskedKeyVisualTransformation(keyRevealed),
                    trailingIcon = {
                        IconButton(onClick = { keyRevealed = !keyRevealed }) {
                            Icon(
                                if (keyRevealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (keyRevealed) L10n.t("隐藏密钥") else L10n.t("显示密钥")
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    L10n.t("默认隐藏中间字符，仅显示首尾各 4 位；点击右侧图标可查看完整密钥，密钥只保存在本机。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = modelText,
                    onValueChange = { modelText = it },
                    label = { Text(L10n.t("模型名称")) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                val suggestions = AiSettings.suggestions(providerId)
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(L10n.t("常用模型"), style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(suggestions) { name ->
                            FilterChip(
                                selected = modelText == name,
                                onClick = { modelText = name; AiSettings.setModel(name) },
                                label = { Text(name, maxLines = 1) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        persist()
                        AppState.notify(L10n.t("AI 配置已保存"))
                    }, modifier = Modifier.weight(1f)) { Text(L10n.t("保存")) }
                    OutlinedButton(onClick = {
                        persist()
                        testing = true
                        testResult = ""
                        scope.launch {
                            val result = AiClient.ask("Reply with OK only.", useTemplate = false)
                            testOk = result.success
                            testResult = if (result.success) {
                                L10n.t("连接成功：%1\$s", result.text.take(60))
                            } else result.text
                            testing = false
                        }
                    }, modifier = Modifier.weight(1f)) { Text(L10n.t("测试连接")) }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        persist()
                        querying = true
                        models = emptyList()
                        modelHint = ""
                        scope.launch {
                            val result = AiClient.fetchModels()
                            models = result.models
                            modelHint = if (result.success) result.message
                            else L10n.t("查询失败：%1\$s", result.message)
                            querying = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(L10n.t("查询可用模型")) }
                if (testing || querying) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                if (modelHint.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(modelHint, style = MaterialTheme.typography.bodySmall)
                }
                if (models.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(L10n.t("点击选择模型"), style = MaterialTheme.typography.labelMedium)
                    Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                        models.take(60).forEach { name ->
                            TextButton(onClick = {
                                modelText = name
                                AiSettings.setModel(name)
                            }) { Text(name, maxLines = 1) }
                        }
                    }
                }
                if (testResult.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        testResult,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (testOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    L10n.t("每个供应商的 API 地址、密钥与模型独立保存，切换时互不影响；切换前会先保存当前编辑内容。可用「查询可用模型」从 /models 接口拉取列表，本地模型也会尝试 /api/tags。本地模型密钥留空即可，应用已允许明文 HTTP。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(L10n.t("关闭")) } }
    )

    if (showProvider) {
        ChoiceDialog(
            title = L10n.t("选择模型提供商"),
            options = presets.map { it.id to it.label },
            selected = providerId,
            onSelect = { id ->
                persist()
                AiSettings.selectProvider(id)
                models = emptyList()
                modelHint = ""
                showProvider = false
            },
            onDismiss = { showProvider = false },
            logoKeys = presets.map { it.id }.toSet()
        )
    }
}

/** 设备编解码能力：Android MediaCodecList 实测 + FFmpeg 运行时信息命令实测。 */
@Composable
private fun CodecCapabilitiesDialog(
    report: DeviceCodecReport?,
    loading: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.t("设备编解码器支持")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (loading || report == null) {
                    Text(L10n.t("正在读取设备编解码器…"), style = MaterialTheme.typography.bodySmall)
                    return@Column
                }
                val probe = report.probe
                Text(L10n.t("FFmpeg 引擎"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(probe.version, style = MaterialTheme.typography.bodySmall)
                Text(
                    L10n.t("构建日期：%1\$s", probe.buildDate.ifBlank { L10n.t("未知") }),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    L10n.t("硬件加速方式：%1\$s", probe.hwaccels.joinToString(", ").ifBlank { L10n.t("无") }),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    L10n.t(
                        "MediaCodec 支持：%1\$s",
                        if (probe.mediaCodecSupported) L10n.t("可用") else L10n.t("不可用")
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    L10n.t(
                        "MediaCodec 编码器：%1\$s",
                        probe.mediaCodecEncoders.joinToString(", ").ifBlank { L10n.t("未检测到") }
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    L10n.t(
                        "MediaCodec 解码器：%1\$s",
                        probe.mediaCodecDecoders.joinToString(", ").ifBlank { L10n.t("未检测到") }
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(12.dp))
                CodecSection(L10n.t("硬件解码器（%1\$s）", report.hardwareDecoders.size), report.hardwareDecoders)
                CodecSection(L10n.t("硬件编码器（%1\$s）", report.hardwareEncoders.size), report.hardwareEncoders)
                CodecSection(L10n.t("软件解码器（%1\$s）", report.softwareDecoders.size), report.softwareDecoders)
                CodecSection(L10n.t("软件编码器（%1\$s）", report.softwareEncoders.size), report.softwareEncoders)
                Text(
                    L10n.t("硬件 / 软件按 Android MediaCodecInfo 判定；FFmpeg 侧清单来自本机 -hwaccels / -encoders / -decoders 实测，不是预置列表。"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(L10n.t("关闭")) } }
    )
}

@Composable
private fun CodecSection(title: String, entries: List<CodecEntry>) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(2.dp))
    if (entries.isEmpty()) {
        Text(L10n.t("无"), style = MaterialTheme.typography.bodySmall)
    } else {
        entries.take(20).forEach { entry ->
            Text("• ${entry.name}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            Text(
                entry.mime.take(160),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
        }
        if (entries.size > 20) {
            Text(
                L10n.t("… 另有 %1\$s 个", entries.size - 20),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
    Spacer(Modifier.height(10.dp))
}
