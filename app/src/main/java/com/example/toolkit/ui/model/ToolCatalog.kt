package com.example.toolkit.ui.model

import androidx.compose.ui.graphics.Color

/** One tappable tool in the grid. */
data class ToolItem(
    val name: String,
    val iconKey: String,
    val color: Color,
)

/** A home-page section / category page entry. */
data class ToolCategory(
    val id: String,
    val name: String,
    val color: Color,
    val iconKey: String,
    val tools: List<ToolItem>,
)

enum class AppTab(val label: String, val iconKey: String) {
    Home("首页", "home"),
    Favorites("收藏", "bookmark"),
    Exclusive("独家", "ai"),
    Category("分类", "grid"),
}

/**
 * All content of the HTML prototype, including its icon-matching rules.
 */
object ToolCatalog {

    private data class IconRule(val regex: Regex, val icon: String)

    private val iconRules = listOf(
        IconRule(Regex("二维码|条形码|收款码"), "qr"),
        IconRule(Regex("身份|证件|头像|人物"), "person"),
        IconRule(Regex("马赛克|水印|修复|去痕|消除"), "eraser"),
        IconRule(Regex("取色|吸管|拾色"), "dropper"),
        IconRule(Regex("配色|调色|色板|背景色|颜色|画板|绘图|涂鸦"), "palette"),
        IconRule(Regex("反相|反色|对比"), "contrast"),
        IconRule(Regex("裁剪|裁切|剪切|圆角|缩放"), "crop"),
        IconRule(Regex("压缩"), "compress"),
        IconRule(Regex("加密|解密|密码|密钥"), "lock"),
        IconRule(Regex("Base64|MD5|SHA|RSA|AES|RC4|哈希", RegexOption.IGNORE_CASE), "hash"),
        IconRule(Regex("JSON", RegexOption.IGNORE_CASE), "json"),
        IconRule(Regex("PDF", RegexOption.IGNORE_CASE), "pdf"),
        IconRule(Regex("Excel|CSV|表格|发票", RegexOption.IGNORE_CASE), "table"),
        IconRule(Regex("OCR|识别|扫描", RegexOption.IGNORE_CASE), "ocr"),
        IconRule(Regex("翻译|英文"), "translate"),
        IconRule(Regex("IP|服务器|端口|域名", RegexOption.IGNORE_CASE), "server"),
        IconRule(Regex("转换|互转|转"), "convert"),
        IconRule(Regex("汇率|利率|货币|金额|价格"), "currency"),
        IconRule(Regex("计算|年份|体积|面积|速度|温度|功热|进制|单位"), "calc"),
        IconRule(Regex("世界时间|时间|日期|时钟|时区"), "clock"),
        IconRule(Regex("日历|倒数日"), "calendar"),
        IconRule(Regex("经纬度|坐标|归属地|地图"), "globe"),
        IconRule(Regex("商标|专利|版权"), "shield"),
        IconRule(Regex("邮编|邮政|高校|院校|大学"), "letter"),
        IconRule(Regex("彩票|开奖|排序"), "dice"),
        IconRule(Regex("号码"), "person"),
        IconRule(Regex("查询|检索|搜索"), "catQuery"),
        IconRule(Regex("抽奖|随机|骰|分组|点名"), "dice"),
        IconRule(Regex("计时|秒表"), "timer"),
        IconRule(Regex("GIF|视频|剪辑|字幕|抽帧", RegexOption.IGNORE_CASE), "video"),
        IconRule(Regex("音频|音量|调速|变声|录音"), "audio"),
        IconRule(Regex("图片|图像|照片|拼图|相框|贴纸|图"), "catImage"),
        IconRule(Regex("文本|文字|字数|词频|符号|去重|替换|空行|换行|网址|链接|号码"), "catText"),
        IconRule(Regex("安全|隐私|检测|校验|验证"), "shield"),
        IconRule(Regex("智能|AI|魔法|生成", RegexOption.IGNORE_CASE), "ai"),
        IconRule(Regex("重命名|改名"), "rename"),
        IconRule(Regex("清洗|筛选|过滤"), "filter"),
    )

    fun pickIcon(name: String, fallback: String): String =
        iconRules.firstOrNull { it.regex.containsMatchIn(name) }?.icon ?: fallback

    private fun rgb(r: Int, g: Int, b: Int) = Color(r, g, b)

    private fun category(
        id: String,
        name: String,
        color: Color,
        iconKey: String,
        toolNames: List<String>,
    ): ToolCategory = ToolCategory(
        id = id,
        name = name,
        color = color,
        iconKey = iconKey,
        tools = toolNames.map { ToolItem(it, pickIcon(it, iconKey), color) },
    )

    val categories: List<ToolCategory> = listOf(
        category(
            id = "daily", name = "日常", color = rgb(46, 158, 143), iconKey = "catDaily",
            toolNames = listOf(
                "国企头像制作", "随机抽奖", "画板", "二维码解析",
                "二维码生成", "比赛活动分组", "身份证查询",
            ),
        ),
        category(
            id = "query", name = "查询", color = rgb(59, 125, 216), iconKey = "catQuery",
            toolNames = listOf(
                "商标查询", "经纬度查询", "归属地查询", "彩票开奖查询",
                "高校查询", "全球IP查询", "世界时间", "邮编查询",
                "常用号码查询",
            ),
        ),
        category(
            id = "text", name = "文本", color = rgb(123, 97, 201), iconKey = "catText",
            toolNames = listOf(
                "字数统计", "魔法文案", "链接批量提取", "文本对比",
                "时间日期格式化", "文本转列表", "Unicode互转", "英文文本转换",
                "小说格式转换", "文本去空换行", "文本加密", "数字号码提取",
                "词频统计", "文本提取网址", "文本去重", "中英文符号转换",
                "正则表达式测试", "驼峰命名转换", "文本替换", "IP地址批量提取",
            ),
        ),
        category(
            id = "convert", name = "转换", color = rgb(224, 138, 60), iconKey = "catConvert",
            toolNames = listOf(
                "时间转换", "体积转换", "功热转换", "面积转换",
                "速度转换", "温度转换", "汇率转换",
            ),
        ),
        category(
            id = "calc", name = "计算", color = rgb(62, 143, 168), iconKey = "catCalc",
            toolNames = listOf("日期天地计算", "朝代年份计算", "信用卡利率计算"),
        ),
        category(
            id = "image", name = "图像", color = rgb(217, 85, 127), iconKey = "catImage",
            toolNames = listOf(
                "图片马赛克", "图片转Base64", "图片裁剪", "GIF帧修改",
                "PNG背景色", "GIF帧拆分", "图片反相/反色", "GIF压缩",
                "图片合成GIF", "修改图片尺寸", "图片圆角", "图片水印平铺",
                "颜色预览", "ACG表情包制作", "图片格式转换", "图片拼接",
                "收款码合并", "Base64转图片", "图片压缩", "图片加包浆",
                "条形码生成", "添加水印", "图片取色", "图标制作",
                "图片切割",
            ),
        ),
        category(
            id = "video", name = "视频", color = rgb(194, 94, 94), iconKey = "catVideo",
            toolNames = listOf(
                "视频拼接", "视频提取音频", "视频变速", "视频压缩",
                "视频转GIF", "视频音量修改", "视频裁剪", "视频格式转换",
            ),
        ),
        category(
            id = "audio", name = "音频", color = rgb(160, 106, 62), iconKey = "catAudio",
            toolNames = listOf(
                "音频音量修改", "音频调速", "音频压缩",
                "音频拼接", "音频格式转换", "音频裁剪",
            ),
        ),
        category(
            id = "crypto", name = "加密", color = rgb(108, 123, 209), iconKey = "catCrypto",
            toolNames = listOf(
                "RSA加密", "MD5加密", "Base64编码", "SHA加密",
                "RC4互转", "AES加密", "URL编码",
            ),
        ),
        category(
            id = "more", name = "其他", color = rgb(122, 132, 150), iconKey = "catMore",
            toolNames = listOf(
                "随机数生成", "电报码翻译", "随机密码生成", "开源许可", "隐私政策",
            ),
        ),
    )

    val exclusiveTitle: String = "独家工具集"

    val exclusiveTools: List<ToolItem> = listOf(
        ToolItem("文件格式转换", "convert", rgb(224, 138, 60)),
        ToolItem("批量重命名", "rename", rgb(59, 125, 216)),
        ToolItem("PDF 工具箱", "pdf", rgb(194, 94, 94)),
        ToolItem("音频剪辑", "audio", rgb(76, 143, 203)),
        ToolItem("数据清洗", "filter", rgb(160, 106, 62)),
        ToolItem("表格合并", "table", rgb(75, 158, 106)),
        ToolItem("隐私检测", "shield", rgb(108, 123, 209)),
        ToolItem("自动化脚本", "code", rgb(201, 119, 59)),
        ToolItem("批量压缩", "compress", rgb(46, 158, 143)),
    )

    /** Category tools first, then the exclusive set; names stay unique. */
    val allTools: List<ToolItem> = buildList {
        categories.forEach { addAll(it.tools) }
        addAll(exclusiveTools)
    }.distinctBy { it.name }

    val byName: Map<String, ToolItem> = allTools.associateBy { it.name }

    fun find(name: String?): ToolItem? = name?.let(byName::get)
}
