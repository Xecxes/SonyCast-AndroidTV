# SonyCast Android TV

适用于 Android TV 的 AirPlay + DLNA 投屏接收端，按电视遥控器操作重新设计。
当前版本：**0.1.0**（versionCode 34）。已在 Sony A80EK / Android 12 上验证。

基于 [F-e-n-y-x/airplay-dlna-receiver](https://github.com/F-e-n-y-x/airplay-dlna-receiver)
修改，保留上游提交历史、版权和 GPL-3.0 许可；上游基准提交为
`c045344146c21c8834b0c02e76762d0db5dba584`。
原上游说明保存在 [README.upstream.md](README.upstream.md)。

## 功能

- AirPlay 屏幕镜像、音频及标准 URL/HLS 视频接收。
- DLNA MediaRenderer，通过手机视频 App 或其他 DLNA 控制端播放媒体链接。
- 中文 TV 界面：左侧导航、分组设置、清晰的遥控器焦点、居中播放器小图标。
- 左右键直接快退/快进，控件隐藏时也生效；显示当前视频尺寸。
- 长按加速：满 2 秒进入 2×，3 秒 3×，4 秒 4×，5 秒及以上保持 5×。
- 修复中文 DLNA SOAP 请求超时、部分发送端开播后重复归零及同 URI 重复重载。
- 支持 armeabi-v7a 与 arm64-v8a；最低 Android 9，target SDK 35。

## 使用

在 [Releases](https://github.com/Xecxes/SonyCast-AndroidTV/releases) 下载 APK，
或按下文说明自行构建。v0.1.0 使用 release 构建并沿用此前测试签名，便于覆盖升级；
Android 包 ID 仍为 `local.sonycast.receiver`。

1. 安装自行构建的 APK，在电视上打开“电视投屏”，按提示授予所需权限并启动接收。
2. 手机和电视连接同一局域网，选择电视应用里显示的设备名称。初始名称为 `Sony A80EK Cast`，可以修改。
3. iPhone / iPad / Mac 可用系统屏幕镜像；视频 App 内的投屏入口可能走 DLNA，取决于 App。

| 遥控器操作 | 结果 |
| --- | --- |
| 单按左 / 右 | 后退 / 前进 10 秒 |
| 持续按住左 / 右 | 按 1× → 2× → 3× → 4× → 5× 增加跳转距离 |
| 松开或换方向 | 清空加速档位，下次从 1× 开始 |
| 隐藏控件时按确定 | 一次播放 / 暂停，并显示控件 |
| 上 / 下 | 显示控件；中央按钮下键进入进度条 |
| 返回 | 先收起控件，再按退出播放 |

加速倍率相对于原有 10 秒跳转：每个已处理重复事件分别跳 10 / 20 / 30 / 40 / 50 秒，
重复事件间隔至少 250 ms。它加快定位进度，正常播放速率不变。暂停时跳转仍保持暂停。
控件显示的分辨率来自当前解码视频尺寸。

## 构建与验证

详见 [BUILDING.md](BUILDING.md)。需要 JDK 17 或 21、Android SDK 35、Build Tools 35.0.0、
NDK 28.2.13676358 和 CMake 3.22.1。

```shell
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Windows 使用 `gradlew.bat`。APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。
本地 SDK 路径和签名密钥自行配置，不包含在仓库内；debug 构建使用本机自动生成的测试签名。

test12 已通过 18 项 JVM 单元测试；Android Lint 为 0 错误、59 警告、1 条信息。
在 A80EK 上通过标准方向键事件验证了两个方向的全部加速档位、实际跳转距离、松开复位与暂停保持，
并用实际 MP4 回归播放控制。标准 AirPlay URL 视频播放、暂停和拖动也已回归。
这不代表所有电视、实体遥控器、iOS 版本或视频平台均兼容。

更多说明：[TV 设计与按键](TV_DESIGN.md)、[构建修改与协议修复](SONY_BUILD.md)、
[AirPlay 视频回移记录](AIRPLAY_VIDEO_BACKPORT.md)。

## 已知限制

- 当前为自用互通测试版本。DRM 内容不在支持范围内；4K/HDR、照片、后台长期运行和待机恢复需要进一步验证。
- 原生构建附带固定版本 OpenSSL 3.1.8 的 ARM 静态 libcrypto，版本较旧，正式发布前应升级。
  来源、许可证、校验值及重建脚本见 [OpenSSL 说明](app/src/main/cpp/third_party/android-openssl/PROVENANCE.md)。
- 首次开播窗口内的首个远端 Seek(0) 会被兼容逻辑忽略一次；具体条件和边界见 SONY_BUILD.md。

## 开源许可与致谢

本项目按 [GPL-3.0](LICENSE) 提供；分发修改版时请遵守其源码提供要求。
保留上游及第三方代码的版权和各自许可文件。主要来源包括：

- [F-e-n-y-x/airplay-dlna-receiver](https://github.com/F-e-n-y-x/airplay-dlna-receiver)
- [jqssun/android-airplay-server](https://github.com/jqssun/android-airplay-server)
- [UxPlay](https://github.com/FDH2/UxPlay)
- AndroidX Media3、Compose、Hilt，以及随源码附带的 ALAC、libplist、llhttp、playfair 和 OpenSSL 构建依赖。

本项目不是 Sony、Apple 或 Google 的官方产品，与这些品牌没有关联。
