plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
}

val configuredReleaseApiUrl = providers.gradleProperty("PROD_API_BASE_URL")
  .orElse(providers.environmentVariable("PROD_API_BASE_URL"))
  .getOrElse("")
val configuredStagingApiUrl = providers.gradleProperty("STAGING_API_BASE_URL")
  .orElse(providers.environmentVariable("STAGING_API_BASE_URL"))
  .getOrElse("")
val configuredLocalApiUrl = providers.gradleProperty("LOCAL_API_BASE_URL")
  .orElse(providers.environmentVariable("LOCAL_API_BASE_URL"))
  .getOrElse("")
val configuredReleaseJpushAppKey = providers.gradleProperty("JPUSH_APP_KEY")
  .orElse(providers.environmentVariable("JPUSH_APP_KEY"))
  .getOrElse("")
val allowInsecureProductionHttp = providers.gradleProperty("ALLOW_INSECURE_PRODUCTION_HTTP")
  .orElse(providers.environmentVariable("ALLOW_INSECURE_PRODUCTION_HTTP"))
  .map { it.equals("true", ignoreCase = true) || it == "1" }
  .getOrElse(false)
val approvedInsecureProductionApiUrl = "http://47.94.227.58/api/v1/"
val configuredStagingJpushAppKey = providers.gradleProperty("STAGING_JPUSH_APP_KEY")
  .orElse(providers.environmentVariable("STAGING_JPUSH_APP_KEY"))
  .getOrElse("")
val configuredDebugApiUrl = configuredLocalApiUrl.ifBlank { "http://127.0.0.1:18001/api/v1/" }
val releaseKeystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
val releaseKeyAlias = System.getenv("KEY_ALIAS") ?: "upload"
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
    versionCode = 24
    versionName = "1.1.17"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    manifestPlaceholders["usesCleartextTraffic"] = "false"
    manifestPlaceholders["JPUSH_PKGNAME"] = "com.smartprocurement.internal"
    manifestPlaceholders["JPUSH_APPKEY"] = ""
    manifestPlaceholders["JPUSH_CHANNEL"] = "developer-default"
    buildConfigField("String", "APP_VARIANT_LABEL", "\"\"")
    buildConfigField("String", "JPUSH_APP_KEY", "\"\"")
  }

  signingConfigs {
    create("release") {
      storeFile = file(releaseKeystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = releaseKeyAlias
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
      signingConfig = signingConfigs.getByName("release")
      manifestPlaceholders["usesCleartextTraffic"] = "false"
      manifestPlaceholders["JPUSH_PKGNAME"] = "com.smartprocurement.internal"
      manifestPlaceholders["JPUSH_APPKEY"] = configuredReleaseJpushAppKey
      buildConfigField("String", "API_BASE_URL", "\"$configuredReleaseApiUrl\"")
      buildConfigField("String", "APP_VARIANT_LABEL", "\"\"")
      buildConfigField("String", "JPUSH_APP_KEY", "\"$configuredReleaseJpushAppKey\"")
    }
    create("staging") {
      initWith(getByName("debug"))
      matchingFallbacks += listOf("debug")
      applicationIdSuffix = ".staging"
      manifestPlaceholders["usesCleartextTraffic"] = "true"
      manifestPlaceholders["JPUSH_PKGNAME"] = "com.smartprocurement.internal.staging"
      manifestPlaceholders["JPUSH_APPKEY"] = configuredStagingJpushAppKey
      resValue("string", "app_name", "三公鲜配（测试）")
      buildConfigField("String", "API_BASE_URL", "\"$configuredStagingApiUrl\"")
      buildConfigField("String", "APP_VARIANT_LABEL", "\"测试版\"")
      buildConfigField("String", "JPUSH_APP_KEY", "\"$configuredStagingJpushAppKey\"")
    }
    create("local") {
      initWith(getByName("debug"))
      matchingFallbacks += listOf("debug")
      applicationIdSuffix = ".local"
      manifestPlaceholders["usesCleartextTraffic"] = "true"
      manifestPlaceholders["JPUSH_PKGNAME"] = "com.smartprocurement.internal.local"
      manifestPlaceholders["JPUSH_APPKEY"] = ""
      resValue("string", "app_name", "三公鲜配（本地测试）")
      buildConfigField("String", "API_BASE_URL", "\"$configuredLocalApiUrl\"")
      buildConfigField("String", "APP_VARIANT_LABEL", "\"本地验收版\"")
      buildConfigField("String", "JPUSH_APP_KEY", "\"\"")
    }
    create("orbpreview") {
      initWith(getByName("debug"))
      matchingFallbacks += listOf("debug")
      applicationIdSuffix = ".orbpreview"
      versionNameSuffix = "-动画预览"
      manifestPlaceholders["usesCleartextTraffic"] = "true"
      manifestPlaceholders["JPUSH_PKGNAME"] = "com.smartprocurement.internal.orbpreview"
      manifestPlaceholders["JPUSH_APPKEY"] = ""
      resValue("string", "app_name", "三公鲜配动画预览")
      buildConfigField("String", "API_BASE_URL", "\"$configuredDebugApiUrl\"")
      buildConfigField("String", "APP_VARIANT_LABEL", "\"动画预览版\"")
      buildConfigField("String", "JPUSH_APP_KEY", "\"\"")
    }
    debug {
      applicationIdSuffix = ".debug"
      manifestPlaceholders["usesCleartextTraffic"] = "true"
      manifestPlaceholders["JPUSH_PKGNAME"] = "com.smartprocurement.internal.debug"
      manifestPlaceholders["JPUSH_APPKEY"] = ""
      resValue("string", "app_name", "三公鲜配（开发）")
      buildConfigField("String", "API_BASE_URL", "\"$configuredDebugApiUrl\"")
      buildConfigField("String", "APP_VARIANT_LABEL", "\"开发版\"")
      buildConfigField("String", "JPUSH_APP_KEY", "\"\"")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
    resValues = true
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
  implementation(libs.androidx.work.runtime.ktx)
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
  implementation(libs.jpush)
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
    if (!hasReleaseSigning) {
      throw GradleException("Release build requires the existing production keystore and STORE_PASSWORD/KEY_PASSWORD; debug signing is not allowed")
    }
    val usesHttpsReleaseApi = configuredReleaseApiUrl.matches(Regex("https://[^/]+/api/v1/"))
    val usesExplicitTemporaryHttpApi = allowInsecureProductionHttp && configuredReleaseApiUrl == approvedInsecureProductionApiUrl
    if (!usesHttpsReleaseApi && !usesExplicitTemporaryHttpApi) {
      throw GradleException("PROD_API_BASE_URL is required and must use https://.../api/v1/; the approved temporary HTTP endpoint additionally requires ALLOW_INSECURE_PRODUCTION_HTTP=true")
    }
    if (configuredReleaseJpushAppKey.isBlank()) {
      throw GradleException("Release build requires JPUSH_APP_KEY from a Gradle property or environment variable")
    }
  }
  val buildsStagingVariant = allTasks.any { it.name.contains("Staging", ignoreCase = true) }
  val validStagingApiUrl = configuredStagingApiUrl.matches(Regex("https://[^/]+/api/v1/")) ||
    configuredStagingApiUrl.matches(Regex("http://(127\\.0\\.0\\.1|localhost|10\\.0\\.2\\.2):[0-9]+/api/v1/"))
  if (buildsStagingVariant && !validStagingApiUrl) {
    throw GradleException("STAGING_API_BASE_URL is required and must use HTTPS or an ADB/emulator loopback URL")
  }
  val buildsLocalVariant = allTasks.any {
    it.name.matches(Regex("(?i)^(assemble|bundle|install|compile|package|lint|test)Local.*"))
  }
  if (buildsLocalVariant && !configuredLocalApiUrl.matches(Regex("http://(127\\.0\\.0\\.1|localhost|10\\.0\\.2\\.2):[0-9]+/api/v1/"))) {
    throw GradleException("LOCAL_API_BASE_URL is required and must use a loopback host such as http://127.0.0.1:18001/api/v1/")
  }
}
