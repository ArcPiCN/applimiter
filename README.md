# 戒刷止刷 (AppLimiter)

一款本地运行的安卓 App，通过无障碍服务监听用户打开的短视频/社交 App 页面，按预设的停留时长自动返回桌面，帮助戒断短视频依赖。

- 官网：<https://applimiter.arcpi.cn>
- 出品：圆弧派（ARCPI）<https://arcpi.cn>
- 协议：MIT

## 特性

- 按 Activity 维度限制页面停留时长（精确到秒）
- 时间到自动返回桌面（HOME），不需要任何额外授权
- 悬浮窗一键添加规则：在任意应用任意页面，输入页面别名 + 选时长即可
- 规则按应用分组，支持多选批量启用/禁用/删除
- JSON 导入导出，支持本地文件和网络 URL
- 内置常见短视频页面规则集
- 液态玻璃 UI，雾感冰川蓝配色

## 运行环境

- minSdk 26（Android 8.0）
- targetSdk 34
- Kotlin + Jetpack Compose + Material3
- Room 持久化，Coil 图标加载

## 构建

1. 用 Android Studio Iguana 或更新版本打开
2. JDK 17（File → Settings → Gradle JDK）
3. 同步完成后真机运行
4. 启动后授权"无障碍服务"和"显示在其他应用上层"两个权限

## 项目结构

```
app/src/main/
├── java/com/example/applimiter/
│   ├── data/        # Room、Repository、JSON IO
│   ├── service/     # 无障碍服务、悬浮窗
│   ├── ui/          # Compose 屏幕、主题、ViewModel
│   └── util/        # 工具类
└── res/
    ├── raw/default_rules.json  # 内置规则
    ├── mipmap-*/icon.png        # 应用图标
    └── drawable/background.png  # 主题背景图（可选）
```

## 贡献规则文件

欢迎提 PR 扩充 `app/src/main/res/raw/default_rules.json`，覆盖更多短视频页面。Schema 见 `data/io/RuleIo.kt`。

## License

[MIT](LICENSE) © 2026 圆弧派 ARCPI
