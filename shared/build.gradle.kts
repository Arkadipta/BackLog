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
        }
    }
}
