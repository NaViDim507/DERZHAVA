plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // отключаем kapt, поскольку Room больше не используется для генерации кода
    // id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.example.derzhava"   // <-- если у тебя другой package, поставь его
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.derzhava"  // тот же, что namespace
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")   // ← НОВОЕ
    implementation("androidx.fragment:fragment-ktx:1.8.2")

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Room (локальная БД) отключён. Оставляем только runtime, чтобы Entity и Dao аннотации
    // были доступны, но не подключаем компилятор и ktx.
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    // kapt("androidx.room:room-compiler:$roomVersion")
    // implementation("androidx.room:room-ktx:$roomVersion")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
