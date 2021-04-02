/*
 * Copyright 2020 RICOH Company, Ltd. All rights reserved.
 */

buildscript {
    repositories {
        google()
        jcenter()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:3.5.2")
        classpath(kotlin("gradle-plugin", "1.3.41"))
        classpath("com.kezong:fat-aar:1.2.12")
    }
}

allprojects {
    repositories {
        maven { url = uri("https://github.com/ricohapi/theta-plugin-library/raw/master/repository") }
        google()
        jcenter()

        flatDir {
            dirs("libs")
        }
    }
}

tasks.create(BasePlugin.CLEAN_TASK_NAME, Delete::class.java) {
    group = BasePlugin.BUILD_GROUP
    delete(rootProject.buildDir)
}
