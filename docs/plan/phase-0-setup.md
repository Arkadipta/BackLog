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

**Notes:**
- **Go/no-go: go.** SQLDelight → `Flow` → SKIE → `AsyncSequence` → `@Observable` → SwiftUI works on macOS 27.2 with the App Sandbox on. Adding a row updates the list live, and rows survive a relaunch. The GRDB fallback isn't needed.
- **Integration choice: KMP direct integration with a static framework.** `embedAndSignAppleFrameworkForXcode` runs in a pre-build script phase, and the framework lands in `shared/build/xcode-frameworks/<config>/<sdk>`. The details are in CLAUDE.md under "Mac app and the shared framework". Three snags hit the first build:
  1. No signing certificate. Automatic signing for the Personal Team `8WUD8HUYH5` needed one build with `-allowProvisioningUpdates` to create the Mac Development cert. Plain `xcodebuild -scheme BackLog build` works since then.
  2. Undefined `_sqlite3_load_extension` / `_sqlite3_enable_load_extension`. sqliter's cinterop wrappers reference them, but the macOS SDK's `libsqlite3.tbd` doesn't export them. `DEAD_CODE_STRIPPING: YES` fixes it, as Kotlin/Native's own test links already do. XcodeGen leaves this setting off by default.
  3. A Swift 6 "sending 'self.repository' risks causing data races" error on `try await repository.add(text:)` from a `@MainActor` model. The fix is `extension SpikeRepository: @retroactive @unchecked Sendable {}` in `macApp/BackLog/KotlinSendable.swift`. Iterating `for await` over a SKIE flow needed no conformance.
- **Build times** (M-series Mac; Kotlin/Native dependency caches in `~/.konan` warm). The Mac numbers are for the whole `xcodebuild -scheme BackLog build`, Gradle phase included.

  | Build | Time |
  | --- | --- |
  | Clean: Gradle daemon stopped, `shared/build` wiped, `xcodebuild clean` | 18.2 s |
  | Framework only, clean (`:shared:linkDebugFrameworkMacosArm64`, warm daemon) | 14.9 s |
  | No change (the Gradle phase always runs, up to date) | 2.7 s |
  | One Kotlin file changed | 7.1 s |
  | One Swift file changed | 6.0 s |
  | Release configuration, first build | 33.0 s |

- **How Kotlin looks from Swift** (SKIE 0.10.16, Kotlin 2.4.21). Phase 1 should design its public API around these:
  - **Names.** Classes keep their Kotlin names with no prefix (`SpikeRepository`). The ObjC header uses a `BLC` prefix, but Swift never sees it. Top-level functions become global Swift functions through SKIE (`createSpikeRepository()`, `greeting()`), and they're also reachable as `SpikeRepository_macosKt.createSpikeRepository()`. SQLDelight row types keep the table name: `Spike_note`, with snake_case properties (`created_at`). Phase 1 should return its own model classes (`Task`, `TaskList`), not generated rows.
  - **Numbers.** Non-null `Long`/`Int`/`Boolean` map to `Int64`/`Int32`/`Bool`. Nullable ones become boxed `KotlinLong?`/`KotlinInt?`/`KotlinBoolean?`, read with `.int64Value` and the like, which is clumsy. So avoid nullable primitives in the public API, or wrap them in a small model type. Nullable `String` is a plain `String?`.
  - **Value classes are erased** to `Any` (`id` in ObjC). `SpikeId` shows up as an untyped `Any`, both as a property and as a parameter. So don't use `@JvmInline value class` in the public API. Use `String` ids (UUIDs) or a regular data class.
  - **Enums** become real Swift enums through SKIE (`SpikePriority.high`, `CaseIterable`). The Kotlin class is still there as `__SpikePriority`.
  - **Sealed** classes and interfaces stay as classes or protocols, and SKIE adds `onEnum(of:)`, which returns an exhaustive Swift enum (`.ok(x)`, `.failed(x)`, `.empty(x)`). A `data object` is reached as `SpikeResultEmpty.shared`. Nested sealed subclasses are flattened to `SpikeResultOk` and so on.
  - **Flows.** `Flow<T>` becomes `SkieSwiftFlow<T>`, an `AsyncSequence` (`for await list in repo.notes()`). Element types map as usual: `List<Spike_note>` becomes `[Spike_note]`. A flow of a nullable type becomes a `SkieSwiftOptionalFlow`. SKIE documents that cancelling the Swift task cancels the Kotlin collection, which is how `.task {}` stops observing. The spike relied on this but didn't measure it.
  - **Suspend functions** become `async throws`. Every suspend function throws in Swift, even without `@Throws`. Under Kotlin/Native's rules, an exception that isn't listed in `@Throws` is fatal instead of becoming a Swift error. The spike didn't exercise this (`SpikeInterop.mayFail` is there to try it), so phase 1 suspend functions should either declare `@Throws` or not throw.
  - **Default arguments** aren't exposed (SKIE keeps that off by default). `withDefault(text:times:)` requires every argument, so add explicit overloads where Swift needs them.
  - **Data classes** get `init(...)`, `doCopy(...)` (not `copy`), `isEqual`, `hash` and `description()`, but no Swift `Equatable`/`Identifiable`. Use `List(items, id: \.id)` in SwiftUI.
  - **Doc comments** come through, but SKIE sometimes attaches one to the wrong generated wrapper (`notes()`'s doc ended up on `add`). It's cosmetic.
  - The generated header also exports SQLDelight runtime and coroutines types reachable from public API (`RuntimeTransacterImpl`, `Kotlinx_coroutines_coreFlow`, …). Keep `BackLogDatabase` out of the public API from phase 1 on.
- **Android.** `BackLogApplication` owns a lazy repository, and `SpikeViewModel` exposes `notes` with `stateIn(WhileSubscribed(5 s))`. The screen uses `collectAsStateWithLifecycle`. `kotlinx-coroutines-core` is now an `api` dependency of `shared`, because `Flow` is in its public API. The database is `backlog.db` in the app's database directory.
- **Emulator.** The existing `Medium_Phone_API_36` AVD has only 206 MB free on `/data`, and installs fail with "Requested internal only, but not enough space". 0.2 created `BackLog_API_36` from the same android-36 Play Store image, with an 8 GB data partition. The other AVD wasn't touched.
- **Tests.** `SpikeRepositoryTest` in `commonTest` checks that `add` makes `notes()` emit again. It runs on all three targets, using a JDBC in-memory driver on jvm and the Android host, and SQLDelight's `inMemoryDriver` on macOS. 1.2 deletes it along with the table, `SpikeInterop.kt` and the per-target `inMemoryDriver` actuals. Keep the pattern.
- **Evidence, 20 Nov 2026.**
  - `./gradlew :shared:allTests :shared:testAndroidHostTest :androidApp:assembleDebug`: BUILD SUCCESSFUL. `PlatformTest` and `SpikeRepositoryTest` passed on jvm, macosArm64 and the Android host (2 tests each, 0 failures).
  - `xcodegen generate && xcodebuild -scheme BackLog build`: BUILD SUCCEEDED, and the Release configuration also builds. `codesign` shows `com.backlog.app`, team `8WUD8HUYH5` and `com.apple.security.app-sandbox`.
  - Mac, driven through System Events: typing "buy milk" and pressing Return changed the window to "1 notes", and "call mum" made it "2 notes", newest first. After quitting and relaunching it still showed 2 notes, and `sqlite3` on the container's `backlog.db` showed both rows.
  - Android, on `BackLog_API_36` (API 36) with `adb input`: tapping Add and the keyboard's Done each added a row live ("1 notes", then "2 notes"), and the rows survived a force-stop and relaunch.
