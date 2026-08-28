# TikTok 自动化工具 - 构建说明

## 项目结构

```
APK工具/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/tiktokhelper/
│       │   ├── MainActivity.java      # 控制面板
│       │   └── AutoService.java       # 自动化服务
│       └── res/
│           ├── layout/activity_main.xml
│           ├── values/strings.xml
│           ├── values/themes.xml
│           └── xml/accessibility_config.xml
├── build.gradle
├── settings.gradle
├── gradle.properties
└── gradlew.bat
```

## 构建方法

### 方法一：本地构建（需要 Android Studio）

1. 安装 Android Studio
2. File → Open → 选择 `APK工具` 文件夹
3. 等待 Gradle 同步完成
4. Build → Build APK(s)
5. APK 输出位置: `app/build/outputs/apk/debug/app-debug.apk`

### 方法二：命令行构建（需要 JDK 17+）

```bash
cd APK工具
./gradlew assembleDebug
```

### 方法三：在线构建（无需本地环境）

1. 把整个 `APK工具` 文件夹打包成 ZIP
2. 上传到以下任一在线构建服务：
   - https://appetize.io （支持 APK 构建）
   - 或使用 GitHub Actions 自动构建

## 安装使用

1. 安装 APK 到手机
2. 打开应用 → 点击"开启无障碍服务"
3. 在设置中找到 "TikTok Helper" → 开启
4. 打开 TikTok 停在首页
5. 回到应用 → 配置评论内容 → 点击启动

## 功能说明

| 功能 | 说明 |
|------|------|
| 自动点赞 | 双击屏幕自动点赞 |
| 自动评论 | 随机选择评论内容发布 |
| 概率控制 | 可调节点赞/评论触发概率 |
| 自定义评论 | 支持多条评论随机发送 |

## 注意事项

- 评论概率不要设太高（建议 20-30%），避免被检测
- 每次运行建议不超过 50 个视频
- 如被 TikTok 限制，暂停使用 24 小时
