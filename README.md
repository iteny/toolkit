# 河马工具箱 · Jetpack Compose

用 Jetpack Compose 复刻 `app.html` 的「河马工具箱」界面与交互。

## 已实现

- 流动彩色背景、液态玻璃面板、彩虹描边、缓慢流光与柔和阴影
- 首页分类宫格、收藏页、独家页、左导航 + 右列表的分类页
- 底部悬浮玻璃 Tab、按下缩放、选中胶囊过渡
- 搜索浮层：聚焦展开、实时过滤、清除、结果直达详情
- 工具详情页从右侧滑入，标题栏右侧书签收藏带弹跳动画
- **图片马赛克工具**：从系统相册选图、实时调整马赛克颗粒、查看原图、保存到相册
- **图片裁剪工具**：自由裁剪 / 1:1 / 4:3 / 16:9 比例，拖动四角缩放、框内移动，保存到相册
- **图片转 Base64 工具**：原图 / 压缩模式、可切换 data URI 前缀、复制 Base64、保存 TXT
- **GIF 帧修改工具**：查看帧缩略图、调整单帧/全部时长、左移/右移、复制、删除，保存 GIF
- **PNG 背景色工具**：为透明区域填充预设色或自定义 HEX 色，棋盘格查看原图，导出不透明 PNG
- **GIF 帧拆分工具**：解析 GIF 帧并预览，支持保存当前帧或全部帧为独立 PNG（旧版本导出 ZIP）
- **图片反相/反色工具**：RGB 反相 / 灰度反相，保留 Alpha 通道，导出 PNG
- **GIF 压缩工具**：按比例抽帧、去除连续重复帧，实时预览压缩后动画与体积，保存 GIF
- **文件格式转换工具（独家页）**：支持多选批量转换、系统分享入口、最近转换与结果分享，按来源格式只展示所有文件共同可用的目标，全程本地离线。支持图片互转 JPG / PNG / WEBP / BMP / TIFF / ICO / JXL、SVG 转图片、TIFF / PSD / TGA / ICO / QOI / PNM / HDR / DDS / PCX / JXL / JP2 转常用图片、RAW（DNG / CR2 / CR3 / NEF / ARW / RAF / RW2 等，LibRaw）转常用图片或 PDF、图片转 PDF、PDF 提取文本 / 逐页转 PNG / JPG / 合并 / 拆分 / 压缩 / 旋转 / 页面排序 / 插入 PDF / 提取页 / 删除页；TXT / Markdown / HTML / DOCX / ODT / RTF / HWPX / PPTX / ODP / EPUB 转 PDF 或文本；XLSX / ODS 转 CSV / TSV / JSON；JSON / JSONL / XML / YAML / TOML / CSV / TSV / XLSX 互转；SRT / VTT / ASS / LRC 字幕互转；ZIP / 7Z / RAR / TAR 等压缩包解压、选择性解压、重打包、加密 ZIP 与分卷 ZIP
- **音视频转换（FFmpeg）**：MP3 / M4A / AAC / WAV / FLAC / OGG / OPUS 互转；MP4 / MOV / MKV / WEBM / AVI 互转；视频提取音频；视频转 GIF
- **压缩包工具**：ZIP / 7Z / RAR / TAR / TAR.GZ / TAR.BZ2 / TAR.XZ / GZ / BZ2 / XZ 解压到文件夹；选择性解压；重新打包为 ZIP / 7Z / TAR / TAR.GZ；加密 ZIP 与分卷 ZIP
- **开源许可页**：分类 → 其他 → 开源许可，列出全部第三方组件与许可证
- **隐私政策页**：分类 → 其他 → 隐私政策，展示版本号、数据收集说明、本地存储与更新日志
- 登录底部抽屉：微信一键登录演示、加载态、客服二维码
- 登录头像、用户菜单、退出登录；登录态通过 `SharedPreferences` 持久化
- 页面切换淡入 / 上移 / 轻微缩放动画，滚动内容可从顶部玻璃栏和底部 Tab 下穿过
- Android 12+ 使用真实 RenderEffect 对遮罩后的内容做背景模糊

## 构建

首次构建需要 NDK `27.2.12479018` 与 CMake `3.22.1`（用于编解码 RAW 的 LibRaw 原生库），Android Studio 会自动安装；命令行可用 `sdkmanager "ndk;27.2.12479018" "cmake;3.22.1"` 安装。

```bash
./gradlew :app:assembleDebug
```

Debug APK 输出（启用 ABI 拆分）：

- `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`（约 60 MB）
- `app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk`（约 29 MB）
- `app/build/outputs/apk/debug/app-x86_64-debug.apk`（约 67 MB）
- `app/build/outputs/apk/debug/app-universal-debug.apk`（全 ABI，约 118 MB）

Release 构建（R8 + 资源压缩 + ABI 拆分）：

```bash
./gradlew :app:assembleRelease
```

- `app-arm64-v8a-release.apk` 约 47 MB，`app-armeabi-v7a-release.apk` 约 16 MB，`app-x86_64-release.apk` 约 55 MB，universal 约 102 MB
- 未提供 `keystore.properties` 时，release 会用 debug 证书签名，仅用于本地安装验证
- 正式发布请在仓库根目录创建 `keystore.properties`（已被 `.gitignore` 忽略）：

```properties
storeFile=release.jks
storePassword=******
keyAlias=******
keyPassword=******
```

- R8 保留规则在 `app/proguard-rules.pro`，覆盖 FFmpegKit、libjxl、PDFBox、junrar、zip4j 等 JNI / 反射较多的库

Play 上架用 AAB（构建时会自动关闭 ABI 拆分，包内含全部 ABI）：

```bash
./gradlew :app:bundleRelease
```

- 输出：`app/build/outputs/bundle/release/app-release.aab`（约 56 MB）
- 同样使用 `keystore.properties` 中的签名；未配置时回退为 debug 证书

## 上架清单

- [ ] 把 `applicationId` / `namespace` 从 `com.example.toolkit` 换成正式包名
- [ ] 配置正式 `keystore.properties`，确认 `.jks` 没有提交到仓库
- [ ] 每次发布递增 `versionCode`，同步更新 `versionName` 与隐私政策页的版本号
- [ ] 用 `:app:bundleRelease` 生成 AAB，并上传到 Play Console
- [ ] 隐私政策：可直接使用应用内「分类 → 其他 → 隐私政策」的文本，或在商店填写托管链接
- [ ] Data safety：声明不收集、不共享数据，应用未申请网络与存储权限
- [ ] FFmpeg LGPL：保留许可证并提供源码获取方式；LibRaw（LGPL-2.1 / CDDL-1.0）同样保留许可证与源码；junrar 保留 UnRAR 声明
- [ ] 复核第三方许可证页与目标商店的 AI / 文件处理政策

## 说明

- 除「图片马赛克」「图片裁剪」「图片转Base64」「GIF帧修改」「PNG背景色」「GIF帧拆分」「图片反相/反色」「GIF压缩」「文件格式转换」「开源许可」「隐私政策」外，其余工具详情页内容仍为占位。
- 图片马赛克当前对整张图片生效；选择图片使用系统照片选择器，无需存储权限。
- 图片裁剪支持自由比例和常用固定比例，支持拖动四角与移动裁剪框；裁剪结果按解码后的图片分辨率保存（最长边最高 2048px）。
- 图片转 Base64 支持原图编码与压缩编码（最长边 1400px / JPEG 85%），可切换 data URI 前缀，支持复制和保存 TXT。
- GIF 帧修改采用块级复用，直接调整原始帧顺序/数量/GCE 时长，不重新量化，保留原始帧画质；支持 30MB 以内 GIF。
- PNG 背景色支持预设色板和自定义 HEX，按原图分辨率合成背景并导出不透明 PNG；可切换棋盘格查看原图透明区域。
- GIF 帧拆分逐帧渲染 PNG，支持保存当前帧或全部帧；Android 10+ 保存到相册，Android 9 及以下导出 ZIP，30MB 以内 GIF。
- 图片反相/反色支持 RGB 反相与灰度反相，保留透明通道，按原图分辨率导出 PNG。
- GIF 压缩支持 1/2、1/3、1/4 抽帧与连续重复帧合并，首帧始终保留、总时长不变；直接复用原始帧数据，不重新编码画面，压缩后实时预览并显示体积变化。
- 文件格式转换只展示当前文件可用的目标格式；图片输出最长边 4096px，PDF 最多逐页导出 24 页（多页在 Android 10+ 直接存入相册，其余情况打包 ZIP）。
- 文件格式转换的 Office 支持为纯本地 XML 提取：DOCX / ODT / RTF / HWPX / PPTX / ODP 可导出 TXT / HTML / PDF，XLSX / ODS 可导出 CSV / TSV / JSON；旧版 DOC / XLS / PPT、HWP、MOBI 等仍需 LibreOffice / rhwp / calibre 引擎。Office / EPUB / HTML 转 PDF 只保留文字并按 A4 重新排版，不保留原始排版、图片和表格样式。
- 专业图片格式使用自研解码器（TIFF / PSD / TGA / ICO / QOI / PNM / HDR / DDS / PCX），不引入 GPL 版 ImageMagick；TIFF 支持未压缩 / LZW / PackBits / Deflate，PSD 读取合并图层并支持 Raw / RLE。JPEG XL 由 libjxl 解码 / 编码；RAW 由 LibRaw 0.21.3 通过 JNI 解码（LGPL-2.1 / CDDL-1.0），按相机白平衡和 sRGB 输出，超过 1600 万像素时使用半尺寸解码；JPEG 2000（JP2 / J2K）通过 FFmpeg 解码为 PNG 后再交给图片管线处理，只支持读取。
- 音视频由 FFmpegKit 在本地转码（LGPL-3.0，16KB page size 构建）：输入上限 512MB，输出上限 256MB；MP4 / MOV / MKV 使用 MPEG-4 Part 2 视频编码（该构建不含 GPL 的 x264），WebM 使用 VP9，长视频需要等待。Release 已按 ABI 拆分，arm64 约 47 MB。
- PDF 文本提取使用 PDFBox-Android（Apache-2.0）；扫描版 PDF 没有文本层时会给出提示，需要 OCR 才能提取。
- 压缩包读取使用 Apache Commons Compress（ZIP / 7Z / TAR / GZ / BZ2 / XZ）和 junrar（RAR，只读，UnRAR license）。解压会跳过 `../` 路径，输入上限 512MB，输出上限 256MB；RAR 只支持读取，不支持写出 RAR。
- 批量转换一次最多 20 个文件，转换结果会逐个文件执行，失败的条目会跳过并汇总提示；多文件可共同输出到同一目标格式时才会显示该目标。
- 最近转换会保留最多 6 条、总计约 96MB 的结果（单次超过 32MB 不保留），通过 FileProvider 支持打开、分享和清空；清空应用数据会一并清除。
- 支持从其他应用通过系统分享（`ACTION_SEND` / `ACTION_SEND_MULTIPLE`）把文件送进工具，自动打开「文件格式转换」并载入；`ACTION_VIEW` 未注册，避免抢占所有文件的默认打开方式。
- PDF 合并要求至少两个 PDF，按选择顺序合并；拆分按页生成独立 PDF，多页结果会打包保存。
- PDF 压缩会重新编码内嵌位图（最长边 2000px、JPEG 质量可调），文字与矢量内容不变；带真实透明通道的图片和超过 4800 万像素的图片保持原样。如果文档没有可压缩图片或压缩后没有变小，会提示保留原文件而不输出伪结果。
- JPEG XL 使用 libjxl 解码与编码；编码为有损、Hare 档位，大图在手机上可能需要等待。JXL 动图只取首帧。
- 加密压缩包：ZIP / 7Z / RAR 都可以输入密码读取；「加密 ZIP」使用 AES-256 重新打包。Commons Compress 不支持写出加密 7Z，RAR 仍然只读，密码错误或丢失无法恢复内容。
- 「选择解压」会先列出压缩包内的文件（最多 1000 个），支持按文件名实时搜索，默认全选，可逐个取消或用全选 / 反选调整，只解压勾选的文件并自动保留目录结构；每行可以提供预览：图片按最长边 900px 解码，文本显示前 4000 字符，单文件超过 8 MB 会跳过预览。条目超过 1000 个时回退为全部解压；加密压缩包需要先输入密码才会读取列表，且选择解压只支持单个压缩包。
- 分卷 ZIP 使用 zip4j 生成 `.z01`、`.z02`… 和 `.zip`，每个分卷 1-2048 MB，默认 100 MB；分卷不加密，保存时请保持文件名不变。多文件结果可在结果卡选择「保存到文件夹」，文件名会按 SAF 规则尽量保持原样。
- PDF 页面管理：旋转支持 90° / 180° / 270° 顺时针；页面排序用缩略图列表的上下箭头调整，位置 1 输出为第一页，可一键重置；「插入 PDF」选择另一个 PDF 后可以点选要插入的页面，并选择插到开头或结尾，未选择页面时插入全部；提取页与删除页直接在页面缩略图上点选（支持全选 / 反选 / 清空）。排序、插入和页面选择对批量文件会把同一设置应用到每个 PDF，其中插入只支持单个基准 PDF。缩略图按需渲染，单页最长边约 220px，超过 40 张会回收旧缓存。压缩、合并、拆分、旋转、排序、插入、提取、删除都由 PDFBox 本地完成。
- 文件选择器与文件夹选择器会记住上一次使用的目录（SAF 持久授权），下次打开直接定位到该目录；解压和「保存到文件夹」共用这份记录。
- 图片类工具在 Android 10+ 保存到 `Pictures/河马工具箱`，Android 9 及以下使用系统文件选择器；文件格式转换的单文件结果统一通过系统保存面板导出，多页 PDF 在 Android 10+ 直接存入相册。
- 文件格式转换新增依赖：AndroidSVG（SVG 渲染）、SnakeYAML（YAML）、tomlj（TOML）、FFmpegKit（音视频，LGPL-3.0）、PDFBox-Android（PDF，Apache-2.0）、Commons Compress / XZ（压缩包，Apache-2.0 / Public Domain）、junrar（RAR，UnRAR license）、jxl-coder / libjxl（JPEG XL，Apache-2.0 / BSD-3-Clause）、LibRaw（RAW，LGPL-2.1 / CDDL-1.0）、zip4j（加密与分卷 ZIP，Apache-2.0）和 DocumentFile。最近转换的打开/分享使用 AndroidX FileProvider。许可证清单已内置在「分类 → 其他 → 开源许可」；正式发布前仍需按 LGPL 要求提供 FFmpeg 与 LibRaw 的源码获取方式，并保留 junrar 的 UnRAR 声明。
- 收藏为会话内状态，与原型的 `favSet` 行为一致；登录用户名会持久化。收藏按钮固定在详情页标题栏右侧，避免遮挡长页面底部的操作按钮。
- 独家页工具也会进入搜索与收藏索引，避免原型中「独家工具收藏后不可见」的问题。
- 「文件格式转换」已从分类 → 转换移动到独家页首位，分类页的转换分组只保留单位换算类工具。
