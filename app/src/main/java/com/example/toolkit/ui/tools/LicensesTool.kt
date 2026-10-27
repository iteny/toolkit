package com.example.toolkit.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toolkit.ui.design.liquidGlass
import com.example.toolkit.ui.model.ToolItem
import com.example.toolkit.ui.theme.Ink
import com.example.toolkit.ui.theme.Ink3

private data class LicenseEntry(
    val name: String,
    val license: String,
    val note: String,
)

private val licenses = listOf(
    LicenseEntry("AndroidX / Jetpack Compose", "Apache-2.0", "界面、生命周期与文件访问"),
    LicenseEntry("Kotlin / kotlinx.coroutines", "Apache-2.0", "语言运行时与协程"),
    LicenseEntry("AndroidSVG", "Apache-2.0", "SVG 渲染"),
    LicenseEntry("SnakeYAML", "Apache-2.0", "YAML 解析"),
    LicenseEntry("tomlj", "Apache-2.0", "TOML 解析"),
    LicenseEntry("FFmpegKit / FFmpeg", "LGPL-3.0", "音视频转码；FFmpeg 以动态库方式使用"),
    LicenseEntry("PDFBox-Android", "Apache-2.0", "PDF 读取、合并、拆分、压缩与页面编辑"),
    LicenseEntry("Apache Commons Compress", "Apache-2.0", "ZIP / 7Z / TAR 读写"),
    LicenseEntry("XZ for Java", "Public Domain", "XZ 压缩"),
    LicenseEntry("junrar", "UnRAR License", "仅用于读取 RAR，不支持写出 RAR"),
    LicenseEntry("jxl-coder / libjxl", "Apache-2.0 / BSD-3-Clause", "JPEG XL 编解码"),
    LicenseEntry("LibRaw", "LGPL-2.1 / CDDL-1.0", "相机 RAW 解码（DNG / CR2 / NEF / ARW 等）"),
    LicenseEntry("zip4j", "Apache-2.0", "加密 ZIP 读写"),
    LicenseEntry("AndroidX DocumentFile", "Apache-2.0", "SAF 目录访问"),
)

@Composable
internal fun LicensesTool(
    tool: ToolItem,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(cornerRadius = 24.dp)
                .padding(18.dp),
        ) {
            Text(
                text = tool.name,
                color = Ink,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "河马工具箱 1.0 · 本地离线工具集",
                color = Ink3,
                fontSize = 11.5.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "全部转换都在本机完成，不上传文件内容，也不收集转换结果。",
                color = Ink3,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = "第三方开源组件",
            color = Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))

        licenses.forEach { entry ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.64f))
                    .border(1.dp, Color.White.copy(alpha = 0.92f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = entry.name,
                        color = Ink,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.height(0.dp))
                    Text(
                        text = entry.license,
                        color = Ink3,
                        fontSize = 10.5.sp,
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = entry.note,
                    color = Ink3,
                    fontSize = 10.5.sp,
                    lineHeight = 15.sp,
                )
            }
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = "各组件许可证全文请以对应项目主页为准。FFmpeg 的 LGPL 条款要求保留其许可证与源码获取方式；junrar 的 UnRAR License 仅允许用于解压 RAR。",
            color = Ink3,
            fontSize = 10.sp,
            lineHeight = 15.sp,
        )
        Spacer(Modifier.height(8.dp))
    }
}
