package com.framebetween.app.ui.terminal
import com.framebetween.app.core.L10n

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framebetween.app.core.AiClient
import com.framebetween.app.core.AppState
import com.framebetween.app.core.BrightnessConfig
import com.framebetween.app.core.CommandRedactor
import com.framebetween.app.core.CommandTokenizer
import com.framebetween.app.core.Commands
import com.framebetween.app.core.FFmpegEngine
import com.framebetween.app.core.FilterFix
import com.framebetween.app.core.MediaRepository
import com.framebetween.app.core.OutputTarget
import com.framebetween.app.core.Settings
import com.framebetween.app.core.SubtitleAssets
import com.framebetween.app.core.TaskStatus
import com.framebetween.app.core.WatermarkAssets
import com.framebetween.app.core.WatermarkConfig
import com.framebetween.app.ui.common.ActionButton
import com.framebetween.app.ui.common.CurrentMediaCard
import com.framebetween.app.ui.common.Label
import com.framebetween.app.ui.common.LogView
import com.framebetween.app.ui.common.ScreenTopBar
import com.framebetween.app.ui.common.SectionCard
import com.framebetween.app.ui.common.rememberMediaPicker
import com.framebetween.app.util.CommandInsert
import kotlinx.coroutines.launch
import java.io.File

private data class CommandTemplate(val name: String, val ext: String, val body: String, val special: String = "")

private val templates = listOf(
    CommandTemplate(L10n.t("转 MP3"), "mp3", "-i {in} -vn -c:a libmp3lame -b:a 192k {out}"),
    CommandTemplate(L10n.t("H.265 压缩"), "mp4", "-i {in} -c:v libx265 -preset fast -crf 28 -c:a aac -b:a 128k {out}"),
    CommandTemplate(L10n.t("缩放 720p"), "mp4", "-i {in} -vf scale=-2:720 -c:v libx265 -preset veryfast -crf 23 -c:a copy {out}"),
    CommandTemplate(L10n.t("截取前 10 秒"), "mp4", "-i {in} -t 10 -c copy {out}"),
    CommandTemplate(L10n.t("水平镜像"), "mp4", "-i {in} -vf hflip -c:v libx265 -preset veryfast -crf 20 -c:a copy {out}"),
    CommandTemplate(L10n.t("视频消音"), "mp4", "-i {in} -an -c:v copy {out}"),
    CommandTemplate(L10n.t("提取 AAC"), "m4a", "-i {in} -vn -c:a aac -b:a 192k {out}"),
    CommandTemplate(L10n.t("转 WebM"), "webm", "-i {in} -c:v libvpx-vp9 -crf 32 -b:v 0 -c:a libopus {out}"),
    CommandTemplate(L10n.t("调整亮度"), "mp4", "", "BRIGHTNESS"),
    CommandTemplate(L10n.t("文字水印"), "mp4", "", "WATERMARK"),
    CommandTemplate(L10n.t("烧录字幕"), "mp4", "", "SUBTITLE"),
    CommandTemplate(
        L10n.t("视频倒放"), "mp4",
        "-i {in} -vf reverse -af areverse -c:v libx265 -preset veryfast -crf 23 -c:a aac {out}"
    ),
    CommandTemplate(
        L10n.t("2 倍速"), "mp4",
        "-i {in} -vf setpts=PTS/2 -af atempo=2 -c:v libx265 -preset veryfast -crf 23 -c:a aac {out}"
    ),
    CommandTemplate(L10n.t("修改封面"), "mp4", "", "COVER")
)

/** 生成一个输出路径，并把展示路径（用户选择的目录）注册成可执行暂存路径的别名。 */
private fun outputForCommand(ext: String, label: String): String {
    val staging = MediaRepository.newOutput(ext, label)
    return MediaRepository.outputAlias(staging)
}

private fun quotedPath(path: String): String =
    if (path.any { it.isWhitespace() }) CommandTokenizer.quote(path) else path

/** 「AI效果评估」首轮请求所需的三项现场数据。 */
private data class EvaluateContext(
    val command: String,
    val inputPath: String,
    val outputRef: String
)

/** 从实际执行的命令里取 -i 后的输入路径，保证 ffprobe 查的是这次真正处理过的文件。 */
private fun executedInputPath(command: String, fallback: String): String {
    val tokens = CommandTokenizer.stripBinaryName(CommandTokenizer.parse(command))
    val index = tokens.indexOfFirst { it.equals("-i", ignoreCase = true) }
    val candidate = if (index >= 0) tokens.getOrNull(index + 1).orEmpty() else ""
    return candidate.takeIf {
        it.startsWith("/") || it.startsWith("content://") || it.startsWith("file://")
    } ?: fallback
}

/** SAF 选中的文件必须复制成真实路径，FFmpeg 无法直接读 content://。 */
private fun copyToCache(context: Context, uri: Uri, name: String): String? = runCatching {
    val dir = File(context.cacheDir, "assets").apply { mkdirs() }
    val safe = name.replace(Regex("[/\\\\:*?\"<>|]"), "_")
    val file = File(dir, "${System.currentTimeMillis()}_$safe")
    context.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    } ?: return@runCatching null
    file.absolutePath
}.getOrNull()

private fun displayName(context: Context, uri: Uri): String? = runCatching {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return@runCatching null
        val i = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (i >= 0) cursor.getString(i) else null
    }
}.getOrNull()

@Composable
fun TerminalScreen() {
    val media by AppState.current.collectAsState()
    val logs by FFmpegEngine.logs.collectAsState()
    val tasks by FFmpegEngine.tasks.collectAsState()
    val outputTarget by Settings.outputTarget.collectAsState()
    val context = LocalContext.current
    val pick = rememberMediaPicker()

    // 用 TextFieldValue 才能拿到 selection，实现「在光标处插入」
    var commandText by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(""))
    }
    var activeTaskId by rememberSaveable { mutableStateOf<Long?>(null) }
    var history by rememberSaveable { mutableStateOf(listOf<String>()) }
    // 「求助 AI」输入框内容提到页面级：执行失败后的「AI 解析」用它填 {剪辑需求}
    var aiQuestion by rememberSaveable { mutableStateOf("") }
    // 最近一次点击的【快捷模板】文字；用户没填求助 AI 时当作 {剪辑需求} 交给效果评估
    var templateRequest by rememberSaveable { mutableStateOf("") }
    // AI 解析：首次发送 / 转圈 / 回复 / 多轮上下文 / 对话框隐藏与恢复
    var diagnoseStarted by rememberSaveable { mutableStateOf(false) }
    var diagnoseReply by rememberSaveable { mutableStateOf("") }
    var diagnoseVisible by remember { mutableStateOf(false) }
    var diagnoseLoading by remember { mutableStateOf(false) }
    var diagnoseError by remember { mutableStateOf("") }
    var diagnoseHistory by remember { mutableStateOf(listOf<AiClient.ChatTurn>()) }
    // AI 效果评估：处理完成后发送前后 ffprobe 信息 / 剪辑需求 / 脱敏命令，可隐藏与恢复
    var evaluateStarted by rememberSaveable { mutableStateOf(false) }
    var evaluateReply by rememberSaveable { mutableStateOf("") }
    var evaluateVisible by remember { mutableStateOf(false) }
    var evaluateLoading by remember { mutableStateOf(false) }
    var evaluateError by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    /** 剪辑需求：优先「求助 AI」输入框；没填时用最近一次点击的快捷模板文字。 */
    fun evaluateRequest(): String = aiQuestion.trim().ifBlank { templateRequest }

    /** 整条命令替换（模板 / AI / 历史记录）：光标落到命令末尾。 */
    fun setCommand(text: String) {
        commandText = TextFieldValue(text, TextRange(text.length))
    }

    /** 路径与片段插入：替换当前选区，必要时补前后空格，光标停在新内容末尾。 */
    fun insertAtCursor(text: String) {
        val current = commandText
        val result = CommandInsert.atCursor(
            current.text, current.selection.start, current.selection.end, text
        )
        commandText = TextFieldValue(result.text, TextRange(result.caret))
    }

    val subtitleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val source = media ?: return@rememberLauncherForActivityResult
        val path = SubtitleAssets.importSubtitle(context, uri, displayName(context, uri))
        if (path == null) {
            AppState.notify(L10n.t("字幕文件读取失败"))
        } else {
            val out = outputForCommand("mp4", "subtitle")
            val args = Commands.burnSubtitles(source.path, out, path, source.videoKbps)
            setCommand(CommandTokenizer.join(args))
            AppState.notify(L10n.t("已填入字幕烧录命令，可直接执行"))
        }
    }

    val coverLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val source = media ?: return@rememberLauncherForActivityResult
        val cover = copyToCache(context, uri, displayName(context, uri) ?: "cover.jpg")
        if (cover == null) {
            AppState.notify(L10n.t("封面图片读取失败"))
        } else {
            val out = outputForCommand("mp4", "cover")
            setCommand(
                listOf(
                    "-i", quotedPath(source.path), "-i", quotedPath(cover),
                    "-map", "0", "-map", "1", "-c", "copy",
                    "-disposition:v:1", "attached_pic", quotedPath(out)
                ).joinToString(" ")
            )
            AppState.notify(L10n.t("已填入修改封面命令，可直接执行"))
        }
    }
    // 对话框状态不跨导航保存，避免切换 tab 后残留弹窗
    var showAi by remember { mutableStateOf(false) }
    var showWatermark by remember { mutableStateOf(false) }

    val activeTask = tasks.firstOrNull { it.id == activeTaskId }
    val logLines = activeTaskId?.let { logs[it] }.orEmpty()
        .ifEmpty { tasks.firstOrNull()?.let { logs[it.id] }.orEmpty() }
    val isRunning = activeTask?.status == TaskStatus.RUNNING

    /** AI 建议的命令统一在这里替换 {in} / {out}，再整条写回命令框。 */
    fun useAiCommand(raw: String) {
        val ext = guessOutputExt(raw)
        val resolved = raw
            // 输入路径统一走 token 引号，含空格 / 中文 / 特殊字符时命令依然可执行
            .let { text -> media?.let { text.replace("{in}", CommandTokenizer.quote(it.path)) } ?: text }
            .replace("{out}", quotedPath(outputForCommand(ext, "ai")))
        setCommand(resolved)
        AppState.notify(L10n.t("已填入 AI 建议的命令，请检查后执行"))
    }

    /** 生成脱敏命令与脱敏错误日志，供首轮诊断和「首轮失败后直接追问」共用。 */
    fun diagnoseContext(): Pair<String, String> {
        val task = activeTask
        val command = task?.command?.takeIf { it.isNotBlank() } ?: commandText.text
        val input = media?.path.orEmpty()
        val output = task?.output.orEmpty()
        val redacted = CommandRedactor.redact(command, input, output)
        val failure = buildString {
            if (task?.message?.isNotBlank() == true) appendLine(task.message)
            append(logLines.takeLast(60).joinToString("\n"))
        }.let { CommandRedactor.redactText(it, input, output) }.take(6000)
        return redacted to failure
    }

    /** 点击【AI 解析】：首次发请求（按钮转圈），回复后自动弹出对话框；之后点击只恢复。 */
    fun startDiagnose() {
        if (diagnoseLoading) {
            diagnoseVisible = true
            return
        }
        if (diagnoseStarted && diagnoseReply.isNotBlank()) {
            diagnoseVisible = true
            return
        }
        if (activeTask == null) return
        val currentMedia = media
        val (redacted, failure) = diagnoseContext()
        val request = aiQuestion
        diagnoseStarted = true
        diagnoseLoading = true
        diagnoseError = ""
        scope.launch {
            val result = AiClient.diagnose(request, currentMedia, redacted, failure)
            diagnoseLoading = false
            if (result.success) {
                diagnoseReply = result.text
                diagnoseHistory = listOf(
                    AiClient.ChatTurn("user", result.sentPrompt),
                    AiClient.ChatTurn("assistant", result.text)
                )
            } else {
                diagnoseError = result.text
                diagnoseStarted = false // 失败时允许重新发起
            }
            diagnoseVisible = true
        }
    }

    /** 对话框内继续追问：带上首轮提示词与全部回复，保持排错上下文。 */
    fun followUp(text: String) {
        if (text.isBlank() || diagnoseLoading) return
        val historySnapshot = diagnoseHistory
        val currentMedia = media
        // 首轮请求失败时没有上下文，追问自动重新携带诊断模板与错误日志
        val fallback = if (historySnapshot.isEmpty()) diagnoseContext() else null
        diagnoseLoading = true
        diagnoseError = ""
        scope.launch {
            val result = if (fallback != null) {
                AiClient.diagnose(text, currentMedia, fallback.first, fallback.second)
            } else {
                AiClient.ask(
                    text, currentMedia, useTemplate = false, history = historySnapshot
                )
            }
            diagnoseLoading = false
            if (result.success) {
                diagnoseReply = result.text
                val turns = listOf(
                    AiClient.ChatTurn("user", result.sentPrompt),
                    AiClient.ChatTurn("assistant", result.text)
                )
                diagnoseHistory = if (fallback != null) turns else historySnapshot + turns
            } else {
                diagnoseError = result.text
            }
        }
    }

    /**
     * 组装【AI效果评估】首轮数据：
     * - 命令取最终【命令输入】（任务里若记录了实际执行时的修正命令，用其 -i 路径做 ffprobe 与脱敏）；
     * - 输入路径按实际执行命令解析，回退到当前输入文件；
     * - 输出 ref 是发布后的真实路径或 content://，由 AiClient 负责探测。
     */
    fun evaluateContext(): EvaluateContext {
        val task = activeTask
        val command = commandText.text.takeIf { it.isNotBlank() } ?: task?.command.orEmpty()
        val input = executedInputPath(task?.command.orEmpty(), media?.path.orEmpty())
            .ifBlank { media?.path.orEmpty() }
        return EvaluateContext(command, input, task?.output.orEmpty())
    }

    /** 点击【AI效果评估】：首次发请求（按钮转圈），回复后自动弹窗；之后点击只恢复。 */
    fun startEvaluate() {
        if (evaluateLoading) {
            evaluateVisible = true
            return
        }
        if (evaluateStarted && evaluateReply.isNotBlank()) {
            evaluateVisible = true
            return
        }
        if (activeTask == null) return
        val ctx = evaluateContext()
        val request = evaluateRequest()
        val currentMedia = media
        evaluateStarted = true
        evaluateLoading = true
        evaluateError = ""
        scope.launch {
            val result = AiClient.evaluate(
                request, currentMedia, ctx.inputPath, ctx.outputRef, ctx.command
            )
            evaluateLoading = false
            if (result.success) {
                evaluateReply = result.text
            } else {
                evaluateError = result.text
                evaluateStarted = false // 失败时允许重新发起
            }
            evaluateVisible = true
        }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(L10n.t("FFmpeg 命令行"), L10n.t("无需 root · 直接执行 ffmpeg 参数"))
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Label(L10n.t("输入文件"))
            CurrentMediaCard(onPick = pick)

            SectionCard(L10n.t("快捷模板"), icon = Icons.Default.Add) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(templates) { template ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                // 快捷模板的文字就是这次的剪辑需求，供【AI效果评估】填 {剪辑需求}
                                templateRequest = template.name
                                when (template.special) {
                                    "WATERMARK" -> {
                                        if (media == null) {
                                            AppState.notify(L10n.t("请先选择输入文件"))
                                        } else showWatermark = true
                                    }
                                    "BRIGHTNESS" -> {
                                        if (media == null) {
                                            AppState.notify(L10n.t("请先选择输入文件"))
                                        } else {
                                            val out = outputForCommand("mp4", "bright")
                                            val args = Commands.brightness(
                                                media!!.path, out, BrightnessConfig(), media!!.videoKbps
                                            )
                                            setCommand(CommandTokenizer.join(args))
                                            AppState.notify(L10n.t("已填入亮度调整命令，可修改 eq 参数后执行"))
                                        }
                                    }
                                    "SUBTITLE" -> {
                                        if (media == null) AppState.notify(L10n.t("请先选择输入文件"))
                                        else subtitleLauncher.launch(arrayOf("*/*"))
                                    }
                                    "COVER" -> {
                                        if (media == null) AppState.notify(L10n.t("请先选择输入文件"))
                                        else coverLauncher.launch(arrayOf("image/*"))
                                    }
                                    else -> {
                                        val input = media?.path ?: L10n.t("请先选择输入文件")
                                        val output = outputForCommand(template.ext, "cli")
                                        setCommand(
                                            template.body
                                                .replace("{in}", input)
                                                .replace("{out}", quotedPath(output))
                                        )
                                    }
                                }
                            },
                            label = { Text(template.name) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            media?.let { insertAtCursor(quotedPath(it.path)) }
                        },
                        enabled = media != null,
                        modifier = Modifier.weight(1f)
                    ) { Text(L10n.t("插入输入路径")) }
                    OutlinedButton(
                        onClick = {
                            insertAtCursor(quotedPath(outputForCommand("mp4", "cli")))
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(L10n.t("插入输出路径")) }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    when (outputTarget) {
                        OutputTarget.EXTERNAL -> L10n.t("产物直接保存在你选择的默认输出目录。")
                        OutputTarget.GALLERY -> L10n.t("目标为系统媒体库：任务完成后产物自动转存到相册 / 音乐。")
                        OutputTarget.CUSTOM -> L10n.t("目标为自定义文件夹：任务完成后产物自动转存到你选择的文件夹。")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    L10n.t("命令中的输出路径按你选择的目录显示；执行时会自动写回应用可写的暂存位置，完成后再转存。"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    L10n.t("FFmpeg 9.0.2 含原生 drawtext；文字叠加命令可直接执行，中文字体自动指向系统 CJK 字体。"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            L10n.t("求助 AI"),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            L10n.t("用自然语言描述剪辑需求，获取 ffmpeg 命令建议"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { showAi = true }) { Text(L10n.t("提问")) }
                }
            }

            SectionCard(L10n.t("命令输入")) {
                OutlinedTextField(
                    value = commandText,
                    onValueChange = {
                        commandText = it
                        // 手工改过命令就不再把快捷模板文字当作这次的剪辑需求
                        templateRequest = ""
                    },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    ),
                    placeholder = { Text("-i /path/to/input.mp4 ... /path/to/output.mp4") },
                    minLines = 5,
                    maxLines = 8
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    L10n.t("填写 ffmpeg 的参数部分即可（可带 ffmpeg 前缀）。不支持 shell 管道与重定向。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton(
                        text = L10n.t("执行"),
                        onClick = {
                            val raw = commandText.text.trim()
                            if (raw.isEmpty()) {
                                AppState.notify(L10n.t("请先输入命令"))
                                return@ActionButton
                            }
                            if (CommandTokenizer.containsShellMeta(raw)) {
                                AppState.notify(L10n.t("不支持 shell 管道 / 重定向，请直接写 ffmpeg 参数"))
                                return@ActionButton
                            }
                            val parsed = CommandTokenizer.stripBinaryName(CommandTokenizer.parse(raw))
                            if (parsed.isEmpty()) return@ActionButton
                            // 把「用户所选目录」的展示路径还原成 FFmpeg 真正可写的暂存路径
                            val resolved = MediaRepository.resolveOutputAliases(parsed)
                            // 修正帧格式与流复制冲突；drawtext 在 9.0.2 下原生执行
                            val fixed = FilterFix.apply(resolved)
                            if (fixed.blocked) {
                                AppState.notify(fixed.reason)
                                return@ActionButton
                            }
                            if (fixed.notes.isNotEmpty()) AppState.notify(fixed.notes.first())
                            val output = fixed.tokens.lastOrNull()?.takeUnless { it.startsWith("-") }.orEmpty()
                            // 新一轮执行清空上一次的 AI 解析 / 效果评估结果，避免恢复出旧任务的对话
                            diagnoseVisible = false
                            diagnoseStarted = false
                            diagnoseReply = ""
                            diagnoseError = ""
                            diagnoseHistory = emptyList()
                            evaluateVisible = false
                            evaluateStarted = false
                            evaluateReply = ""
                            evaluateError = ""
                            activeTaskId = FFmpegEngine.submit(L10n.t("自定义命令"), fixed.tokens, output)
                            history = (listOf(raw) + history).take(8)
                        },
                        enabled = !isRunning,
                        icon = Icons.Default.PlayArrow,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = { activeTaskId?.let { FFmpegEngine.cancel(it) } },
                        enabled = isRunning
                    ) {
                        Icon(Icons.Default.Stop, null, Modifier.size(18.dp))
                        Text(L10n.t(" 停止"))
                    }
                }
            }

            if (activeTask != null) {
                // 两个 AI 入口都在【执行状态】画布右侧，样式与【求助 AI】的【提问】按钮一致
                val canEvaluate = activeTask.status == TaskStatus.SUCCESS &&
                    activeTask.message == L10n.t("处理完成")
                SectionCard(L10n.t("执行状态")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when (activeTask.status) {
                                TaskStatus.RUNNING -> L10n.t("正在执行…")
                                TaskStatus.SUCCESS -> L10n.t("执行成功 · %1\$ss", activeTask.elapsedMs / 1000)
                                TaskStatus.FAILED -> L10n.t("执行失败")
                                TaskStatus.CANCELLED -> L10n.t("已取消")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        if (activeTask.status == TaskStatus.RUNNING) {
                            Text(activeTask.speed, style = MaterialTheme.typography.bodySmall)
                        }
                        if (activeTask.status == TaskStatus.FAILED) {
                            StatusAiButton(L10n.t("AI 解析"), diagnoseLoading) { startDiagnose() }
                        }
                        if (canEvaluate) {
                            StatusAiButton(L10n.t("AI效果评估"), evaluateLoading) { startEvaluate() }
                        }
                    }
                    if (activeTask.message.isNotBlank() && activeTask.status != TaskStatus.RUNNING) {
                        Text(
                            activeTask.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (activeTask.status == TaskStatus.SUCCESS) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                    if (canEvaluate || activeTask.status == TaskStatus.FAILED) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (canEvaluate) {
                                L10n.t("发送剪辑需求、处理前后的 ffprobe 信息与脱敏命令；媒体文件本身不会上传。")
                            } else {
                                L10n.t("发送失败原因与错误日志；命令中的真实路径会替换为「输入路径 / 输出路径」。")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (logLines.isNotEmpty()) {
                Label(L10n.t("实时输出"))
                LogView(logLines)
            }

            if (history.isNotEmpty()) {
                SectionCard(L10n.t("历史命令")) {
                    history.forEach { item ->
                        FilterChip(
                            selected = false,
                            onClick = { setCommand(item) },
                            label = { Text(item.take(28), maxLines = 1) }
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = { history = emptyList() }) {
                        Icon(Icons.Default.Delete, null, Modifier.size(16.dp))
                        Text(L10n.t(" 清空历史"))
                    }
                }
            }
        }
    }

    if (showWatermark) {
        WatermarkDialog(
            onDismiss = { showWatermark = false },
            onBuild = { text, position, fontSize ->
                val source = media
                if (source == null) {
                    AppState.notify(L10n.t("请先选择输入文件"))
                } else {
                    val png = WatermarkAssets.create(
                        context,
                        WatermarkAssets.Style(text = text, fontSizePx = fontSize)
                    )
                    val out = outputForCommand("mp4", "watermark")
                    val args = Commands.watermark(
                        source.path, out, WatermarkConfig(png, position), source.videoKbps
                    )
                    setCommand(CommandTokenizer.join(args))
                    AppState.notify(L10n.t("已生成文字水印 PNG 并填入命令，可直接执行"))
                }
                showWatermark = false
            }
        )
    }

    if (showAi) {
        AiHelpDialog(
            media = media,
            question = aiQuestion,
            onQuestionChange = { aiQuestion = it },
            onDismiss = { showAi = false },
            onUseCommand = { raw ->
                useAiCommand(raw)
                showAi = false
            }
        )
    }

    // 点击对话框外 / 返回键只隐藏，状态仍在页面里，按钮可再次恢复本次对话
    if (diagnoseVisible) {
        AiDiagnoseDialog(
            reply = diagnoseReply,
            loading = diagnoseLoading,
            error = diagnoseError,
            onDismiss = { diagnoseVisible = false },
            onFollowUp = { followUp(it) },
            onUseCommand = { raw ->
                useAiCommand(raw)
                diagnoseVisible = false
            }
        )
    }

    // 处理完成后可反复恢复本次效果评估对话；新一轮执行会清空
    if (evaluateVisible) {
        AiEvaluateDialog(
            reply = evaluateReply,
            loading = evaluateLoading,
            error = evaluateError,
            onDismiss = { evaluateVisible = false },
            onUseCommand = { raw ->
                useAiCommand(raw)
                evaluateVisible = false
            }
        )
    }
}

/** 【执行状态】卡片右侧的小按钮：请求中显示转圈，样式与【求助 AI】的【提问】一致。 */
@Composable
private fun StatusAiButton(text: String, loading: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            Icon(Icons.Default.AutoAwesome, null, Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** 文字水印模板：把文字画成 PNG 再用 overlay 合成，中文显示稳定可靠。 */
@Composable
private fun WatermarkDialog(
    onDismiss: () -> Unit,
    onBuild: (String, String, Int) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var position by remember { mutableStateOf("br") }
    var fontSize by remember { mutableStateOf(56f) }
    val positions = listOf(
        "tl" to L10n.t("左上"), "tc" to L10n.t("上中"), "tr" to L10n.t("右上"),
        "ml" to L10n.t("左中"), "mc" to L10n.t("居中"), "mr" to L10n.t("右中"),
        "bl" to L10n.t("左下"), "bc" to L10n.t("下中"), "br" to L10n.t("右下")
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.t("文字水印")) },
        text = {
            Column {
                Text(
                    L10n.t("这里把中文文字渲染成透明 PNG，再用 overlay 叠加，便于预览里拖动与缩放；如需整段字幕，请用「烧录字幕」模板走原生 drawtext。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(L10n.t("水印文字")) },
                    placeholder = { Text("FrameBetween") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Text(L10n.t("位置"), style = MaterialTheme.typography.labelMedium)
                positions.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { (key, label) ->
                            FilterChip(
                                selected = position == key,
                                onClick = { position = key },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(L10n.t("字号：%1\$s", fontSize.toInt()), style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = fontSize,
                    onValueChange = { fontSize = it },
                    valueRange = 24f..160f,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onBuild(text.ifBlank { "FrameBetween" }, position, fontSize.toInt())
            }) { Text(L10n.t("生成命令")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.t("取消")) } }
    )
}

/** 按 AI 建议里的编码器 / 扩展名推测输出容器，保证填入的命令可直接执行。 */
private fun guessOutputExt(command: String): String = when {
    command.contains("libmp3lame") || command.contains(".mp3") -> "mp3"
    command.contains(".m4a") -> "m4a"
    command.contains(".wav") -> "wav"
    command.contains(".flac") -> "flac"
    command.contains(".webm") -> "webm"
    command.contains(".mkv") -> "mkv"
    command.contains(".gif") -> "gif"
    else -> "mp4"
}
