package com.framebetween.app.ui.home
import com.framebetween.app.core.L10n

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Gif
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.framebetween.app.core.AppBrand
import com.framebetween.app.ui.Routes
import com.framebetween.app.ui.common.CurrentMediaCard
import com.framebetween.app.ui.common.Label
import com.framebetween.app.ui.common.ScreenTopBar
import com.framebetween.app.ui.common.rememberMediaPicker

private data class Tool(
    val route: String,
    val title: String,
    val description: String,
    val icon: ImageVector
)

@Composable
fun HomeScreen(onOpen: (String) -> Unit) {
    val pick = rememberMediaPicker()
    val tools = listOf(
        Tool(Routes.TERMINAL, L10n.t("FFmpeg 命令行"), L10n.t("直接执行 ffmpeg 参数"), Icons.Default.Terminal),
        Tool(Routes.CONVERT, L10n.t("压缩 / 格式转换"), L10n.t("H.265、MP3、码率与分辨率"), Icons.Default.Tune),
        Tool(Routes.TRIM, L10n.t("时长裁剪"), L10n.t("滑动区间或输入精确帧"), Icons.Default.ContentCut),
        Tool(Routes.CROP, L10n.t("画面裁剪"), L10n.t("预览上拖拽选区、显示像素"), Icons.Default.Crop),
        Tool(Routes.MERGE, L10n.t("视频合并"), L10n.t("多段拼接与顺序调整"), Icons.AutoMirrored.Filled.MergeType),
        Tool(Routes.TRANSFORM, L10n.t("翻转与镜像"), L10n.t("水平 / 垂直 / 旋转"), Icons.Default.Flip),
        Tool(Routes.SPEED, L10n.t("视频变速"), L10n.t("0.25–4 倍 · 音画同步"), Icons.Default.Speed),
        Tool(Routes.WATERMARK, L10n.t("字幕和水印"), L10n.t("提取 / 嵌入 / 烧录 · 添加与去除"), Icons.Default.Image),
        Tool(Routes.GIF, L10n.t("视频转 GIF"), L10n.t("调色板优化、帧率与尺寸可调"), Icons.Default.Gif),
        Tool(Routes.AUDIO, L10n.t("消音 / 音频提取"), L10n.t("移除音轨或导出 MP3 等"), Icons.Default.MusicNote),
        Tool(Routes.AUDIO_JOIN, L10n.t("音频拼接"), L10n.t("顺序拼接 / 音轨合并 · 音量与淡化"), Icons.AutoMirrored.Filled.PlaylistAdd),
        Tool(Routes.AUDIO_CUT, L10n.t("音频剪切"), L10n.t("波形截取 · 变速试听"), Icons.Default.ContentCut)
    )

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(AppBrand.displayName(), L10n.t("本地处理 · 不上传文件"))
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Label(L10n.t("当前文件"))
            CurrentMediaCard(onPick = pick)
            Label(L10n.t("全部工具"))
            tools.chunked(2).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { tool ->
                        ToolCard(tool, Modifier.weight(1f), onOpen)
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun RowScope.ToolCard(
    tool: Tool,
    modifier: Modifier,
    onOpen: (String) -> Unit
) {
    Card(
        onClick = { onOpen(tool.route) },
        modifier = modifier.height(124.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Icon(
                tool.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(tool.title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}
