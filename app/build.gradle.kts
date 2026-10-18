import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Release signing: reads ../keystore.properties when present (never committed).
// Without it, release builds are produced unsigned so anyone can still build.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.example.swish"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.swish"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // Phones are ARM; dropping x86 roughly halves the APK. Debug builds keep all ABIs for emulators.
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
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
