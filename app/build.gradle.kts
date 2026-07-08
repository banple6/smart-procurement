plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

val configuredReleaseApiUrl = providers.gradleProperty("API_BASE_URL")
  .orElse(providers.environmentVariable("API_BASE_URL"))
  .getOrElse("")
val allowInsecureHttpRelease = providers.gradleProperty("ALLOW_INSECURE_HTTP_RELEASE")
  .orElse(providers.environmentVariable("ALLOW_INSECURE_HTTP_RELEASE"))
  .map { it.equals("true", ignoreCase = true) || it == "1" || it.equals("yes", ignoreCase = true) || it.equals("on", ignoreCase = true) }
  .getOrElse(false)
val releaseKeystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
val debugKeystorePath = System.getenv("DEBUG_KEYSTORE_PATH")
  ?: "${System.getProperty("user.home")}/.android/debug.keystore"
val hasReleaseSigning = file(releaseKeystorePath).exists()
  && !System.getenv("STORE_PASSWORD").isNullOrBlank()
  && !System.getenv("KEY_PASSWORD").isNullOrBlank()

android {
  namespace = "com.smartprocurement.internal"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.smartprocurement.internal"
    minSdk = 24
    targetSdk = 36
    versionCode = 8
    versionName = "1.1.1"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    manifestPlaceholders["usesCleartextTraffic"] = "false"
    buildConfigField("String", "APP_VARIANT_LABEL", "\"\"")
  }

  signingConfigs {
    create("release") {
      storeFile = file(releaseKeystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file(debugKeystorePath)
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName(if (hasReleaseSigning) "release" else "debugConfig")
      manifestPlaceholders["usesCleartextTraffic"] = allowInsecureHttpRelease.toString()
      buildConfigField("String", "API_BASE_URL", "\"$configuredReleaseApiUrl\"")
      buildConfigField("String", "APP_VARIANT_LABEL", "\"\"")
    }
    create("staging") {
      initWith(getByName("debug"))
      matchingFallbacks += listOf("debug")
      applicationIdSuffix = ".staging"
      manifestPlaceholders["usesCleartextTraffic"] = "true"
      buildConfigField("String", "API_BASE_URL", "\"http://47.94.227.58/api/v1/\"")
      buildConfigField("String", "APP_VARIANT_LABEL", "\"测试版\"")
    }
    debug {
      manifestPlaceholders["usesCleartextTraffic"] = "true"
      buildConfigField("String", "API_BASE_URL", "\"http://47.94.227.58/api/v1/\"")
      buildConfigField("String", "APP_VARIANT_LABEL", "\"开发版\"")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.mlkit.barcode.scanning)
  implementation(libs.okhttp)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

gradle.taskGraph.whenReady {
  if (allTasks.any { it.name.contains("Release") }) {
    if (!configuredReleaseApiUrl.startsWith("https://") && !allowInsecureHttpRelease) {
      throw GradleException("Release build requires API_BASE_URL starting with https:// unless ALLOW_INSECURE_HTTP_RELEASE=true")
    }
  }
}
