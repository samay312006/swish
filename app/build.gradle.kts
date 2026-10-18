plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.swish"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.swish"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Google MediaPipe for Hand Tracking
    implementation(libs.mediapipe.tasks.vision)

    // Interpreter.
    implementation(libs.tensorflow.lite)

    // Appium and Selenium for UI Testing
    testImplementation("io.appium:java-client:9.1.0")
    testImplementation("org.seleniumhq.selenium:selenium-java:4.17.0")
    testImplementation("junit:junit:4.13.2")

}
