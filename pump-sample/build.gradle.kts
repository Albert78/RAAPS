plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "de.dh.raaps.plugin.pump"
    compileSdk = libs.versions.sdkCompile.get().toInt()

    defaultConfig {
        minSdk = libs.versions.sdkMin.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    }
}

dependencies {
    implementation(project(":common"))
    implementation(libs.androidx.core.ktx)
}