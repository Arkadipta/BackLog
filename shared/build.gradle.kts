plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
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
    macosArm64()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
