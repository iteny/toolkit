package com.example.toolkit.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.toolkit.ui.theme.AppleBlue
import com.example.toolkit.ui.theme.Ink
import com.example.toolkit.ui.theme.Ink3

internal fun filterArchiveEntries(
    entries: List<ArchiveEntryInfo>,
    query: String,
): List<ArchiveEntryInfo> {
    val keyword = query.trim()
    if (keyword.isEmpty()) return entries
    return entries.filter { it.name.contains(keyword, ignoreCase = true) }
}

@Composable
internal fun ArchiveEntryPicker(
    entries: List<ArchiveEntryInfo>,
    selected: Set<String>,
    query: String,
    onQueryChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: (List<ArchiveEntryInfo>) -> Unit,
    onInvert: (List<ArchiveEntryInfo>) -> Unit,
    onPreview: (ArchiveEntryInfo) -> Unit,
    previewingName: String?,
    listHeight: Int = 320,
    modifier: Modifier = Modifier,
) {
    val visible = filterArchiveEntries(entries, query)

    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "已选 ${selected.size} / 共 ${entries.size} 个文件",
                color = Ink3,
                fontSize = 11.sp,
            )
            if (visible.size != entries.size) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "筛选 ${visible.size}",
                    color = AppleBlue,
                    fontSize = 10.5.sp,
                )
            }
            Spacer(Modifier.weight(1f))
            EntryAction("全选") { onSelectAll(visible) }
            EntryAction("反选") { onInvert(visible) }
        }
        Spacer(Modifier.height(7.dp))
        ArchiveSearchField(
            value = query,
            onValueChange = onQueryChange,
        )
        Spacer(Modifier.height(7.dp))
        if (visible.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.60f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "没有匹配的文件",
                    color = Ink3,
                    fontSize = 11.sp,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(listHeight.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(visible, key = { it.name }) { entry ->
                    ArchiveEntryRow(
                        entry = entry,
                        checked = entry.name in selected,
                        previewing = previewingName == entry.name,
                        onClick = { onToggle(entry.name) },
                        onPreview = { onPreview(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchiveSearchField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = Ink, fontSize = 12.sp),
        cursorBrush = SolidColor(AppleBlue),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.78f))
                    .border(1.dp, Color.White.copy(alpha = 0.95f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = "搜索文件名",
                            color = Ink3.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                        )
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    Text(
                        text = "清除",
                        color = AppleBlue,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onValueChange("") }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        },
    )
}

@Composable
private fun EntryAction(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = AppleBlue,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun ArchiveEntryRow(
    entry: ArchiveEntryInfo,
    checked: Boolean,
    previewing: Boolean,
    onClick: () -> Unit,
    onPreview: () -> Unit,
) {
    val shape = RoundedCornerShape(11.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.64f))
            .border(
                1.dp,
                if (checked) AppleBlue.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.92f),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (checked) AppleBlue else Color.White.copy(alpha = 0.92f))
                .border(
                    1.dp,
                    if (checked) AppleBlue else Ink3.copy(alpha = 0.35f),
                    RoundedCornerShape(6.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Text(
                    text = "✓",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                color = Ink,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = fileConvertFormatSize(entry.size),
                color = Ink3,
                fontSize = 9.5.sp,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (previewing) "读取中" else "预览",
            color = if (previewing) Ink3 else AppleBlue,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .clickable(enabled = !previewing, onClick = onPreview)
                .padding(horizontal = 5.dp, vertical = 3.dp),
        )
    }
}
