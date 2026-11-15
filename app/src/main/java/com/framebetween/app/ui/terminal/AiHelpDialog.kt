package com.framebetween.app.ui.terminal
import com.framebetween.app.core.L10n

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.framebetween.app.core.AiChatStore
import com.framebetween.app.core.AiClient
import com.framebetween.app.core.AiCommandParser
import com.framebetween.app.core.AiSettings
import com.framebetween.app.core.ChatMessage
import com.framebetween.app.core.MediaInfo
import com.framebetween.app.core.Settings
import com.framebetween.app.ui.common.providerLogoRes
import kotlinx.coroutines.launch

private fun quickQuestions() = listOf(
    L10n.t("把视频压缩到指定大小"),
    L10n.t("提高视频亮度和对比度"),
    L10n.t("视频添加水印和滤镜"),
    L10n.t("把这个视频倒放"),
    L10n.t("视频加速 2 倍"),
    L10n.t("修改视频封面")
)

/**
 * 「求助 AI」对话框。
 *
 * - 提示词默认开启：提问附带 ffprobe 媒体参数与固定模板，回复中的 {ffmpeg命令} 可选用；
 *   关闭后不做任何包装，用户输入原样发送、不带 system 消息。
 * - 连续对话：切换为常规聊天对话框，用户消息与默认头像在右、AI 回复在左且原文展示，
 *   可多轮追问；对话保存在 [AiChatStore]，切换开关 / 关闭弹窗都不清除，
 *   只有【清屏】或设置页【清除软件缓存】会清空。
 */
@Composable
fun AiHelpDialog(
    media: MediaInfo?,
    /** 提到页面级保存：执行失败后的「AI 解析」会复用这份剪辑需求填 {剪辑需求}。 */
    question: String,
    onQuestionChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onUseCommand: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    // 三个滚动状态在组合顶部无条件创建，避免切换开关 / 分支时打乱 slot table
    val bodyScroll = rememberScrollState()
    val quickScroll = rememberScrollState()
    val fullScroll = rememberScrollState()
    val promptEnabled by Settings.aiPromptEnabled.collectAsState()
    val chatMode by Settings.aiChatMode.collectAsState()
    val messages by AiChatStore.messages.collectAsState()
    val provider by AiSettings.provider.collectAsState()

    var answer by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var showFullAnswer by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableStateOf(0) }
    var draft by remember { mutableStateOf("") }
    val configured = AiSettings.effective().isValid
    val commands = remember(answer) { AiCommandParser.extractAll(answer) }
    val command = commands.getOrNull(selectedIndex).orEmpty()
    val preview = if (answer.length > 400) answer.take(400) + "\n…" else answer

    /** 单轮模式：保留命令提取与备选；关闭提示词时按原文发送。 */
    fun sendSingle() {
        if (question.isBlank() || loading) return
        loading = true
        error = ""
        answer = ""
        selectedIndex = 0
        val request = question.trim()
        val useTemplate = promptEnabled
        scope.launch {
            val result = AiClient.ask(request, media, useTemplate = useTemplate, raw = !useTemplate)
            loading = false
            if (result.success) answer = result.text else error = result.text
        }
    }

    /** 连续对话：首轮按开关决定是否套模板，后续轮携带全部上下文，回复原文入列。 */
    fun sendChat() {
        val text = draft.trim()
        if (text.isBlank() || loading) return
        val historySnapshot = AiChatStore.history()
        val useTemplate = promptEnabled && historySnapshot.isEmpty()
        loading = true
        error = ""
        draft = ""
        scope.launch {
            val result = AiClient.ask(
                text,
                media,
                useTemplate = useTemplate,
                history = historySnapshot,
                raw = !promptEnabled
            )
            loading = false
            if (result.success) {
                val sent = if (useTemplate) result.sentPrompt else text
                AiChatStore.append(
                    ChatMessage("user", sent, text),
                    ChatMessage("assistant", result.text)
                )
            } else {
                error = result.text
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            // 连续对话占满宽度做常规聊天窗口，单轮模式保持系统默认紧凑宽度
            usePlatformDefaultWidth = !chatMode
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .heightIn(max = if (chatMode) 640.dp else 620.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                // 标题 + 两个开关
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        L10n.t("求助 AI"),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = !promptEnabled,
                        onCheckedChange = { Settings.setAiPromptEnabled(!it) }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(L10n.t("关闭提示词"), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = chatMode,
                        onCheckedChange = { Settings.setAiChatMode(it) }
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(L10n.t("连续对话"), style = MaterialTheme.typography.labelMedium)
                }
                if (!configured) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        L10n.t("尚未配置 AI：请在「设置 → AI 助手」中选择模型提供商，或填写自定义 API 地址与模型；本地模型无需密钥。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(8.dp))

                if (chatMode) {
                    ChatBody(
                        messages = messages,
                        loading = loading,
                        error = error,
                        provider = provider,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            placeholder = { Text(L10n.t("输入消息，继续和 AI 对话")) },
                            minLines = 1,
                            maxLines = 3,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(6.dp))
                        Button(
                            onClick = { sendChat() },
                            enabled = !loading && draft.isNotBlank()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        L10n.t("连续对话中 AI 回复原文展示，不再提取 ffmpeg 命令；对话内容会保留到清屏或清除软件缓存。"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // 对话框内必须给滚动区一个明确的最大高度：父 Column 是 wrap-content，
                    // 无约束的 verticalScroll 会把内容全部测量出来再被 Card 裁掉，导致底部看不到也滑不动。
                    Column(
                        Modifier
                            .heightIn(max = 420.dp)
                            .verticalScroll(bodyScroll)
                    ) {
                        Text(
                            if (promptEnabled) L10n.t("常见问题") else L10n.t("提示词已关闭，将直接发送你输入的内容"),
                            style = MaterialTheme.typography.labelMedium
                        )
                        if (promptEnabled) {
                            Spacer(Modifier.height(4.dp))
                            Row(
                                Modifier.horizontalScroll(quickScroll),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // 横向滑动显示全部常见问题；不用 LazyRow 避免在竖向滚动区内抢手势
                                quickQuestions().forEach { item ->
                                    FilterChip(
                                        selected = false,
                                        onClick = { onQuestionChange(item) },
                                        label = { Text(item, maxLines = 1) }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = question,
                            onValueChange = onQuestionChange,
                            label = { Text(L10n.t("描述你的剪辑需求")) },
                            placeholder = { Text(L10n.t("例如：把这段视频转成 H.265 并保持原码率")) },
                            minLines = 2,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { sendSingle() },
                            enabled = !loading && question.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, null)
                            Spacer(Modifier.width(8.dp))
                            Text(L10n.t("发送"))
                        }
                        if (loading) {
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        if (error.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        if (answer.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(L10n.t("AI 回复"), style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { showFullAnswer = true }) {
                                    Icon(
                                        Icons.Default.Visibility,
                                        contentDescription = L10n.t("查看完整回复"),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(L10n.t("全文"))
                                }
                            }
                            Card(Modifier.fillMaxWidth()) {
                                Text(
                                    preview,
                                    Modifier.padding(12.dp).heightIn(max = 180.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        if (commands.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                if (commands.size > 1) {
                                    L10n.t("识别到 %1\$s 条命令，请选择要使用的备选", commands.size)
                                } else {
                                    L10n.t("识别到的命令")
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                            if (commands.size > 1) {
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    commands.indices.forEach { index ->
                                        FilterChip(
                                            selected = index == selectedIndex,
                                            onClick = { selectedIndex = index },
                                            label = { Text(L10n.t("备选 %1\$s", index + 1)) }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Card(Modifier.fillMaxWidth()) {
                                Text(
                                    command,
                                    Modifier.padding(12.dp).heightIn(max = 160.dp),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onUseCommand(command); onDismiss() },
                                    modifier = Modifier.weight(1f)
                                ) { Text(L10n.t("填入命令框")) }
                                OutlinedButton(
                                    onClick = { clipboard.setText(AnnotatedString(command)) },
                                    modifier = Modifier.weight(1f)
                                ) { Text(L10n.t("复制命令")) }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (promptEnabled) {
                                L10n.t("提问时会附带 ffprobe 读取的媒体参数（时长、编码、码率、分辨率等）；媒体文件本身不会上传。")
                            } else {
                                L10n.t("媒体文件本身不会上传。")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 右下角：清屏 + 关闭
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (chatMode) {
                        TextButton(
                            onClick = {
                                AiChatStore.clear()
                                error = ""
                                loading = false
                            },
                            enabled = messages.isNotEmpty()
                        ) { Text(L10n.t("清屏")) }
                        Spacer(Modifier.width(4.dp))
                    }
                    TextButton(onClick = onDismiss) { Text(L10n.t("关闭")) }
                }
            }
        }
    }

    if (showFullAnswer && answer.isNotBlank()) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showFullAnswer = false },
            title = { Text(L10n.t("AI 回复全文")) },
            text = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(fullScroll)
                ) {
                    Text(answer, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(answer)) }) {
                    Text(L10n.t("复制全文"))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFullAnswer = false }) { Text(L10n.t("关闭")) }
            }
        )
    }
}

/** 聊天记录：用户消息与默认头像在右，AI 回复与图标在左，回复原文展示。 */
@Composable
private fun ChatBody(
    messages: List<ChatMessage>,
    loading: Boolean,
    error: String,
    provider: String,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, loading) {
        if (messages.isNotEmpty()) {
            val target = messages.lastIndex + if (loading) 1 else 0
            listState.scrollToItem(target)
        }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            // 固定可滚动高度：不用 weight，避免 Dialog 在无限高度约束下测量异常
            .heightIn(min = 180.dp, max = 320.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        if (messages.isEmpty() && !loading) {
            Text(
                L10n.t("暂无对话，发送第一条消息开始"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(messages, key = { index, item -> "$index-${item.role}" }) { _, item ->
                ChatBubble(item = item, provider = provider)
            }
            if (loading) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AiAvatar(provider)
                        Spacer(Modifier.width(6.dp))
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            L10n.t("正在等待 AI 回复…"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (error.isNotBlank()) {
                item {
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(item: ChatMessage, provider: String, modifier: Modifier = Modifier) {
    val mine = item.role == "user"
    val bubble = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val onBubble = if (mine) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!mine) {
            AiAvatar(provider)
            Spacer(Modifier.width(6.dp))
        }
        Card(
            modifier = Modifier.widthIn(max = 260.dp),
            colors = CardDefaults.cardColors(containerColor = bubble),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                item.display,
                Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodySmall,
                color = onBubble
            )
        }
        if (mine) {
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Default.AccountCircle,
                contentDescription = L10n.t("用户头像"),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun AiAvatar(provider: String) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val res = providerLogoRes(provider, dark)
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (res != null) {
            Image(
                painter = painterResource(res),
                contentDescription = L10n.t("AI 头像"),
                modifier = Modifier.size(20.dp)
            )
        } else {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = L10n.t("AI 头像"),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
