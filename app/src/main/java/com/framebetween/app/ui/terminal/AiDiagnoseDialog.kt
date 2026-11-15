package com.framebetween.app.ui.terminal
import com.framebetween.app.core.L10n

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.framebetween.app.core.AiCommandParser

/**
 * 「AI 解析」浮层对话框：展示失败原因 / 修正命令，底部可继续追问。
 * 点击对话框外、返回键或右上角关闭都会隐藏到【AI 解析】按钮，状态由页面保留，
 * 再次点击按钮即可恢复。
 */
@Composable
fun AiDiagnoseDialog(
    reply: String,
    loading: Boolean,
    error: String,
    onDismiss: () -> Unit,
    onFollowUp: (String) -> Unit,
    onUseCommand: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var draft by remember { mutableStateOf("") }
    var selectedIndex by remember(reply) { mutableStateOf(0) }
    val commands = remember(reply) { AiCommandParser.extractAll(reply) }
    val command = commands.getOrNull(selectedIndex).orEmpty()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 560.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        L10n.t("AI 解析"),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = L10n.t("隐藏到 AI 解析按钮")
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))

                if (loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            L10n.t("正在等待 AI 回复…"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (reply.isNotBlank()) {
                        Text(reply, style = MaterialTheme.typography.bodySmall)
                    } else if (!loading && error.isBlank()) {
                        Text(
                            L10n.t("暂无回复"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (error.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
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
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(commands.size) { index ->
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
                            Modifier.padding(10.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onUseCommand(command) },
                            modifier = Modifier.weight(1f)
                        ) { Text(L10n.t("填入命令框")) }
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(command)) },
                            modifier = Modifier.weight(1f)
                        ) { Text(L10n.t("复制命令")) }
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text(L10n.t("继续询问")) },
                    placeholder = { Text(L10n.t("补充错误现象，或让 AI 换一种方案")) },
                    minLines = 1,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            onFollowUp(draft.trim())
                            draft = ""
                        },
                        enabled = !loading && draft.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text(L10n.t("发送")) }
                    TextButton(
                        onClick = { clipboard.setText(AnnotatedString(reply)) },
                        enabled = reply.isNotBlank()
                    ) { Text(L10n.t("复制全文")) }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    L10n.t("点击对话框外或返回可隐藏，再次点击【AI 解析】可恢复本次对话。"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
