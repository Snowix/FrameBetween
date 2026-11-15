package com.framebetween.app.ui.transform
import com.framebetween.app.core.L10n

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.framebetween.app.core.AppState
import com.framebetween.app.core.Commands
import com.framebetween.app.core.FFmpegEngine
import com.framebetween.app.core.FlipOp
import com.framebetween.app.core.MediaRepository
import com.framebetween.app.ui.common.ActionButton
import com.framebetween.app.ui.common.CommandPreview
import com.framebetween.app.ui.common.CurrentMediaCard
import com.framebetween.app.ui.common.InfoRow
import com.framebetween.app.ui.common.Label
import com.framebetween.app.ui.common.ScreenTopBar
import com.framebetween.app.ui.common.SectionCard
import com.framebetween.app.ui.common.nextOutput
import com.framebetween.app.ui.common.rememberMediaPicker
import com.framebetween.app.ui.common.rememberOutput

@Composable
fun TransformScreen(onBack: () -> Unit, onSubmitted: () -> Unit) {
    val media by AppState.current.collectAsState()
    val pick = rememberMediaPicker()
    var op by rememberSaveable { mutableStateOf(FlipOp.HFLIP) }
    var metadataOnly by rememberSaveable { mutableStateOf(false) }
    var output by rememberOutput("mp4", "flip")

    val useMetadata = metadataOnly && op.rotation != 0
    val args = media?.let { Commands.transform(it.path, output, op, useMetadata, it.videoKbps) } ?: emptyList()

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(L10n.t("翻转与镜像"), L10n.t("支持无损元数据旋转"), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Label(L10n.t("输入文件"))
            CurrentMediaCard(onPick = pick)

            SectionCard(L10n.t("选择变换"), icon = Icons.Default.Flip) {
                FlipOp.entries.chunked(2).forEach { group ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        group.forEach { item ->
                            FilterChip(
                                selected = op == item,
                                onClick = { op = item },
                                label = { Text(item.label) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (group.size == 1) Spacer(Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        InfoRow(L10n.t("滤镜"), op.filter)
                        InfoRow(L10n.t("像素重排"), if (useMetadata) L10n.t("否（仅写旋转元数据）") else L10n.t("是（需要重编码）"))
                        InfoRow(L10n.t("音频"), L10n.t("直接复制（不重新编码）"))
                    }
                }
            }

            SectionCard(L10n.t("无损快速旋转")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(L10n.t("仅修改旋转元数据"), modifier = Modifier.weight(1f))
                    Switch(
                        checked = metadataOnly,
                        onCheckedChange = { metadataOnly = it },
                        enabled = op.rotation != 0
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    L10n.t("适用于 90°/180°/270° 旋转：不重编码、秒级完成，但依赖播放器读取 rotation 元数据；") +
                        L10n.t("部分编辑器/平台可能忽略该标记，此时请关闭此开关用像素级旋转。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    InfoRow(L10n.t("源视频码率"), "${media?.videoKbps ?: 0} kbps")
                    InfoRow(L10n.t("输出码率"), if (useMetadata) L10n.t("直接复制（完全不变）") else L10n.t("沿用源视频码率"))
                    Text(
                        L10n.t("像素级变换会重编码视频，但按源平均码率设置 ABR 目标并加 maxrate 上限，音频直接复制，体积不会变大。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            CommandPreview(Commands.preview(args))
            ActionButton(
                text = L10n.t("开始处理"),
                enabled = media?.hasVideo == true && args.isNotEmpty(),
                onClick = {
                    val info = media ?: return@ActionButton
                    FFmpegEngine.submit(L10n.t("翻转 / 镜像"), args, output, info.durationMs)
                    output = nextOutput("mp4", "flip")
                    onSubmitted()
                }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}
