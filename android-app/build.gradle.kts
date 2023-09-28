/*
 * Copyright 2020 RICOH Company, Ltd. All rights reserved.
 */

import java.util.*

plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.ricoh.livestreaming.app"
    compileSdk = 33
    defaultConfig {
        applicationId = "com.ricoh.livestreaming.app"
        minSdk = 25
        targetSdk = 33
        versionCode = 1
        versionName = "1.0.0"

        val props = Properties().apply { load(file("local.properties").inputStream()) }

        buildConfigField("String", "CLIENT_ID", "\"" + props["client_id"] + "\"")
        buildConfigField("String", "CLIENT_SECRET", "\"" + props["client_secret"] + "\"")
        buildConfigField("String", "ROOM_ID", "\"" + props["room_id"] + "\"")
        buildConfigField("int", "VIDEO_BITRATE", props["video_bitrate"].toString())
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions.apply {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "../libs", "include" to listOf("*.aar"))))
    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("com.github.tony19:logback-android:3.0.0")
    implementation("androidx.appcompat:appcompat:1.6.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    api("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-orgjson:0.11.2") {
        exclude("org.json", "json")     // provided by Android natively
    }
    implementation("com.squareup.okhttp3", "okhttp", "4.11.0")
    implementation("com.google.code.gson", "gson", "2.10.1")
}
