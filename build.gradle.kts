val sqldelight_version = "1.5.3"
val betterCipherSqlVersion = "7.6.2"
group = "com.github.julianhille.sqldelight.drivers.jscipher"
version = "1.0-SNAPSHOT"

buildscript {
    dependencies {
        classpath("com.squareup.sqldelight:gradle-plugin:1.5.3")
    }
}

plugins {
    kotlin("js") version "1.7.10"
}

repositories {
    maven("https://jitpack.io")
    google()
    mavenCentral()
}

dependencies {
     testImplementation(kotlin("test-js", "1.7.10"))
     implementation(npm("better-sqlite3-multiple-ciphers", betterCipherSqlVersion))
     api("com.squareup.sqldelight:runtime:1.5.3")
     implementation("com.squareup.sqldelight:coroutines-extensions:$sqldelight_version")
}

kotlin {
    js(IR) {
        browser()
        nodejs() {

        testTask {
            plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
                rootProject.the<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension>().ignoreScripts = false
            }
            useMocha {
                timeout = "5s"
            }
            filter.excludeTestsMatching("*JsWorker*")
        }
        }
        binaries.library()
    }
}