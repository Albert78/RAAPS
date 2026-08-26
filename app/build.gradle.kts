plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

android {
    namespace = "de.dh.raaps"
    compileSdk = libs.versions.sdkCompile.get().toInt()

    defaultConfig {
        applicationId = "de.dh.raaps"
        minSdk = libs.versions.sdkMin.get().toInt()
        targetSdk = libs.versions.sdkTarget.get().toInt()
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
    flavorDimensions += "version"
    productFlavors {
        create("sim") {
            dimension = "version"
            isDefault = true
            applicationIdSuffix = ".sim"
        }
        create("prod") {
            dimension = "version"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Configuration for "sim" flavor; the sim flavor is a test for the core calculation algorithm
    // and adds the sim-body module, which simulates a real body with glucose fluctuations, meals,
    // virtual insulin pump
    "simImplementation"(project(":sim-body"))

    // Configuration for "prod" flavor; set the modules for your CGM and pump
    "prodImplementation"(project(":source-glucose-receiver"))
    "prodImplementation"(project(":pump-sample"))

    // The sample modules are meant as copy template for real source and pump modules
//    implementation(project(":source-cgm-sample"))
//    implementation(project(":pump-sample"))

    // General dependencies
    implementation(project(":common"))
    implementation(project(":core"))
    implementation(project(":ui"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}