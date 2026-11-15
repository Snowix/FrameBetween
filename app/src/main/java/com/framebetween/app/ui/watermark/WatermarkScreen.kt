package com.framebetween.app.ui.watermark
import com.framebetween.app.core.L10n

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.framebetween.app.core.AppState
import com.framebetween.app.core.Commands
import com.framebetween.app.core.FFmpegEngine
import com.framebetween.app.core.RemoveMode
import com.framebetween.app.core.RemoveWatermarkConfig
import com.framebetween.app.core.SubtitleAction
import com.framebetween.app.core.SubtitleAssets
import com.framebetween.app.core.WatermarkAssets
import com.framebetween.app.core.WatermarkConfig
import com.framebetween.app.core.WatermarkMotion
import com.framebetween.app.ui.common.ActionButton
import com.framebetween.app.ui.common.CommandPreview
import com.framebetween.app.ui.common.CurrentMediaCard
import com.framebetween.app.ui.common.Label
import com.framebetween.app.ui.common.LabeledDropdown
import com.framebetween.app.ui.common.NumberField
import com.framebetween.app.ui.common.ScreenTopBar
import com.framebetween.app.ui.common.SectionCard
import com.framebetween.app.ui.common.DraggableSelectionBox
import com.framebetween.app.ui.common.VideoPreview
import com.framebetween.app.ui.common.fitRect
import com.framebetween.app.ui.common.nextOutput
import com.framebetween.app.ui.common.rememberMediaPicker
import com.framebetween.app.ui.common.rememberOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** 文字水印预设色板：写成函数，切换界面语言后名称会随 L10n 一起刷新。 */
private fun textPalette(): List<Pair<Color, String>> = listOf(
    Color.White to L10n.t("白色"),
    Color.Black to L10n.t("黑色"),
    Color(0xFFFF5252) to L10n.t("红色"),
    Color(0xFFFFD54F) to L10n.t("黄色"),
    Color(0xFF69F0AE) to L10n.t("绿色"),
    Color(0xFF40C4FF) to L10n.t("蓝色"),
    Color(0xFFB388FF) to L10n.t("紫色"),
    Color(0xFFFF80AB) to L10n.t("粉色")
)

@Composable
fun WatermarkScreen(onBack: () -> Unit, onSubmitted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val media by AppState.current.collectAsState()
    val pick = rememberMediaPicker()

    // —— 字幕 ——
    var subAction by rememberSaveable { mutableStateOf(SubtitleAction.EXTRACT) }
    var subPath by rememberSaveable { mutableStateOf("") }
    var subName by rememberSaveable { mutableStateOf("") }
    var container by rememberSaveable { mutableStateOf("mp4") }
    var outSubtitle by rememberOutput("srt", "subtitle")
    var outEmbed by rememberOutput(container, "subtitle")

    // —— 添加水印 ——
    var adding by rememberSaveable { mutableStateOf(true) }
    var wmKind by rememberSaveable { mutableStateOf("text") }
    var wmText by rememberSaveable { mutableStateOf("FrameBetween") }
    var wmFont by rememberSaveable { mutableStateOf("56") }
    var wmColorArgb by rememberSaveable { mutableStateOf(Color.White.toArgb()) }
    var wmImage by rememberSaveable { mutableStateOf("") }
    var wmImageName by rememberSaveable { mutableStateOf("") }
    var wmPosition by rememberSaveable { mutableStateOf("br") }
    var wmMargin by rememberSaveable { mutableStateOf("24") }
    var wmMotion by rememberSaveable { mutableStateOf(WatermarkMotion.STATIC) }
    var wmOpacity by rememberSaveable { mutableStateOf(1f) }
    var wmScale by rememberSaveable { mutableStateOf(1f) }
    var wmStart by rememberSaveable { mutableStateOf("0") }
    var wmEnd by rememberSaveable { mutableStateOf("0") }
    // < 0 表示还没在预览里拖动过，此时沿用九宫格预设位置
    var wmNormX by rememberSaveable { mutableStateOf(-1f) }
    var wmNormY by rememberSaveable { mutableStateOf(-1f) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    var outWatermark by rememberOutput("mp4", "watermark")

    // —— 去水印 ——
    var removeMode by rememberSaveable { mutableStateOf(RemoveMode.BLUR) }
    var rmX by rememberSaveable { mutableStateOf("0") }
    var rmY by rememberSaveable { mutableStateOf("0") }
    var rmW by rememberSaveable { mutableStateOf("200") }
    var rmH by rememberSaveable { mutableStateOf("80") }
    var rmIntensity by rememberSaveable { mutableStateOf("10") }

    var initializedPath by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(media?.path) {
        if (initializedPath == media?.path) return@LaunchedEffect
        initializedPath = media?.path
        val w = media?.video?.width ?: 0
        val h = media?.video?.height ?: 0
        if (w > 0 && h > 0) {
            rmX = (w * 0.05).toInt().toString()
            rmY = (h * 0.05).toInt().toString()
            rmW = (w * 0.22).toInt().coerceAtLeast(2).toString()
            rmH = (h * 0.10).toInt().coerceAtLeast(2).toString()
        }
        // 不要清零 previewSize：预览组件尺寸不变时 onSizeChanged 不会再次回调，
        // 清零会让叠加的水印 / 选框永久不出现。改由 VideoPreview 每次布局主动上报。
    }

    val subtitlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = uri.lastPathSegment.orEmpty().ifBlank { "subtitle.srt" }
            val path = withContext(Dispatchers.IO) {
                SubtitleAssets.importSubtitle(context, uri, name)
            }
            if (path == null) {
                AppState.notify(L10n.t("无法读取该字幕文件"))
            } else {
                subPath = path
                subName = name
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = withContext(Dispatchers.IO) {
                WatermarkAssets.importImage(context, uri)
            }
            if (path == null) {
                AppState.notify(L10n.t("无法读取该图片"))
            } else {
                wmImage = path
                wmImageName = uri.lastPathSegment.orEmpty().ifBlank { L10n.t("图片水印") }
            }
        }
    }

    /** 文字水印先由 Android Canvas 渲染成透明 PNG；生成放 IO 线程，改参数不会卡住预览。 */
    var wmAsset by remember { mutableStateOf("") }
    LaunchedEffect(wmKind, wmText, wmFont, wmImage, wmColorArgb) {
        wmAsset = if (wmKind == "text") {
            withContext(Dispatchers.IO) {
                WatermarkAssets.create(
                    context.applicationContext,
                    WatermarkAssets.Style(
                        text = wmText.ifBlank { "FrameBetween" },
                        fontSizePx = wmFont.toIntOrNull()?.coerceIn(16, 240) ?: 56,
                        color = wmColorArgb,
                        // 透明度统一交给 FFmpeg 的 colorchannelmixer，避免文字与图片叠加两次
                        opacity = 1f
                    )
                )
            }
        } else wmImage
    }

    val subArgs = when {
        media == null -> emptyList()
        subAction == SubtitleAction.EXTRACT -> Commands.extractSubtitle(media!!.path, outSubtitle)
        subPath.isBlank() -> emptyList()
        subAction == SubtitleAction.EMBED ->
            Commands.embedSubtitle(media!!.path, subPath, outEmbed, container)
        else -> Commands.burnSubtitles(media!!.path, outEmbed, subPath, media!!.videoKbps)
    }
    val subtitleOutput = if (subAction == SubtitleAction.EXTRACT) outSubtitle else outEmbed

    val wmConfig = WatermarkConfig(
        overlayPng = wmAsset,
        position = wmPosition,
        margin = wmMargin.toIntOrNull() ?: 24,
        opacity = wmOpacity,
        scale = wmScale,
        startSec = wmStart.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
        endSec = wmEnd.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
        normX = wmNormX,
        normY = wmNormY
    )
    val rmConfig = RemoveWatermarkConfig(
        x = rmX.toIntOrNull() ?: 0,
        y = rmY.toIntOrNull() ?: 0,
        width = rmW.toIntOrNull() ?: 2,
        height = rmH.toIntOrNull() ?: 2,
        mode = removeMode,
        intensity = rmIntensity.toIntOrNull() ?: 10
    )
    val wmArgs = when {
        media == null -> emptyList()
        adding -> Commands.watermarkDynamic(
            media!!.path, outWatermark, wmConfig, wmMotion, media!!.videoKbps
        )
        else -> Commands.removeWatermark(
            media!!.path, outWatermark, rmConfig, media!!.videoKbps,
            media!!.video?.width ?: 0, media!!.video?.height ?: 0
        )
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(
            L10n.t("字幕和水印"),
            L10n.t("提取 / 嵌入 / 烧录 · 文字与图片水印"),
            onBack = onBack
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Label(L10n.t("输入文件"))
            CurrentMediaCard(onPick = pick)

            if (media?.hasVideo == true) {
                val videoW = media!!.video?.width ?: 0
                val videoH = media!!.video?.height ?: 0
                VideoPreview(
                    path = media!!.path,
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                    onSizeChanged = { previewSize = it },
                    overlayAboveControls = true
                ) {
                    if (previewSize.width > 0 && videoW > 0 && videoH > 0) {
                        val rect = fitRect(videoW, videoH, previewSize.width, previewSize.height)
                        if (adding) {
                            if (wmAsset.isNotBlank()) {
                                AddWatermarkPreview(
                                    rect = rect,
                                    videoWidth = videoW,
                                    videoHeight = videoH,
                                    isText = wmKind == "text",
                                    text = wmText,
                                    // 直接用实际色值，自定义/非色板颜色在预览里也能即时生效
                                    textColor = Color(wmColorArgb),
                                    fontSizePx = wmFont.toIntOrNull()?.coerceIn(16, 240) ?: 56,
                                    imagePath = wmImage,
                                    position = wmPosition,
                                    marginPx = wmMargin.toIntOrNull() ?: 24,
                                    normX = wmNormX,
                                    normY = wmNormY,
                                    scale = wmScale,
                                    opacity = wmOpacity,
                                    onMove = { nx, ny ->
                                        wmNormX = nx
                                        wmNormY = ny
                                        wmPosition = "custom"
                                    },
                                    onScale = { wmScale = it.coerceIn(0.2f, 3f) }
                                )
                            }
                        } else if (removeMode == RemoveMode.INPAINT) {
                            // 智能填补复用【画面裁剪】的拖动选框：四角缩放 + 整体平移 + 坐标显示
                            val rx = (rmX.toIntOrNull() ?: 0).toFloat()
                            val ry = (rmY.toIntOrNull() ?: 0).toFloat()
                            val rw = (rmW.toIntOrNull() ?: 2).toFloat().coerceAtLeast(2f)
                            val rh = (rmH.toIntOrNull() ?: 2).toFloat().coerceAtLeast(2f)
                            DraggableSelectionBox(
                                rect = rect,
                                left = (rx / videoW.toFloat()).coerceIn(0f, 1f),
                                top = (ry / videoH.toFloat()).coerceIn(0f, 1f),
                                right = ((rx + rw) / videoW.toFloat()).coerceIn(0f, 1f),
                                bottom = ((ry + rh) / videoH.toFloat()).coerceIn(0f, 1f),
                                label = "${rw.toInt()} × ${rh.toInt()}  @ (${rx.toInt()}, ${ry.toInt()})",
                                onChange = { nl, nt, nr, nb ->
                                    rmX = (nl * videoW).toInt().coerceAtLeast(0).toString()
                                    rmY = (nt * videoH).toInt().coerceAtLeast(0).toString()
                                    rmW = ((nr - nl) * videoW).toInt().coerceAtLeast(2).toString()
                                    rmH = ((nb - nt) * videoH).toInt().coerceAtLeast(2).toString()
                                }
                            )
                        } else {
                            RemoveRegionPreview(
                                rect = rect,
                                videoWidth = videoW,
                                videoHeight = videoH,
                                x = rmX.toIntOrNull() ?: 0,
                                y = rmY.toIntOrNull() ?: 0,
                                width = rmW.toIntOrNull() ?: 2,
                                height = rmH.toIntOrNull() ?: 2,
                                onChange = { nx, ny, nw, nh ->
                                    rmX = nx.toString()
                                    rmY = ny.toString()
                                    rmW = nw.toString()
                                    rmH = nh.toString()
                                }
                            )
                        }
                    }
                }
            }

            SectionCard(L10n.t("字幕"), icon = Icons.Default.AutoAwesome) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SubtitleAction.entries.forEach { item ->
                        FilterChip(
                            selected = subAction == item,
                            onClick = { subAction = item },
                            label = { Text(item.label, maxLines = 1) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (subAction != SubtitleAction.EXTRACT) {
                    OutlinedButton(
                        onClick = { subtitlePicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            subName.ifBlank { L10n.t("选择字幕文件（SRT / ASS / SSA / VTT）") },
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (subAction == SubtitleAction.EMBED) {
                    LabeledDropdown(
                        label = L10n.t("输出容器"),
                        options = listOf("mp4" to "MP4 / mov_text", "mkv" to "MKV / srt"),
                        selected = container,
                        onSelect = { container = it }
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    when (subAction) {
                        SubtitleAction.EXTRACT -> L10n.t("把视频里已有的内封字幕导出为 SRT 文本；图形字幕（PGS / DVB）无法转成文字。")
                        SubtitleAction.EMBED -> L10n.t("把字幕作为独立软字幕轨封装进视频，播放器可开关，画面本身不改动，速度最快。")
                        SubtitleAction.BURN -> L10n.t("用原生 drawtext 逐条把字幕烧进画面，任何播放器都能看到，但必须重新编码视频。")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                CommandPreview(Commands.preview(subArgs))
                Spacer(Modifier.height(10.dp))
                ActionButton(
                    text = subAction.label,
                    enabled = media != null && subArgs.isNotEmpty(),
                    onClick = {
                        FFmpegEngine.submit(subAction.label, subArgs, subtitleOutput, media?.durationMs ?: 0L)
                        if (subAction == SubtitleAction.EXTRACT) {
                            outSubtitle = nextOutput("srt", "subtitle")
                        } else {
                            outEmbed = nextOutput(container, "subtitle")
                        }
                        onSubmitted()
                    }
                )
            }

            SectionCard(L10n.t("水印"), icon = Icons.Default.Image) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = adding,
                        onClick = { adding = true },
                        label = { Text(L10n.t("添加水印")) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = !adding,
                        onClick = { adding = false },
                        label = { Text(L10n.t("去水印")) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                if (adding) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = wmKind == "text",
                            onClick = { wmKind = "text" },
                            label = { Text(L10n.t("文字")) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = wmKind == "image",
                            onClick = { wmKind = "image" },
                            label = { Text(L10n.t("图片")) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    if (wmKind == "text") {
                        OutlinedTextField(
                            value = wmText,
                            onValueChange = { wmText = it },
                            label = { Text(L10n.t("水印文字")) },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        NumberField(
                            label = L10n.t("字号（px）"),
                            value = wmFont,
                            onValue = { wmFont = it },
                            suffix = "px",
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Label(L10n.t("字体颜色"))
                        Spacer(Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            textPalette().forEach { (color, _) ->
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (color.toArgb() == wmColorArgb) 3.dp else 1.dp,
                                            color = if (color.toArgb() == wmColorArgb) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            },
                                            shape = CircleShape
                                        )
                                        .clickable { wmColorArgb = color.toArgb() }
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            textPalette().firstOrNull { it.first.toArgb() == wmColorArgb }?.second
                                ?: L10n.t("自定义"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        OutlinedButton(
                            onClick = { imagePicker.launch(arrayOf("image/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                wmImageName.ifBlank { L10n.t("选择水印图片（PNG / JPG / WebP）") },
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LabeledDropdown(
                        label = L10n.t("位置"),
                        options = listOf(
                            "tl" to L10n.t("左上"), "tc" to L10n.t("上中"), "tr" to L10n.t("右上"),
                            "ml" to L10n.t("左中"), "mc" to L10n.t("居中"), "mr" to L10n.t("右中"),
                            "bl" to L10n.t("左下"), "bc" to L10n.t("下中"), "br" to L10n.t("右下"),
                            "custom" to L10n.t("自定义（拖动预览）")
                        ),
                        selected = wmPosition,
                        onSelect = { value ->
                            wmPosition = value
                            if (value == "custom") {
                                if (wmNormX < 0f) {
                                    wmNormX = 0.5f
                                    wmNormY = 0.5f
                                }
                            } else {
                                wmNormX = -1f
                                wmNormY = -1f
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    NumberField(
                        label = L10n.t("边距（px）"),
                        value = wmMargin,
                        onValue = { wmMargin = it },
                        suffix = "px",
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Label(L10n.t("显示时间段（秒）"))
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(
                            L10n.t("起始"), wmStart, { wmStart = it },
                            suffix = "s", modifier = Modifier.weight(1f)
                        )
                        NumberField(
                            L10n.t("结束"), wmEnd, { wmEnd = it },
                            suffix = "s", modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        L10n.t("都填 0 表示全程显示；结束填 0 表示一直显示到视频结尾。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Label(L10n.t("透明度") + " ${(wmOpacity * 100).toInt()}%")
                    Slider(
                        value = wmOpacity,
                        onValueChange = { wmOpacity = it },
                        valueRange = 0.1f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Label(
                        L10n.t("缩放比例") + " " +
                            String.format(Locale.US, "%.2f", wmScale) + "x"
                    )
                    Slider(
                        value = wmScale,
                        onValueChange = { wmScale = it },
                        valueRange = 0.2f..3f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        L10n.t("在上方预览里拖动水印可调整位置，双指捏合或拖动滑条可调整大小。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Label(L10n.t("动态效果（防盗录）"))
                    Spacer(Modifier.height(6.dp))
                    WatermarkMotion.entries.chunked(2).forEach { group ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            group.forEach { item ->
                                FilterChip(
                                    selected = wmMotion == item,
                                    onClick = { wmMotion = item },
                                    label = { Text(item.label, maxLines = 1) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (group.size == 1) Spacer(Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(
                        L10n.t("动态水印的位置 / 可见性会随时间变化，便于追溯录屏来源；文字由系统字体渲染，中文不会变成方框。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RemoveMode.entries.forEach { item ->
                            FilterChip(
                                selected = removeMode == item,
                                onClick = { removeMode = item },
                                label = { Text(item.label, maxLines = 1) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Label(L10n.t("自定义遮挡区域（视频像素坐标）"))
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(L10n.t("X"), rmX, { rmX = it }, modifier = Modifier.weight(1f))
                        NumberField(L10n.t("Y"), rmY, { rmY = it }, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(L10n.t("宽"), rmW, { rmW = it }, modifier = Modifier.weight(1f))
                        NumberField(L10n.t("高"), rmH, { rmH = it }, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        L10n.t("在上方预览里拖动红色选框可移动区域，拖动四角白点可调整大小。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (removeMode == RemoveMode.BLUR) {
                        Spacer(Modifier.height(6.dp))
                        NumberField(
                            label = L10n.t("模糊半径"),
                            value = rmIntensity,
                            onValue = { rmIntensity = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when (removeMode) {
                            RemoveMode.BLUR -> L10n.t("对选定矩形做 boxblur 模糊后贴回原位置，适合半透明台标。")
                            RemoveMode.MOSAIC -> L10n.t("先缩小再放大选中区域形成马赛克，遮挡最彻底。")
                            RemoveMode.INPAINT -> L10n.t("delogo 根据区域四周像素插值填补，适合小面积静态台标。")
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        L10n.t("三种方式都只处理指定矩形，其余画面保持原样；区域须落在视频宽高之内。"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(10.dp))
                CommandPreview(Commands.preview(wmArgs))
                Spacer(Modifier.height(10.dp))
                ActionButton(
                    text = if (adding) L10n.t("添加水印") else L10n.t("去除水印"),
                    enabled = media?.hasVideo == true && wmArgs.isNotEmpty() &&
                        (!adding || wmAsset.isNotBlank()),
                    onClick = {
                        FFmpegEngine.submit(
                            if (adding) L10n.t("添加水印") else L10n.t("去水印"),
                            wmArgs,
                            outWatermark,
                            media?.durationMs ?: 0L
                        )
                        outWatermark = nextOutput("mp4", "watermark")
                        onSubmitted()
                    }
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
