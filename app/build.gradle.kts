import java.text.SimpleDateFormat
import java.util.Date

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val timestamp = SimpleDateFormat("dd-MM-yyyy_hh-mm").format(Date())

android {
    compileSdk = ProjectSetting.PROJECT_COMPILE_SDK
    namespace = ProjectSetting.PROJECT_NAME_SPACE
    defaultConfig {
        applicationId = ProjectSetting.PROJECT_APP_ID
        minSdk = ProjectSetting.PROJECT_MIN_SDK
        targetSdk = ProjectSetting.PROJECT_TARGET_SDK
        versionCode = ProjectSetting.PROJECT_VERSION_CODE
        versionName = ProjectSetting.PROJECT_VERSION_NAME
        multiDexEnabled = true
        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        create("release") {
            storeFile = file(ProjectSetting.KEY_PATH)
            storePassword = ProjectSetting.KEY_STORE_PASSWORD
            keyAlias = ProjectSetting.KEY_ALIAS
            keyPassword = ProjectSetting.KEY_ALIAS_PASSWORD
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isDebuggable = false
            isJniDebuggable = false
            isPseudoLocalesEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }
    applicationVariants.all {
        outputs.all {
            if (this is com.android.build.gradle.internal.api.ApkVariantOutputImpl) {
                this.outputFileName = "${ProjectSetting.NAME_APK}-[${ProjectSetting.PROJECT_VERSION_NAME}]-${timestamp}.apk"
            }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.material)
    implementation("com.google.android.gms:play-services-ads:23.0.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
