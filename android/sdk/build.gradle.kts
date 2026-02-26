plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.jetbrains.dokka)
}

android {
    namespace = "com.media3watch.sdk"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 23

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.mockito.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

mavenPublishing {
    coordinates(
        groupId = "io.github.oguzhaneksi",
        artifactId = "media3watch-sdk",
        version = project.property("VERSION_NAME") as String
    )

    pom {
        name.set("Media3Watch SDK")
        description.set("Android SDK for tracking Media3 ExoPlayer QOE metrics easily.")
        inceptionYear.set("2026")
        url.set("https://github.com/oguzhaneksi/Media3Watch")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("oguzhaneksi")
                name.set("Oğuzhan Ekşi")
                url.set("https://github.com/oguzhaneksi")
            }
        }
        scm {
            url.set("https://github.com/oguzhaneksi/Media3Watch")
            connection.set("scm:git:https://github.com/oguzhaneksi/Media3Watch.git")
            developerConnection.set("scm:git:ssh://git@github.com/oguzhaneksi/Media3Watch.git")
        }
    }

    // Configure publishing to Maven Central Portal
    publishToMavenCentral()

    // Enable GPG signing for all publications
    signAllPublications()
}
