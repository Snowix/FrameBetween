package com.framebetween.app.core
import com.framebetween.app.core.L10n

import android.net.Uri
import com.framebetween.app.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAI 兼容的 /chat/completions 客户端。只把用户主动输入的问题、固定提示词与
 * ffprobe 读到的媒体参数发给用户自己配置的端点；媒体文件内容不上传，密钥只存在本机。
 * 支持多轮 history（AI 解析的「继续询问」）与专门的诊断请求。
 */
object AiClient {

    /** 多轮对话中的一轮，role 为 user / assistant。 */
    data class ChatTurn(val role: String, val content: String)

    private const val SYSTEM_PROMPT = "You are a rigorous FFmpeg command-line expert. " +
        "Runtime: ffmpeg-kit-next 9.0.2 (FFmpeg 9.0.2) on Android, built with native drawtext (freetype, fontconfig, fribidi, harfbuzz) and libass. " +
        "Follow the user message's task specification and required output sections strictly; answer in the same language as the request. " +
        "For every final command, wrap the WHOLE command in one pair of braces, e.g. {ffmpeg -i {in} -c copy -t 10 {out}}. " +
        "Keep the literal {in} and {out} placeholders for paths, state the overwrite policy with -y or -n, " +
        "and give at most 3 alternatives, each in its own separate brace block. " +
        "Section A holds the final command, section B covers risks and fallback commands. " +
        "For a troubleshooting request, first explain the failure, then give corrected commands in the same brace format. " +
        "Available encoders: libx264, libx265, libmp3lame, aac, libvpx-vp9, libopus, flac. The app defaults to H.265 (libx265). " +
        "drawtext is available natively and is NOT rewritten; use drawtext=text='...':fontfile=/system/fonts/NotoSansCJK-Regular.ttc:fontsize=h/22 for text overlays (CJK renders correctly). " +
        "Use the subtitles/ass filter only when burning an existing SRT/ASS subtitle file. " +
        "Usable filters: scale, crop, hflip, vflip, transpose, fps, reverse, areverse, setpts, atempo, overlay, drawbox, curves, colorchannelmixer, palettegen, paletteuse, drawtext, subtitles, ass, format, eq, delogo. " +
        "Never use shell pipes, redirection or semicolons in the executable command. " +
        "Prefer the shortest command that satisfies the request: keep stream copy (-c copy / -c:a copy) and leave bitrate, codec and pixel format unchanged unless the user explicitly asks to change them."

    /** sentPrompt 供 UI 保存首轮上下文，实现「继续询问」。 */
    data class Result(val success: Boolean, val text: String, val sentPrompt: String = "")
    data class ModelResult(val success: Boolean, val models: List<String>, val message: String)

    /**
     * 发送前重新用 ffprobe 查询当前输入文件，把「输入流 / 大小 / 时长 / 编码器 / 分辨率 /
     * 帧率 / 音视频码率 / 像素格式 / 音频流」填进提示词的 {媒体信息}；
     * 用户的剪辑需求填进 {剪辑需求}。probe 失败时回退到导入阶段已缓存的 MediaInfo。
     */
    private fun refreshProbe(cached: MediaInfo?): MediaInfo? {
        if (cached == null || cached.path.isBlank()) return cached
        val fresh = runCatching {
            MediaRepository.probe(cached.path, cached.displayName)
        }.getOrNull() ?: return cached
        val empty = fresh.sizeBytes <= 0 && fresh.durationMs <= 0 &&
            fresh.video == null && fresh.audio == null
        return if (empty) cached else fresh
    }

    suspend fun ask(
        question: String,
        media: MediaInfo? = null,
        useTemplate: Boolean = true,
        history: List<ChatTurn> = emptyList(),
        /** 关闭提示词：不套模板、不带 system 角色消息，把用户输入原样送出。 */
        raw: Boolean = false
    ): Result = withContext(Dispatchers.IO) {
        val cfg = AiSettings.effective()
        if (!cfg.isValid) {
            return@withContext Result(
                false,
                L10n.t("请先在「设置 → AI 助手」中配置 API 地址与模型；本地模型无需密钥")
            )
        }
        val userText = if (useTemplate && !raw) {
            val probed = refreshProbe(media)
            AiPrompt.build(AiPrompt.TEMPLATE, question, AiPrompt.describe(probed))
        } else {
            question
        }
        sendRequest(cfg, userText, history, includeSystem = !raw)
    }

    /**
     * 「AI 解析」首轮请求：重新 ffprobe 后把 {剪辑需求} / {媒体信息} / {FFmpeg版本} /
     * {脱敏命令} / {错误信息} 代入诊断模板。追问请改用 [ask] 并传入 history，
     * 避免每轮重复整份模板。
     */
    suspend fun diagnose(
        question: String,
        media: MediaInfo?,
        redactedCommand: String,
        errorText: String,
        history: List<ChatTurn> = emptyList()
    ): Result = withContext(Dispatchers.IO) {
        val cfg = AiSettings.effective()
        if (!cfg.isValid) {
            return@withContext Result(
                false,
                L10n.t("请先在「设置 → AI 助手」中配置 API 地址与模型；本地模型无需密钥")
            )
        }
        val probed = refreshProbe(media)
        // 版本读取放在 IO 线程，避免在主线程触碰原生库
        val version = FFmpegVersion.current()
        val userText = AiPrompt.buildDiagnose(
            question,
            AiPrompt.describe(probed),
            version,
            redactedCommand,
            errorText
        )
        sendRequest(cfg, userText, history)
    }

    /**
     * 「AI 效果评估」首轮请求：输入 / 输出文件都在本机重新跑一次 ffprobe，
     * 展开成输入流、大小、时长、编码器、分辨率、帧率、音视频码率、像素格式与音频流；
     * 命令真实路径统一脱敏；剪辑需求取「求助 AI」输入框内容。
     * 媒体文件内容始终不上传，只发送上述文本摘要。
     */
    suspend fun evaluate(
        request: String,
        inputMedia: MediaInfo?,
        inputPath: String,
        outputRef: String,
        command: String
    ): Result = withContext(Dispatchers.IO) {
        val cfg = AiSettings.effective()
        if (!cfg.isValid) {
            return@withContext Result(
                false,
                L10n.t("请先在「设置 → AI 助手」中配置 API 地址与模型；本地模型无需密钥")
            )
        }
        val input = probeInputForEvaluate(inputPath.ifBlank { inputMedia?.path.orEmpty() }, inputMedia)
        val output = probeOutputForEvaluate(outputRef)
        val redacted = CommandRedactor.redact(command, inputPath, outputRef)
        // 输出探测失败时留空，交给 buildEvaluate 写入「输出文件不可读」提示
        val outputInfo = output?.let { AiPrompt.describe(it, AiPrompt.OUTPUT_SUBJECT) }.orEmpty()
        val userText = AiPrompt.buildEvaluate(
            request, AiPrompt.describe(input), redacted, outputInfo
        )
        sendRequest(cfg, userText, emptyList())
    }

    private fun probeInputForEvaluate(path: String, cached: MediaInfo?): MediaInfo? {
        if (path.isBlank()) return cached
        val file = File(path)
        if (!file.isFile) return cached
        val fresh = runCatching {
            MediaRepository.probe(path, cached?.displayName ?: file.name)
        }.getOrNull() ?: return cached
        val empty = fresh.sizeBytes <= 0 && fresh.durationMs <= 0 &&
            fresh.video == null && fresh.audio == null
        return if (empty) cached else fresh
    }

    /** 输出可能是真实路径，也可能是相册 / SAF 的 content://；后者只读复制一份做探测后立即删除。 */
    private fun probeOutputForEvaluate(ref: String): MediaInfo? {
        if (ref.isBlank()) return null
        val name = MediaRepository.outputName(ref)
        if (!MediaRepository.isContentRef(ref)) {
            val file = File(ref)
            if (!file.isFile || file.length() <= 0) return null
            return runCatching { MediaRepository.probe(ref, name) }.getOrNull()
        }
        val uri = Uri.parse(ref)
        val direct = MediaRepository.resolveOriginalPath(uri)
        if (direct != null && File(direct).isFile) {
            return runCatching { MediaRepository.probe(direct, name) }.getOrNull()
        }
        return runCatching {
            val ext = name.substringAfterLast('.', "mp4")
            val tmp = File(App.instance.cacheDir, "eval_${System.currentTimeMillis()}.$ext")
            val stream = App.instance.contentResolver.openInputStream(uri)
                ?: return@runCatching null
            stream.use { input -> tmp.outputStream().use { out -> input.copyTo(out, 64 * 1024) } }
            try {
                MediaRepository.probe(tmp.absolutePath, name)
            } finally {
                tmp.delete()
            }
        }.getOrNull()
    }

    /** 真正的 HTTP 请求：system（可省略）+ 历史对话 + 本轮 user。调用方已切到 IO 线程。 */
    private fun sendRequest(
        cfg: AiConfig,
        userText: String,
        history: List<ChatTurn>,
        includeSystem: Boolean = true
    ): Result {
        var connection: HttpURLConnection? = null
        try {
            val endpoint = cfg.baseUrl.trimEnd('/') + "/chat/completions"
            val payload = JSONObject().apply {
                put("model", cfg.model)
                put("temperature", 0.3)
                put("messages", JSONArray().apply {
                    if (includeSystem) {
                        put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
                    }
                    history.forEach {
                        put(JSONObject().put("role", it.role).put("content", it.content))
                    }
                    put(JSONObject().put("role", "user").put("content", userText))
                })
            }
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 60000
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                if (cfg.apiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
                }
                setRequestProperty("Accept", "application/json")
            }
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                return Result(
                    false,
                    L10n.t("请求失败（HTTP %1\$s）：%2\$s", code, body.take(180)),
                    userText
                )
            }
            val answer = JSONObject(body)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "")
                .orEmpty()
            if (answer.isBlank()) {
                return Result(false, L10n.t("模型未返回内容，请检查模型名称或稍后重试"), userText)
            }
            return Result(true, answer.trim(), userText)
        } catch (e: Exception) {
            return Result(
                false,
                L10n.t("请求失败：%1\$s", e.message ?: L10n.t("网络不可用或地址无效")),
                userText
            )
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * 查询可用模型：优先 OpenAI 兼容的 /v1/models（返回 data[].id），
     * 失败时回退 Ollama 原生的 /api/tags（返回 models[].name）。
     */
    suspend fun fetchModels(cfg: AiConfig = AiSettings.effective()): ModelResult = withContext(Dispatchers.IO) {
        if (cfg.baseUrl.isBlank()) {
            return@withContext ModelResult(false, emptyList(), L10n.t("请先填写 API 地址"))
        }
        val base = cfg.baseUrl.trimEnd('/')
        val nativeBase = base.removeSuffix("/v1").removeSuffix("/v1/")
        val attempts = listOf(
            "$base/models" to "data",
            "$nativeBase/api/tags" to "models"
        )
        val errors = mutableListOf<String>()
        for ((endpoint, arrayKey) in attempts) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10000
                    readTimeout = 20000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/json")
                    if (cfg.apiKey.isNotBlank()) {
                        setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
                    }
                }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    errors += "HTTP $code"
                    continue
                }
                val array: JSONArray? = runCatching { JSONObject(body).optJSONArray(arrayKey) }.getOrNull()
                    ?: runCatching { JSONArray(body) }.getOrNull()
                val names = array?.let { a ->
                    (0 until a.length()).mapNotNull { i ->
                        a.optJSONObject(i)?.optString("id", "").orEmpty()
                            .ifBlank { a.optJSONObject(i)?.optString("name", "").orEmpty() }
                            .takeIf { it.isNotBlank() }
                    }
                }.orEmpty().distinct().sorted()
                if (names.isNotEmpty()) {
                    return@withContext ModelResult(true, names, L10n.t("找到 %1\$s 个可用模型", names.size))
                }
                errors += L10n.t("接口未返回模型列表")
            } catch (e: Exception) {
                errors += e.message ?: L10n.t("连接失败")
            } finally {
                connection?.disconnect()
            }
        }
        ModelResult(false, emptyList(), errors.distinct().joinToString(" / ").take(180))
    }
}
