# 计时器 timer-app

一个安卓倒计时应用。核心功能:**倒计时结束后在任何应用之上弹出悬浮横幅提醒,不手动划掉就一直存在**;锁屏/熄屏时像系统闹钟一样点亮屏幕全屏提醒。

## 功能

- 多个倒计时并行,各自命名、独立提醒
- 时/分/秒滚轮设置时长,支持保存常用预设一键启动
- 计时器支持暂停/继续/取消
- 到点提醒(按屏幕状态自动选择):
  - 亮屏解锁 → 顶部悬浮横幅(上划关闭,不划常驻,可跨应用显示)
  - 锁屏/熄屏 → 全屏通知点亮屏幕,显示响铃页
- 提醒方式可配置:循环响铃+震动(默认)/ 响一次+震动 / 仅震动 / 静音,使用系统默认闹钟铃声
- 倒计时运行期间通知栏常驻剩余时间
- 手机重启后自动恢复未完成的倒计时
- 应用内检查更新(设置 → 检查更新,数据源为本仓库 GitHub Releases)

## 系统要求

- Android 14(API 34)及以上,targetSdk 36(Android 16)
- 针对 ColorOS / OriginOS(OPPO/vivo 系)做了保活引导

## 需要的权限

| 权限 | 用途 |
|------|------|
| 悬浮窗 | 结束提醒横幅(核心功能) |
| 通知 | 计时进度与结束提醒 |
| 全屏通知 | 锁屏响铃页 |
| 精确闹钟(USE_EXACT_ALARM) | 到点准时触发,安装即授予 |
| 忽略电池优化 | 可选,提升后台可靠性 |
| 安装未知应用 | 仅应用内更新时使用 |

首次启动会引导授权。OPPO/vivo 用户建议在应用信息页开启「自启动」并将电量使用设为「无限制」,详见应用内「权限设置」页。

## 构建

```bash
# Debug
./gradlew assembleDebug

# Release(需要签名配置)
./gradlew assembleRelease
```

Release 签名:在项目根目录创建 `keystore/keystore.properties`(不入库):

```properties
storeFile=keystore/release.keystore
storePassword=...
keyAlias=timerapp
keyPassword=...
```

## 技术栈

Kotlin + Jetpack Compose (Material 3),AlarmManager 精确闹钟 + 前台服务,DataStore 持久化,OkHttp(更新检查)。

详细架构见 [docs/设计文档.md](docs/设计文档.md)。

## 发布约定

- tag 形如 `v1.2.0`,与 `versionName` 一致,`versionCode` 每次 +1
- Release 附件上传 APK,应用内更新按 tag 比对版本并下载该 APK
- 签名必须与旧版一致,否则无法覆盖安装
