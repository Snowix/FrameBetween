package com.framebetween.app.ui.crop
import com.framebetween.app.core.L10n

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.framebetween.app.core.AppState
import com.framebetween.app.core.Commands
import com.framebetween.app.core.CropConfig
import com.framebetween.app.core.FFmpegEngine
import com.framebetween.app.core.MediaRepository
import com.framebetween.app.ui.common.ActionButton
import com.framebetween.app.ui.common.CommandPreview
import com.framebetween.app.ui.common.CurrentMediaCard
import com.framebetween.app.ui.common.InfoRow
import com.framebetween.app.ui.common.Label
import com.framebetween.app.ui.common.ScreenTopBar
import com.framebetween.app.ui.common.SectionCard
import com.framebetween.app.ui.common.DraggableSelectionBox
import com.framebetween.app.ui.common.VideoPreview
import com.framebetween.app.ui.common.fitRect
import com.framebetween.app.ui.common.nextOutput
import com.framebetween.app.ui.common.rememberMediaPicker
import com.framebetween.app.ui.common.rememberOutput
import com.framebetween.app.util.even
import kotlin.math.roundToInt

private data class RatioPreset(val name: String, val w: Int, val h: Int)

private val ratioPresets = listOf(
    RatioPreset(L10n.t("全画面"), 0, 0),
    RatioPreset(L10n.t("原比例"), -1, -1),
    RatioPreset("1:1", 1, 1),
    RatioPreset("16:9", 16, 9),
    RatioPreset("4:3", 4, 3),
    RatioPreset("9:16", 9, 16)
)

@Composable
fun CropScreen(onBack: () -> Unit, onSubmitted: () -> Unit) {
    val media by AppState.current.collectAsState()
    val pick = rememberMediaPicker()

    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var left by rememberSaveable { mutableStateOf(0.1f) }
    var top by rememberSaveable { mutableStateOf(0.1f) }
    var right by rememberSaveable { mutableStateOf(0.9f) }
    var bottom by rememberSaveable { mutableStateOf(0.9f) }
    var output by rememberOutput("mp4", "crop")

    val video = media?.video
    val rect = fitRect(video?.width ?: 0, video?.height ?: 0, boxSize.width, boxSize.height)

    val videoWidth = video?.width ?: 0
    val videoHeight = video?.height ?: 0
    val pixelX = even((left * videoWidth).toInt())
    val pixelY = even((top * videoHeight).toInt())
    val pixelW = even(((right - left) * videoWidth).toInt()).coerceAtLeast(2)
    val pixelH = even(((bottom - top) * videoHeight).toInt()).coerceAtLeast(2)
    val cropConfig = CropConfig(pixelX, pixelY, pixelW, pixelH)
    val args = media?.let { Commands.crop(it.path, output, cropConfig, it.videoKbps) } ?: emptyList()

    fun applyRatio(rw: Int, rh: Int) {
        if (rw == 0) {
            left = 0f; top = 0f; right = 1f; bottom = 1f
            return
        }
        if (rw < 0) {
            left = 0.1f; top = 0.1f; right = 0.9f; bottom = 0.9f
            return
        }
        if (videoWidth <= 0 || videoHeight <= 0) return
        val videoAspect = videoWidth.toFloat() / videoHeight
        val target = rw.toFloat() / rh
        val widthN: Float
        val heightN: Float
        if (videoAspect > target) {
            widthN = target / videoAspect
            heightN = 1f
        } else {
            widthN = 1f
            heightN = videoAspect / target
        }
        left = (1f - widthN) / 2
        top = (1f - heightN) / 2
        right = left + widthN
        bottom = top + heightN
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(L10n.t("画面裁剪"), L10n.t("拖拽选区 · 实时显示像素坐标"), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Label(L10n.t("输入文件"))
            CurrentMediaCard(onPick = pick)

            if (media != null && video != null && videoWidth > 0) {
                VideoPreview(
                    path = media!!.path,
                    modifier = Modifier.fillMaxWidth().height(260.dp),
                    onSizeChanged = { boxSize = it }
                ) {
                    if (boxSize.width > 0) {
                        DraggableSelectionBox(
                            rect = rect,
                            left = left,
                            top = top,
                            right = right,
                            bottom = bottom,
                            label = "$pixelW × $pixelH  @ ($pixelX, $pixelY)",
                            onChange = { nl, nt, nr, nb ->
                                left = nl
                                top = nt
                                right = nr
                                bottom = nb
                            }
                        )
                    }
                }
                Text(
                    L10n.t("在画面上拖动选区移动位置，拖动四角白色圆点调整大小；坐标自动对齐到偶数像素。"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                SectionCard(L10n.t("无法预览")) {
                    Text(
                        L10n.t("请先选择一个包含视频流的文件（纯音频不支持画面裁剪）。"),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            SectionCard(L10n.t("比例预设")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ratioPresets.chunked(3).forEach { group ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            group.forEach { preset ->
                                FilterChip(
                                    selected = false,
                                    onClick = { applyRatio(preset.w, preset.h) },
                                    label = { Text(preset.name) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            SectionCard(L10n.t("裁剪参数（视频原始像素）"), icon = Icons.Default.Crop) {
                InfoRow(L10n.t("源分辨率"), "$videoWidth × $videoHeight")
                InfoRow(L10n.t("起点 X / Y"), "$pixelX / $pixelY px")
                InfoRow(L10n.t("选区宽 / 高"), "$pixelW / $pixelH px")
                InfoRow(L10n.t("crop 滤镜"), "crop=$pixelW:$pixelH:$pixelX:$pixelY")
                InfoRow(
                    L10n.t("输出占比"),
                    "${(((right - left) * (bottom - top)) * 100).toInt()}%"
                )
            }

            CommandPreview(Commands.preview(args))
            ActionButton(
                text = L10n.t("开始裁剪画面"),
                enabled = media != null && video != null && videoWidth > 0 && args.isNotEmpty(),
                onClick = {
                    val source = media ?: return@ActionButton
                    FFmpegEngine.submit(L10n.t("画面裁剪"), args, output, source.durationMs)
                    output = nextOutput("mp4", "crop")
                    onSubmitted()
                }
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

// 选区拖动已抽到 ui/common/DraggableSelection.kt，与「去水印·智能填补」共用同一套交互。
