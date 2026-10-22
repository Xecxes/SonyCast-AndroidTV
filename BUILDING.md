# 构建 SonyCast Android TV

需要 JDK 17 或 21、Android SDK platform 35、Build Tools 35.0.0、
NDK 28.2.13676358、CMake 3.22.1。Gradle wrapper 为 8.11.1。
建议将项目放在不含空格的路径。

```shell
sdkmanager "platforms;android-35" "build-tools;35.0.0" "ndk;28.2.13676358" "cmake;3.22.1"
git clone https://github.com/Xecxes/SonyCast-AndroidTV.git
cd SonyCast-AndroidTV
```

设置 ANDROID_HOME，或在项目根目录建立未跟踪的 local.properties：

```properties
sdk.dir=C:/Android/Sdk
```

```shell
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
```

Windows 使用 `gradlew.bat`。调试 APK 位于 app/build/outputs/apk/debug/app-debug.apk。
首轮构建需要联网下载 Gradle 和 Maven 依赖；个人代理在本机配置，勿提交凭据。
原生依赖源码和固定版本 OpenSSL ARM 静态库已附带，无需 git submodule。

调试 APK 使用构建机器自己的 debug 密钥；不同机器构建的 APK 不一定能互相覆盖安装。
release 默认未签名。若需签名，在 local.properties 中配置 storeFile、storePassword、
keyAlias、keyPassword；签名文件和该配置均不可提交。

18 项 JVM 测试覆盖 UTF-8 HTTP 正文读取、初始 Seek 兼容过滤和长按加速策略。
真机加速测试的独立 APK 可用 `:app:assembleDebugAndroidTest` 构建，测试入口为
io.github.jqssun.airplay.ui.SeekHoldInstrumentation。它会替换当前测试媒体并注入遥控器按键，
仅应在自己授权的测试电视上运行。准备一个通过本机 HTTP 服务提供、时长至少 30 分钟且可跳转的媒体，
然后运行：

```shell
adb -s <device> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <device> install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <device> shell am instrument -w -e mediaUrl http://<test-host>:<port>/long.wav local.sonycast.receiver.test/io.github.jqssun.airplay.ui.SeekHoldInstrumentation
adb -s <device> uninstall local.sonycast.receiver.test
```

测试截图位于应用外部文件目录 seek-forward-5x.png / seek-backward-5x.png。
其他修改与依赖来源见 SONY_BUILD.md，TV 操作见 TV_DESIGN.md。
