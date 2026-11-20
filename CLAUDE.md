# BackLog

A personal todo app: a Kotlin Multiplatform core (`shared/`) with a SwiftUI Mac app (`macApp/`), a Compose Android app (`androidApp/`) and a Cloudflare Worker for sync (`worker/`).

- Spec: [Personal_Todo_App_Spec_v1.md](Personal_Todo_App_Spec_v1.md)
- Build plan and task status: [docs/plan/README.md](docs/plan/README.md)
- Decisions and spec deltas: [docs/plan/decisions.md](docs/plan/decisions.md). Where it disagrees with the spec, decisions.md wins.

## Session protocol

Follow "Running a session" in [docs/plan/README.md](docs/plan/README.md):
- Before writing code, read decisions.md, the task's phase file and the spec sections it lists.
- Work on a branch named `task/<id>-<slug>`.
- Before finishing, set the task's status, write Notes in the phase file, and merge to `main`.

Commits are conventional and end with the task id, e.g. `feat(sync): merge changes field by field (1.3)`.

## Environment

Set these in every shell that runs Gradle (D4):

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"   # JBR 21
export ANDROID_HOME="$HOME/Library/Android/sdk"
```

- Run Gradle only through `./gradlew`; there is no global Gradle.
- Run wrangler only through `npx`, inside `worker/`.
- Xcode 27.2 is installed and selected. XcodeGen is installed from Homebrew.
- `local.properties` (gitignored) holds `sdk.dir`. Recreate it with `printf 'sdk.dir=%s/Library/Android/sdk\n' "$HOME" > local.properties`.
- compileSdk is 37.2, because Compose 1.13 requires 37.1 or later. Install it with `sdkmanager --install "platforms;android-37.2"` if it's missing.
- Java on this machine can't connect to `release-assets.githubusercontent.com`, though curl can. When the wrapper needs a new Gradle distribution, download the zip with curl into `~/.gradle/wrapper/dists/gradle-<v>-bin/<hash>/`. Check it against the published `.sha256`; the wrapper also checks `distributionSha256Sum`. Pass `--no-validate-url` to `./gradlew wrapper`.

## Versions

All versions live in `gradle/libs.versions.toml`. Kotlin is pinned to the newest release that SKIE supports (D2). Upgrade Kotlin, SKIE, SQLDelight, Ktor and the kotlinx libraries together. To see which Kotlin versions a SKIE release supports, look at its `gradle-plugin-<v>.jar` on Maven Central. The `co/touchlab/skie/plugin/shim/impl_X_Y_Z` packages list them. Swift Export stays off.

The shared module uses AGP 9's Android-KMP library plugin (`com.android.kotlin.multiplatform.library`), set up in the `kotlin { android { … } }` block. It doesn't use `androidTarget()` with `com.android.library`, which AGP 9 no longer supports.

## Build and test

| What | Command |
| --- | --- |
| All shared tests (jvm, macosArm64, Android host) | `./gradlew :shared:allTests :shared:testAndroidHostTest` |
| Shared tests on one target | `./gradlew :shared:jvmTest`, `:shared:macosArm64Test` or `:shared:testAndroidHostTest` |
| Android debug APK | `./gradlew :androidApp:assembleDebug` (APK in `androidApp/build/outputs/apk/debug/`) |
| Install on a running emulator or device | `./gradlew :androidApp:installDebug` |
| Mac app | `cd macApp && xcodegen generate && xcodebuild -scheme BackLog build` (app in `~/Library/Developer/Xcode/DerivedData/BackLog-*/Build/Products/Debug/`) |
| Worker | Added in task 3.1: `cd worker && npx wrangler …` |

Use the `BackLog_API_36` AVD (8 GB data partition), made in 0.2. `Medium_Phone_API_36` is full and can't install the APK. Start it with `~/Library/Android/sdk/emulator/emulator -avd BackLog_API_36`.

During macOS linking, Kotlin/Native prints many `warning: could not open …/stdlib-per-file-cache/….a(….a.o)` lines. This is linker noise with the Xcode 27 toolchain, and the tests still run. Filter it with `grep -v 'could not open'`.

## Layout

- `shared/`: KMP module with `android`, `jvm` and `macosArm64` targets. Code goes under `com.backlog` in `commonMain`, `commonTest`, `androidMain`, `jvmMain` and `macosMain`. Swift will import it as `BackLogCore` (D1).
- `androidApp/`: Compose app, `applicationId` `com.backlog.app`, minSdk 31.
- `macApp/`: `project.yml` is the source of truth, and the generated `.xcodeproj` is gitignored (D3). Never edit `.pbxproj`. Rerun `xcodegen generate` after adding or removing Swift files.
- `worker/`: from task 3.1.

## Mac app and the shared framework

- **Integration: KMP direct integration with a static framework.** A pre-build script phase in `project.yml` runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode`. That builds `BackLogCore.framework` (static, SKIE applied) into `shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`, which `FRAMEWORK_SEARCH_PATHS` points at. Because the framework is static, nothing is embedded or re-signed.
- The app links SQLite itself (`OTHER_LDFLAGS: -framework BackLogCore -lsqlite3`). It needs `DEAD_CODE_STRIPPING: YES`, because sqliter references `sqlite3_load_extension`, which the macOS SDK doesn't export. Without stripping, the link fails with undefined symbols.
- `ARCHS` is `arm64` only, because the framework is built for macosArm64 only.
- Signing uses the Personal Team `8WUD8HUYH5` with automatic signing. The Mac Development certificate was created in 0.2. If it's ever missing, build once with `-allowProvisioningUpdates`.
- Swift 6 language mode. Kotlin classes are non-`Sendable` in Swift, so every thread-safe shared type that Swift calls `async` methods on needs a line in `macApp/BackLog/KotlinSendable.swift`.
- The sandboxed app keeps its database at `~/Library/Containers/com.backlog.app/Data/Library/Application Support/BackLog/backlog.db`.
- For UI checks from the terminal, drive the app with `osascript` and System Events. Rex Server has Automation and Accessibility access. Focus a SwiftUI text field with `set focused of … to true`, because `click` doesn't focus it.
