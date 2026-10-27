package com.example.toolkit.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import com.example.toolkit.ui.design.liquidGlass
import com.example.toolkit.ui.model.ToolItem
import com.example.toolkit.ui.theme.Ink
import com.example.toolkit.ui.theme.Ink3

private data class PrivacySection(
    val title: String,
    val lines: List<String>,
)

private val privacySections = listOf(
    PrivacySection(
        title = "数据收集",
        lines = listOf(
            "本应用不收集、不上传、不出售任何文件内容或个人信息。",
            "应用没有申请网络权限，所有工具都在本机离线运行。",
        ),
    ),
    PrivacySection(
        title = "文件访问",
        lines = listOf(
            "只读取你在系统文件选择器、相册或分享面板中主动选择的文件。",
            "转换结果通过系统保存面板、相册或你指定的文件夹写入。",
            "应用不会扫描、遍历或后台上传你的存储空间。",
        ),
    ),
    PrivacySection(
        title = "本地数据",
        lines = listOf(
            "最近转换：最多保留 6 条结果，总计约 96MB，可在工具页清空。",
            "转换缓存：处理过程中的临时文件写入应用缓存，完成后删除。",
            "登录昵称与收藏：登录演示昵称保存在本机 SharedPreferences；收藏仅在本次运行内有效。",
            "卸载应用或在系统设置中清除数据会删除上述全部本地记录。",
        ),
    ),
    PrivacySection(
        title = "第三方组件",
        lines = listOf(
            "FFmpegKit、PDFBox、libjxl、LibRaw、Commons Compress、zip4j、junrar 等均在本地运行。",
            "完整组件与许可证清单见「分类 → 其他 → 开源许可」。",
        ),
    ),
)

private val changelog = listOf(
    "文件格式转换：图片 / PDF / Office / EPUB / 文本 / 数据 / 字幕 / 音视频 / 压缩包",
    "JPEG XL 与 JPEG 2000 解码、加密 ZIP、分卷 ZIP、选择性解压与文件预览",
    "RAW 解码（DNG / CR2 / NEF / ARW / RAF / RW2 等，LibRaw）",
    "PDF 合并、拆分、压缩、旋转、页面排序、插入、提取与删除",
    "批量转换、系统分享入口、最近转换与结果分享",
    "发布配置：ABI 拆分、R8 保留规则、开源许可页与隐私政策页",
)

@Suppress("DEPRECATION")
@Composable
internal fun PrivacyTool(
    tool: ToolItem,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = PackageInfoCompat.getLongVersionCode(info)
            "${info.versionName ?: "1.0"} ($code)"
        }.getOrDefault("1.0")
    }

    Column(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(cornerRadius = 24.dp)
                .padding(18.dp),
        ) {
            Text(
                text = "隐私政策",
                color = Ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "河马工具箱 · 版本 $version",
                color = Ink3,
                fontSize = 11.5.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "本应用是一个离线工具集：不联网、不收集数据，文件只在你选择和保存的位置之间流转。",
                color = Ink3,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }

        Spacer(Modifier.height(18.dp))
        privacySections.forEach { section ->
            PrivacySectionCard(section)
            Spacer(Modifier.height(9.dp))
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "更新日志 · v1.0",
            color = Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.64f))
                .border(1.dp, Color.White.copy(alpha = 0.92f), RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            changelog.forEach { item ->
                Text(
                    text = "· $item",
                    color = Ink3,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(5.dp))
            }
            Text(
                text = "生效日期：2026-10-28",
                color = Ink3,
                fontSize = 10.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PrivacySectionCard(section: PrivacySection) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.64f))
            .border(1.dp, Color.White.copy(alpha = 0.92f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = section.title,
            color = Ink,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        section.lines.forEach { line ->
            Text(
                text = "· $line",
                color = Ink3,
                fontSize = 11.sp,
                lineHeight = 17.sp,
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}
