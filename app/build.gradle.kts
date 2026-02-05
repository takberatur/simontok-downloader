import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
}

android {
    namespace = "com.agcforge.videodownloader"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.agcforge.videodownloader"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		ndk {
			//noinspection ChromeOsAbiSupport
			abiFilters += listOf("armeabi-v7a", "arm64-v8a")
		}

    }
    ndkVersion = "29.0.14033849 rc4"
    buildTypes {
        getByName("debug") {
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        dataBinding = true
        viewBinding = true
        buildConfig = true
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = false
        baseline = file("lint-baseline.xml")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL,AL2.0,LGPL2.1}"
            excludes += "/META-INF/README.md"
            excludes += "/META-INF/LICENSE*"
            excludes += "/META-INF/NOTICE*"
        }
        jniLibs.useLegacyPackaging = true
    }

	externalNativeBuild {
		cmake {
			path = file("src/main/jni/CMakeLists.txt")
		}
	}

}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
        freeCompilerArgs.add("-XXLanguage:+PropertyParamAnnotationDefaultTargetMode")
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}
dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlin.stdlib)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.work.runtime.ktx)
    implementation(libs.work.rxjava2)
    implementation(libs.work.gcm)
    androidTestImplementation(libs.work.testing)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    // General Library
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation (libs.cardview)
    implementation (libs.coordinatorlayout)
    implementation (libs.drawerlayout)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.fragment.compose)
    debugImplementation(libs.androidx.fragment.testing)
    implementation (libs.gridlayout)
    implementation (libs.preference)
    implementation (libs.preference.ktx)
    implementation (libs.recyclerview)
    implementation (libs.swiperefreshlayout)
    implementation (libs.viewpager2)
    implementation (libs.palette)
    implementation (libs.vectordrawable.animated)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material3.window.size.class1)
    implementation(libs.androidx.material3.adaptive.navigation.suite)
    implementation(libs.kotlinx.datetime)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.swiperefreshlayout)
    implementation(libs.androidx.core.splashscreen)
    // Messaging, Database & Ads
    implementation(libs.play.services.ads)
	implementation(libs.androidx.credentials)
	implementation(libs.androidx.credentials.play.services.auth)
	implementation(libs.googleid)
    implementation(libs.unity.ads)
    implementation(libs.one.signal)
    implementation(libs.inapp.sdk)
    implementation(libs.billing)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.gson)
    implementation(libs.glide)
	debugImplementation(libs.chucker)
	releaseImplementation(libs.chucker.no.op)
    // Websocket and other
    implementation(libs.centrifuge.java)
    implementation(libs.lottie)
    // ============= MEDIA PLAYERS =============
    // ExoPlayer for Video (Media3)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.session)
    // Audio Visualizer (optional - for audio visualization)
    implementation(libs.audio.visualizer)
    // Permission handling
    implementation(libs.permissionx)
}
