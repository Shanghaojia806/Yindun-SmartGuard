# 银盾智鉴

面向老年群体的短视频多模态反诈交互系统。

An AI identity verification and anti-fraud monitoring HarmonyOS application for elderly care scenarios.

老人端与家属端共用同一个原生 Android App（按登录角色进入不同界面），管理侧为独立的 Web 后台，
业务后端为 FastAPI 服务，AI 模型由团队自主训练、最终部署到老人手机本地推理，
不调用商业 AI 识别 API，不依赖短视频平台私有接口。

## 仓库结构

```
银盾智鉴/
├── apps/
│   ├── android/            # Kotlin + Jetpack Compose 原生 App（老人端 / 家属端共用）
│   └── admin-web/          # Vue 3 + TypeScript + Vite 管理后台
├── docs/
│   ├── requirements/       # 需求基线、权限矩阵、页面索引、实施计划
│   └── api/                # 接口契约草案
└── .gitignore
```

后续阶段将新增 `services/business-api`（FastAPI）与 `ml/`（模型训练与导出），
本轮 P01 不创建，也不包含任何模型训练代码、采集服务或业务 API。

## 文档索引

| 文档 | 说明 |
| --- | --- |
| [PRODUCT_BASELINE.md](docs/requirements/PRODUCT_BASELINE.md) | 产品需求基线 |
| [ROLE_PERMISSION_MATRIX.md](docs/requirements/ROLE_PERMISSION_MATRIX.md) | 角色权限矩阵 |
| [PAGE_INDEX.md](docs/requirements/PAGE_INDEX.md) | 页面索引（E01–E15 / F01–F12 / A01–A10） |
| [IMPLEMENTATION_PLAN.md](docs/requirements/IMPLEMENTATION_PLAN.md) | 分阶段实施计划（P00–P13） |
| [CONTRACT_DRAFT.md](docs/api/CONTRACT_DRAFT.md) | 接口契约草案 |

## 环境要求

| 组件 | 要求 | 说明 |
| --- | --- | --- |
| JDK | 17 或 21 | 构建 Android 用。Gradle 8.9 不支持 JDK 22+，若 `JAVA_HOME` 指向更高版本需显式切换到 17/21 |
| Android SDK | Platform 35 + Build-Tools 35.0.0 | 需配置 `ANDROID_HOME` 或在 `apps/android/local.properties` 写 `sdk.dir` |
| Gradle | 8.9 | 已内置 Wrapper，无需本机安装 |
| Node.js | >= 20.19.0 | 管理后台用 |
| npm | >= 10 | 管理后台用 |

## Android（apps/android）

技术栈：Kotlin 2.0.21、Jetpack Compose（Material 3）、Navigation Compose、
MVVM、Room、DataStore、Kotlin Coroutines / Flow、Gradle Kotlin DSL、版本目录（`gradle/libs.versions.toml`）。

工程结构：

```
apps/android/
├── settings.gradle.kts          # 仓库与模块声明（含 :app）
├── build.gradle.kts             # 根插件声明
├── gradle.properties
├── gradle/
│   ├── libs.versions.toml       # 版本目录
│   └── wrapper/                 # Gradle 8.9 Wrapper
├── gradlew / gradlew.bat
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/silvershield/app/
        │   ├── MainActivity.kt
        │   ├── navigation/      # AppRoutes（含 elder / family 角色图预留）、SilverShieldNavHost
        │   └── ui/
        │       ├── theme/       # Color / Type / Theme
        │       └── welcome/     # WelcomeScreen 启动展示界面
        └── res/values/          # strings / colors / themes
```

角色导航预留：`AppRoutes` 中已声明 `WELCOME`、`ELDER_GRAPH`、`FAMILY_GRAPH`，
当前 `SilverShieldNavHost` 只注册 `WELCOME`；老人端与家属端的页面图将在后续阶段分别挂载。

构建与运行：

```powershell
# 1) 确保 JDK 为 17 或 21
$env:JAVA_HOME = "C:\Program Files\Java\latest\jdk-21"

# 2) 指定 Android SDK（二选一）
$env:ANDROID_HOME = "C:\Android\Sdk"
# 或在 apps/android/local.properties 中写：sdk.dir=C\:\\Android\\Sdk
# local.properties 属本地文件，已被 .gitignore 排除

# 3) 构建 Debug APK
cd apps/android
.\gradlew.bat assembleDebug
```

产物路径：`apps/android/app/build/outputs/apk/debug/app-debug.apk`。
也可用 Android Studio 打开 `apps/android` 目录直接运行。

命令行安装 SDK（无需 Android Studio）：

```powershell
# 解压 command-line tools 到 <SDK>\cmdline-tools\latest 后：
$env:JAVA_HOME = 'C:\Program Files\Java\latest\jdk-21'
$cli = '<SDK>\cmdline-tools\latest\cmdline-tools\bin\android.exe'
& $cli --sdk=<SDK> sdk install 'platform-tools'
& $cli --sdk=<SDK> sdk install 'platforms;android-35'
& $cli --sdk=<SDK> sdk install 'build-tools;35.0.0'
```

新版 command-line tools 已用 `android sdk` 取代 `sdkmanager`，且不再需要手动执行 `--licenses`。

> 注：本仓库路径含中文，`gradle.properties` 中已设置 `android.overridePathCheck=true` 以允许 AGP 在此路径下工作。
> 若在其他机器上遇到路径相关异常，建议将仓库放到纯 ASCII 路径。

## Web 管理端（apps/admin-web）

技术栈：Vue 3、TypeScript（strict）、Vite 6、Element Plus、Pinia、Vue Router、ECharts。

工程结构：

```
apps/admin-web/
├── index.html
├── package.json
├── tsconfig.json                # strict + verbatimModuleSyntax + @ 别名
├── vite.config.ts               # 127.0.0.1:5173，/api 代理到 http://127.0.0.1:8000
├── .env.example                 # VITE_API_BASE_URL / VITE_APP_TITLE
└── src/
    ├── main.ts                  # createApp + Pinia + Router + Element Plus（中文语言包）
    ├── App.vue
    ├── api/                     # 统一请求层：http.ts（超时/错误封装/envelope 解包）、types.ts
    ├── plugins/echarts.ts       # ECharts 按需注册（图表页在后续阶段接入）
    ├── router/index.ts          # 基础路由
    ├── stores/                  # Pinia 实例与 app store
    ├── styles/index.css         # 全局变量与基础样式
    └── views/HomeView.vue       # 启动页「银盾智鉴 · 管理后台」
```

安装与运行：

```powershell
cd apps/admin-web
npm install

npm run dev        # 开发服务 http://127.0.0.1:5173
npm run typecheck  # TypeScript 类型检查
npm run build      # 类型检查 + 生产构建，产物在 dist/
npm run preview    # 预览生产产物
```

环境变量：复制 `.env.example` 为 `.env.local` 并按需修改。
`VITE_API_BASE_URL` 为空时请求走相对路径，由 Vite 代理转发到业务后端。

## 工程规范

- 依赖目录、编译产物（`.gradle/`、`build/`、`node_modules/`、`dist/`）、密钥与签名文件
  （`*.keystore`、`*.jks`）、环境配置（`.env*`、`local.properties`）均由根 `.gitignore` 排除。
- 提交前请确认 `git status` 中不出现上述文件。
- 本轮为工程基线，任何统计图表与业务页面均不使用虚假数据填充。
