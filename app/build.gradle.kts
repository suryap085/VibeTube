plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.video.vibetube"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.video.vibetube"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file(project.property("RELEASE_STORE_FILE") as String)
            storePassword = project.property("RELEASE_STORE_PASSWORD") as String
            keyAlias = project.property("RELEASE_KEY_ALIAS") as String
            keyPassword = project.property("RELEASE_KEY_PASSWORD") as String
        }
    }

    buildTypes {
       debug {
            buildConfigField("String", "YOUTUBE_API_KEY", "\"AIzaSyAZHLNOqAy-R6p6PZBqnpj3a53dotM_htM\"")
            buildConfigField("boolean", "IS_DEBUG", "true")
            buildConfigField("int", "DAILY_QUOTA_LIMIT", "10000")
            isDebuggable = true
            isMinifyEnabled = false
        }
        getByName("release") {
            // IMPORTANT: Replace with your production API key
            buildConfigField("String", "YOUTUBE_API_KEY", "\"AIzaSyAZHLNOqAy-R6p6PZBqnpj3a53dotM_htM\"")
            buildConfigField("boolean", "IS_DEBUG", "false")
            buildConfigField("int", "DAILY_QUOTA_LIMIT", "10000")
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        buildConfig = true  // THIS IS CRUCIAL - enables BuildConfig generation
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.5")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.5")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
// Google Ads
    implementation("com.google.android.gms:play-services-ads:22.6.0")
    implementation("com.google.android.ump:user-messaging-platform:2.1.0")
// Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
// Image loading
    implementation("com.github.bumptech.glide:glide:4.16.0")
// Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:core:12.1.2")
    // pre-built UI (contains DefaultPlayerUiController)
    implementation("com.pierfrancescosoffritti.androidyoutubeplayer:custom-ui:12.1.2")
    // Security (Production)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Work Manager (for background tasks)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.facebook.shimmer:shimmer:0.5.0")

    // Firebase for Cross-Device Sync (YouTube Policy Compliant)
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}