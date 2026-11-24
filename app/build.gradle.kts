import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val retailPricesEnabled = providers.gradleProperty("nutrinestRetailPricesEnabled")
    .map { it.toBooleanStrict() }.orElse(true).get()
val retailPriceBaseUrl = providers.gradleProperty("nutrinestRetailPriceBaseUrl").orElse("").get().trim()
if (retailPriceBaseUrl.isNotEmpty()) {
    val url = URI(retailPriceBaseUrl)
    require(url.scheme == "https" && url.host != null && url.rawUserInfo == null &&
        url.rawQuery == null && url.rawFragment == null) {
        "nutrinestRetailPriceBaseUrl must be an HTTPS publisher URL without credentials, query or fragment"
    }
}

android {
    namespace = "com.zame.nutrinest"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.zame.nutrinest"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("boolean", "RETAIL_PRICES_ENABLED", retailPricesEnabled.toString())
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["retailPriceBaseUrl"] = retailPriceBaseUrl
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
        }
    }
    if (retailPriceBaseUrl.isNotEmpty()) {
        sourceSets.named("debug") { manifest.srcFile("src/retail/AndroidManifest.xml") }
        sourceSets.named("release") { manifest.srcFile("src/retail/AndroidManifest.xml") }
    }
    providers.gradleProperty("nutrinestLocalPublisherRes").orNull?.let { localRes ->
        require(URI(retailPriceBaseUrl).host == "localhost")
        sourceSets.named("debug") {
            manifest.srcFile("src/localPublisher/AndroidManifest.xml")
            res.srcDir("src/localPublisher/res")
            res.srcDir(localRes)
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
    // The app has an in-app language switcher, so both bundled locales must
    // always be present instead of being split into on-demand resources.
    bundle {
        language {
            enableSplit = false
        }
    }
}

// Room writes the schema of every version here. Checked in, so the v4 -> v5
// migration can be verified against the real schemas instead of by eye.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Added for navigation between screens (Home → Recipes / Weekly Menu)
    implementation(libs.androidx.navigation.compose)
    // Added for the icons used on the Home screen action cards
    implementation(libs.androidx.compose.material.icons.extended)

    testImplementation(libs.junit)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.room.testing)
    // Room's migration JSON runtime uses serialization 1.8.1. AGP aligns the test
    // APK to the tested APK's core version; keep debug core compatible with JSON.
    debugImplementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1")
    debugImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Room (local database)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Image loading
    implementation(libs.coil.compose)

    // ViewModel in Compose
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    //Notifications
    implementation(libs.androidx.work.runtime)
}

android.sourceSets.getByName("androidTest").assets.directories.add("$projectDir/schemas")
