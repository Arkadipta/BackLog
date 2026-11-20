import co.touchlab.skie.configuration.SkieVisibility

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.skie)
}

kotlin {
    // AGP 9 replaces `androidTarget()` + `com.android.library` with the
    // Android-KMP library plugin, configured through this block.
    android {
        namespace = "com.backlog.shared"
        compileSdk {
            version = release(libs.versions.androidCompileSdk.get().toInt()) {
                minorApiLevel = libs.versions.androidCompileSdkMinor.get().toInt()
            }
        }
        minSdk = libs.versions.androidMinSdk.get().toInt()
        // Runs commonTest on the JVM against the Android stubs (testAndroidHostTest).
        withHostTest {}
    }
    jvm()
    macosArm64 {
        binaries.framework {
            // Swift imports the shared core as `BackLogCore` (D1).
            baseName = "BackLogCore"
            // Static: linked into the app binary, so nothing to embed or re-sign.
            // The app links libsqlite3 itself (OTHER_LDFLAGS in macApp/project.yml).
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // `api`: Flow is part of the public API the apps consume.
            api(libs.kotlinx.coroutines.core)
            // `api`: the model's dates and times are kotlinx-datetime types.
            api(libs.kotlinx.datetime)
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.cryptography.core)
            // JDK on jvm/Android; CryptoKit + CommonCrypto on macOS.
            implementation(libs.cryptography.provider.optimal)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        // The host test runs on a plain JVM, so it uses the JDBC driver, not the Android one.
        getByName("androidHostTest").dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.sqlite.driver)
        }
        macosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }
}

sqldelight {
    databases {
        create("BackLogDatabase") {
            packageName.set("com.backlog.db")
            dialect(libs.sqldelight.dialect.sqlite330)
            // The schema is the migrations applied in order: 1.sqm creates
            // version 1, and each later N.sqm migrates version N to N + 1.
            // Schema.version is the highest N.sqm + 1 (version 1 is an empty
            // database), so 1.sqm alone gives version 2.
            deriveSchemaFromMigrations.set(true)
            // Code generation fails on any migration statement that doesn't
            // compile against the schema before it. With a derived schema there
            // is no separate .sq schema to diff, so no N.db snapshots are kept;
            // DatabaseSchemaTest checks create and migrate agree at runtime.
            verifyMigrations.set(true)
        }
    }
}

skie {
    features {
        // SQLDelight's generated code is public, and its row classes are named
        // after the tables (`Task`, `List`). In Swift those would shadow
        // `_Concurrency.Task` and `SwiftUI.List`, so hide the whole package.
        // Apps use the model types in com.backlog.model instead.
        group("com.backlog.db") {
            SkieVisibility(SkieVisibility.Level.Private)
        }
    }
}
