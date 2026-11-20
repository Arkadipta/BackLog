# Phase 0: toolchain and skeleton

This phase has two sessions. Task 0.2 is a spike on what the spec's Risks table calls the biggest technical risk, the Kotlin-to-Swift boundary on macOS. Don't start phase 1 until the spike passes or you've chosen the fallback.

---

### 0.1 Toolchain and monorepo skeleton
**Depends on:** none · **Spec:** Architecture › Repository layout, Libraries · **Decisions:** D1, D3, D4

**Before the session (you)**
- Install Xcode from the App Store and open it once so it installs its components. Then run `sudo xcode-select -s /Applications/Xcode.app/Contents/Developer`. Today only the Command Line Tools are installed, and both the Kotlin/Native macOS target and the Mac app need full Xcode.
- In Xcode › Settings › Accounts, sign in with your Apple ID so you have a Personal Team for signing.
- Run `brew install xcodegen`.

**Build**
- `git init` and a `.gitignore` covering Gradle, Kotlin, Xcode (including the generated `.xcodeproj`), Node and `local.properties`.
- A Gradle wrapper. There's no global Gradle on this machine, so either copy `gradlew` and `gradle/wrapper/` from `~/Codes/LifeLog` and bump `distributionUrl`, or `brew install gradle` once to generate the wrapper.
- A version catalog at `gradle/libs.versions.toml`, with versions picked per D2.
- `shared/`: a KMP module with `androidTarget`, `macosArm64` and `jvm` targets, plus `commonMain` and `commonTest` with one trivial test.
- `androidApp/`: an empty Compose app with id `com.backlog.app`, minSdk 31, depending on `:shared`.
- `CLAUDE.md` containing:
  - `JAVA_HOME` and `ANDROID_HOME`.
  - Build and test commands for each module.
  - Links to the spec, this plan and decisions.md.
  - A pointer to the session protocol in the plan README, and the commit style.

**Done when**
- `./gradlew :shared:allTests` passes on jvm and macosArm64.
- `./gradlew :androidApp:assembleDebug` succeeds.
- The first commit is on `main`.

**Notes:**
- **Versions (D2)**, all in `gradle/libs.versions.toml`:
  - Core set: Kotlin 2.4.21, SKIE 0.10.16, SQLDelight 2.4.1, Ktor 3.6.1, coroutines 1.11.0, kotlinx-datetime 0.8.0, kotlinx-serialization 1.13.0.
  - Build: AGP 9.5.1, Gradle 9.9.2.
  - AndroidX: Compose BOM 2026.11.01, activity-compose 1.13.0, core-ktx 1.19.1, lifecycle 2.11.1.
  - Why Kotlin 2.4.21: SKIE's docs page is out of date (it stops at Kotlin 2.0). The 0.10.16 plugin jar (29 Oct 2026) contains shims up to `impl_2_4_20` and lists 2.4.0–2.4.21 as supported. Kotlin 2.5.0 is still an RC. Every pinned kotlinx, Ktor and SQLDelight library was built against Kotlin 2.4.21 or older.
  - SKIE and SQLDelight are in the catalog but aren't applied yet. 0.2 applies them.
- **Deviation: Android target.** AGP 9 drops `androidTarget()` with `com.android.library`. `shared` uses `com.android.kotlin.multiplatform.library` with a `kotlin { android { … } }` block instead (namespace `com.backlog.shared`). `withHostTest {}` is on, so `commonTest` also runs as `:shared:testAndroidHostTest`. `allTests` doesn't include that task, so run it as well.
- **compileSdk 37.2, targetSdk 37, minSdk 31.** Compose 1.13 requires compileSdk 37.1 or later, so 0.1 installed `platforms;android-37.2`. AGP's DSL for this is `compileSdk { version = release(37) { minorApiLevel = 2 } }`.
- **Network quirk.** Java can't connect to `release-assets.githubusercontent.com` (connect timeout), but curl can, and Maven Central, Google Maven and the plugin portal all work from Java. The Gradle zip was fetched with curl and checked against its sha256, and the wrapper now pins `distributionSha256Sum`. CLAUDE.md has the steps for the next Gradle bump.
- **Kotlin/Native linker noise.** On macosArm64 test links, Xcode 27's linker prints hundreds of `could not open …stdlib-per-file-cache….a.o` warnings. They're harmless. If 0.2's framework link fails, try `kotlin.native.cacheKind.macosArm64=none` first.
- The project accessor `projects.shared` didn't resolve, so `androidApp` uses `project(":shared")`. Type-safe project accessors aren't enabled.
- **Evidence, 20 Nov 2026.**
  - After `./gradlew clean`, `./gradlew :shared:allTests :shared:testAndroidHostTest :androidApp:assembleDebug --no-build-cache --rerun-tasks` gave BUILD SUCCESSFUL.
  - `PlatformTest` passed on jvmTest, macosArm64Test and testAndroidHostTest (1 test each, 0 failures).
  - `aapt2 dump badging` on the APK shows package `com.backlog.app`, minSdk 31, targetSdk 37 and launchable activity `MainActivity`.
  - The APK wasn't run on an emulator, because "Done when" doesn't require it.

---

### 0.2 Kotlin ↔ Swift walking skeleton (risk spike)
**Depends on:** 0.1 · **Spec:** Risks (KMP macOS row); Architecture › Rules for the shared core; Libraries (SQLDelight, SKIE) · **Decisions:** D1, D2, D3

**Build**
- SQLDelight 2 in `shared`, with one throwaway table (`spike_note`) and three drivers: native on macOS, Android, and JDBC on jvm.
- A small repository with `Flow<List<SpikeNote>>` and `suspend fun add(text: String)`.
- SKIE, and a `BackLogCore` framework for `macosArm64`.
- `macApp/project.yml`: a SwiftUI app for macOS 14 with the App Sandbox on and bundle id `com.backlog.app`. Add a build phase that has Gradle build and embed the framework, using whichever approach works for macOS (KMP direct integration or a prebuilt framework), and record the choice in CLAUDE.md.
- On the Swift side, an `@Observable` model that reads the Flow as an `AsyncSequence`, and a window with a text field and a list. The database lives in the app container.
- On Android, the same list in Compose behind a `ViewModel`.

**Watch out**
- Before choosing versions, check which Kotlin versions SKIE supports (D2).
- Write down how Kotlin types look from Swift: suffixed names, `KotlinLong`, value classes, sealed classes, nullability. Phase 1 designs its public API around these notes.

**Done when**
- `xcodegen generate && xcodebuild -scheme BackLog build` succeeds from the terminal, the app launches, and adding a row updates the list live.
- Android does the same on an emulator.
- Clean and incremental framework build times are recorded under Notes.
- A go/no-go is recorded. If the interop is unworkable, stop and decide on the spec's GRDB fallback before phase 1.

**Out of scope:** the real schema and any UI design. Task 1.2 deletes the spike table and 2.1 replaces the spike UI.

**Notes:** _(filled in by the session)_
