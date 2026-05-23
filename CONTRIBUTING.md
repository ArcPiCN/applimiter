# 开源上传清单

## ✅ 必传

源码与项目骨架：

```
app/                          # 应用模块（除 build/ 外全传）
├── src/main/
│   ├── java/                 # Kotlin 源码
│   ├── res/                  # 资源（含 mipmap、drawable、values、raw、xml）
│   └── AndroidManifest.xml
├── build.gradle.kts
└── proguard-rules.pro

gradle/wrapper/
├── gradle-wrapper.jar        # 必须包含！没了别人构建不了
└── gradle-wrapper.properties

build.gradle.kts              # 根项目
settings.gradle.kts
gradle.properties
gradlew                       # Linux/Mac 启动脚本（如果没有可在 Studio 里 Tasks → wrapper 重新生成）
gradlew.bat                   # Windows 启动脚本

LICENSE
README.md
CONTRIBUTING.md               # 本文件
.gitignore
applimiter-rules.json         # 示例规则（也可不传，因为内置在 res/raw 里）
需求文档.md                    # 可传，给贡献者参考
```

## ❌ 不要传

会被 .gitignore 自动排除，但提交前最好手动确认：

```
.gradle/                      # Gradle 缓存
.idea/                        # IDE 工程配置（每个人不一样）
*.iml                         # IDE 模块文件
build/                        # 编译产物
app/build/                    # 编译产物
local.properties              # 含本机 SDK 路径，敏感
*.apk / *.aab                 # 打包产物
*.jks / *.keystore            # 签名密钥，泄露后果严重
keystore.properties           # 同上
```

## 首次上传步骤

```bash
# 1. 在项目根目录初始化（如果你之前没用 git）
git init
git add .gitignore LICENSE README.md CONTRIBUTING.md
git commit -m "chore: license, readme and gitignore"

# 2. 加上源码
git add app/ gradle/ gradlew gradlew.bat *.gradle.kts gradle.properties
git add 需求文档.md applimiter-rules.json
git commit -m "feat: initial commit of AppLimiter"

# 3. 加远程并推
git remote add origin https://github.com/ArcPiCN/applimiter.git
git branch -M main
git push -u origin main
```

## 提交前检查

```bash
# 确认没有把敏感文件暂存
git status

# 确认 .idea/、build/、local.properties 都不在列表里
# 如果有，先 git rm --cached <文件> 撤销暂存
```

## 后续协作

- 用 Pull Request 接受贡献，不要直接 push 到 main
- 重大改动先开 Issue 讨论
- 规则贡献：只需修改 `app/src/main/res/raw/default_rules.json`
- Bug 报告请附设备型号、Android 版本、复现步骤
