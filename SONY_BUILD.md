# 索尼 A80EK / Android 12 自用测试版

本版本基于 F-e-n-y-x/airplay-dlna-receiver，保留其 Android TV 界面、
UxPlay AirPlay 接收核心与 DLNA MediaRenderer / ExoPlayer 播放器。

上游：https://github.com/F-e-n-y-x/airplay-dlna-receiver

上游提交：`c045344146c21c8834b0c02e76762d0db5dba584`（来自下载 ZIP 的 GitHub 提交标识）。

原始源码 ZIP SHA-256：
`B29BC419425E9D73B8A4649BEE82067F360885141D051ADAB7A6EF03D602050B`。

本地修改日期：2026-10-21 至 2026-10-22。

## 修改内容

- 增加全部 128 个字符串资源的简体中文翻译，跟随电视系统语言。
- 独立安装包 ID：`local.sonycast.receiver`，避免覆盖上游应用。
- 默认发现名称：`Sony A80EK Cast`，可在设置修改。
- 初始镜像分辨率：1920×1080，最高 30 fps，可在设置调整。
- 默认 AirPlay 端口改为 7100，通过 mDNS 广播该端口，减少与内置接收服务的冲突。
- APK 同时携带 armeabi-v7a 与 arm64-v8a 原生库，不假定电视系统是 64 位。
- 增加 Media3 HLS 播放模块，用于 DLNA 视频链接播放；实际格式兼容性需测试。
- 为 DLNA 播放器补充 Media3 UnstableApi 显式启用声明，修复原项目的 Lint 错误。
- 使用 NDK 28.2.13676358 构建；保留上游的 Android 9 最低版本和 Android 15 目标版本。
- 修复上游 OpenSSL 下载地址返回登录网页的问题，改为随源码附带 KDAB 的固定版本
  ARM 静态库与头文件。其版本为 OpenSSL 3.1.8，仅用于此次自用测试构建；
  正式发布前需升级到仍维护的版本。来源、校验值和头文件的架构修正详见
  `app/src/main/cpp/third_party/android-openssl/PROVENANCE.md`。

## Windows 构建

需要 JDK 17 或 21、Android SDK platform 35、Build Tools 35.0.0、
NDK 28.2.13676358、CMake 3.22.1。Gradle wrapper 指定 8.11.1。

解压到没有空格的路径，建立不随源码分发的 `local.properties`：

```properties
sdk.dir=C:/Android/Sdk
```

执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

APK：`app/build/outputs/apk/debug/app-debug.apk`。

第一次构建需要网络访问 Google Maven、Maven Central 和 Gradle。
OpenSSL ARM 静态库和对应头文件已附带，无需访问上游失效的预编译库站点。
如使用代理，请在本机 Gradle 配置里指定代理，不要将个人代理配置或凭据提交到源码。

## 开源来源

本应用按上游 GPL-3.0 许可提供，完整许可见 LICENSE。源码中保留 UxPlay、
ALAC、libplist、openssl-cmake、llhttp、playfair 的版权与许可文件。
上游 AirPlay 实现派生自 https://github.com/jqssun/android-airplay-server，
协议核心来自 https://github.com/FDH2/UxPlay。
AndroidX / Media3、Compose、Hilt 等依赖由 Gradle 获取。

本版本是基于开源代码制作的自用测试构建，不是 Sony 或 Apple 官方产品。
已通过 ADB 在用户的索尼 A80EK / Android 12（32 位 ARM）上测试 DLNA；
具体结果与尚未完成的项目见交付的真机验证报告，不能据部分测试推断全部协议兼容。

## 真机修复：1.0.0-sony-test4 / versionCode 25

- 电视上 7100 端口已被其他服务占用。首选端口不可用时，原生接收端支持使用 0
  请求系统分配端口，并将实际端口通过 mDNS 对外广播。
- DLNA 播放器先建立内容视图，再获取 InsetsController 进入全屏，修复索尼
  Android 12 的 DecorView 空指针崩溃。同类镜像接收页面也调整了调用顺序。
- DLNA Play 检查新媒体 URI，与当前媒体不同时重新加载，修复换片后继续播放旧媒体。
- 真机回归包含：局域网 SSDP 发现、设备描述、视频硬解、暂停、恢复、跳转、
  视频切换到 MP3、音频播放进度，以及 AirPlay mDNS 和 RTSP OPTIONS / info。
- 用户使用 iPhone 17 / iOS 27（用户提供的型号与系统版本）连接 AirPlay，
  电视画面已核对，用户确认画面与声音正常。

## AirPlay 视频回调补充：test5

补齐 AirPlay 视频能力广播、URL/HLS 播放回调、Media3 视频页面及播放状态反馈。
回移的上游补丁、提交与修改说明见 `AIRPLAY_VIDEO_BACKPORT.md`。
标准 AirPlay URL 请求在真机通过播放、暂停、恢复、拖动与停止测试；这与控制中心
屏幕镜像是不同入口，也不能据此推断特定视频 App 的兼容性。

## DLNA 中文请求修复：1.0.0-sony-test6 / versionCode 27

原 HTTP 解析器用字符读取器按 Content-Length 读取正文，而该长度以字节计。
中文 SOAP 元数据会使其等待额外字符，导致哔哩哔哩直接投屏请求超时。
新增 `DlnaHttpRequestReader`，按字节读取准确长度后再做 UTF-8 解码；同时限制
请求头与正文大小，拒绝截断正文。单元测试覆盖中文与 emoji、分段网络读取、
截断和超大正文，4 项全部通过。

真机修复前中文请求超时，修复后立即返回 HTTP 200。用户从哔哩哔哩投屏页直接
选择当前名称 Sony A80EK2，重试原视频后确认恢复；接收端状态为 PLAYING，
时长 3:27，进度 0:53。构建、单元测试及 Lint 通过，Lint 为 0 错误、59 警告、
1 条信息。交付 APK 通过 v2 签名校验。本包使用 debug 签名。

## DLNA 开头重复播放修复：1.0.0-sony-test9 / versionCode 30

真机日志确认，哔哩哔哩会等接收端开始播放，再发送初始 Seek；实际观察到
Seek(0) 在开播约 2 秒后到达。仅延后开播不能避免重播，因为发送端会跟着等待。

当前方案不再增加启动等待。每次加载新媒体后，仅将满足以下全部条件的首次
Seek(0) 当作重复初始化：播放器正在播放、当前进度不超过 3 秒、距首次进入
播放状态不超过 5 秒。其余 Seek 均执行，包括非零续播、暂停时归零、后续手动
归零，以及超过窗口后的首次归零。这是一项兼容性判断；开播窗口内首次手动
发出的同样归零指令无法与初始化指令区分，也会被忽略一次。

同 URI 的重复启动 Intent 不再重载媒体；重复 Play 主动同步真实播放器状态，
避免没有状态回调时一直报告 TRANSITIONING。换片时清空旧的进度和时长。

新增 6 项初始 Seek 过滤测试，加上 4 项 UTF-8 HTTP 读取测试，共 10 项。
真机控制回归覆盖首次归零、后续手动归零、重复 Play、暂停跳转及非零续播。
具体用户复测结论见交付的真机验证报告。

## TV 界面重设计：1.0.0-sony-test10-tv / versionCode 31

参考 Google Design for TV、Focus system、Layouts、Typography 与 Buttons 指南，
TV 使用独立的侧栏、宽屏首页和分组设置。按钮焦点使用亮底深字、描边及缩放，
当前设置与焦点分开呈现。DLNA / AirPlay URL 视频共用大尺寸遥控器播放器控件，
支持方向键导航、10 秒跳转、进度条、专用播放键及分层返回。

已在 A80EK 上通过标准遥控器键值检查导航、弹窗、列表及实际播放。10 项原有
协议单元测试和 Lint 通过，DLNA 与标准 AirPlay URL 播放控制均回归通过。
完整设计依据、按键表、测试范围及源码位置见 `TV_DESIGN.md`。

## 紧凑播放器与直接跳转：1.0.0-sony-test11-player / versionCode 32

2026-10-22，根据用户实际使用反馈调整 DLNA / AirPlay URL 共用播放器：
左右方向键在任何焦点、控件显示或隐藏时都直接跳转 10 秒；重复事件限速处理。
隐藏时只短暂提示目标时间，不展开按钮；暂停时跳转不自动恢复播放。
中央改为 48 / 64 / 48 dp 的三个小图标，退出移到右上角，底部仅保留进度与时间。
通过 Player.videoSize 显示当前视频画面尺寸，尺寸未知时给出明确等待文字。
隐藏时确定键直接播放/暂停，上下键仅显示控件。文字未变化时不重复更新控件。

已在 A80EK / Android 12 真机验证隐藏/显示时的直接跳转、长按、暂停状态、
进度条、确定键和实际尺寸显示，细节与按键表见 TV_DESIGN.md。

## 长按跳转加速：1.0.0-sony-test12-seek / versionCode 33

按住左右方向键或专用快进快退键，前 2 秒保持原有 1× 跳转，满 2、3、4、5 秒
分别进入 2×、3×、4×、5×，之后保持 5×。倍率对应每个已接收重复事件的跳转距离，
即 10、20、30、40、50 秒；事件间隔至少 250 ms。普通播放速率不变。
按键事件的单调时间用于计时，松开、换方向、失焦、换片及退出均重置。
长按提示方向、档位与目标时间，松开后不残留倍率；暂停时跳转保持暂停。

新增 8 项 SeekAcceleration 单元测试，连同协议测试共 18 项通过。
独立开发测试 APK 的 SeekHoldInstrumentation 在 A80EK 上注入持续方向键，
两个方向均核对实际进度差、1–5× 提示、松开复位及暂停状态。测试 APK 已卸载，
不属于交付的主 APK。实际 MP4 的直接跳转与播放控制另行回归通过。

测试源码位于 app/src/androidTest；默认构建命令不会安装或运行该测试。
开发测试使用本机局域网样本服务，运行时通过 mediaUrl 参数明确提供自己的测试样本 URL，
并准备足够长且可跳转的媒体；构建任务为 :app:assembleDebugAndroidTest。

## 测试范围

应在真机确认：设备发现、AirPlay 镜像画面与声音、DLNA MP4 播放、
暂停/恢复/拖动、切换视频、遥控器操作、后台接收及电视待机后恢复。
DRM 内容不在支持范围内；4K/HDR、照片投屏和各视频平台兼容性不作保证。
编译与检查结果以同目录交付的安装说明为准。
