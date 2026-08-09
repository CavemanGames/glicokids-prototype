plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("androidx.navigation.safeargs.kotlin")
}

android {
    namespace = "com.glicokids.prototype"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.glicokids.prototype"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0-prototype"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
    testOptions {
        unitTests {
            // Sem isto o Robolectric não enxerga res/raw e a semeadura do SQLite
            // (openRawResource de alimentos.json) falha nos testes de unidade.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // --- UI and Navigation ---
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.navigation:navigation-fragment-ktx:2.7.0")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.0")

    // --- Lifecycle and ViewModel ---
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.1")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.1")

    // --- Security ---
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // --- Persistência (Módulo 5): SQLiteOpenHelper escrito à mão + SharedPreferences.
    //     Room é PROIBIDO neste projeto (requisito acadêmico) — nenhuma dependência dele aqui. ---

    // --- Hilt ---
    implementation("com.google.dagger:hilt-android:2.59.2")
    ksp("com.google.dagger:hilt-android-compiler:2.59.2")

    // --- CameraX ---
    val cameraxVersion = "1.3.0"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // --- Testing ---
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.5")
    testImplementation("com.google.truth:truth:1.4.5")
    // Pinned to the kotlinx-coroutines-core version resolved transitively via
    // lifecycle-viewmodel-ktx (1.6.4) — kotlinx-coroutines-test must match the
    // core/android artifacts it wraps (Dispatchers.setMain, runTest).
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.6.4")
    // 4.10.3 só suportava até o SDK 33; com targetSdk 34 ele nem inicializa os testes.
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core-ktx:1.5.0")
    testImplementation("androidx.test.ext:junit-ktx:1.1.5")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("com.google.dagger:hilt-android-testing:2.59.2")
    kspTest("com.google.dagger:hilt-android-compiler:2.59.2")
    
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("com.google.truth:truth:1.4.5")
}
