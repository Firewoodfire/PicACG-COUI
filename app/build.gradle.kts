plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.picacg.coui"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.picacg.coui"
        minSdk = 31
        targetSdk = 37
        versionCode = 17
        versionName = "1.5.0"
        // GitHub Releases 更新检查地址。仓库开通后直接读取该仓库的最新公开 Release。
        buildConfigField("String", "GITHUB_REPOSITORY", "\"Firewoodfire/PicACG-COUI\"")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("io.github.itoseo:coui:0.0.1-alpha14")
}
