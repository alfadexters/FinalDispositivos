// build.gradle.kts (Project-level)

plugins {
    id("com.google.gms.google-services") version "4.4.3" apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

