plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.jetbrains.dokka)
}

android {
    namespace = "com.media3watch.overlay"
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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(project(":sdk"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.cardview)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
}

mavenPublishing {
    coordinates(
        groupId = "io.github.oguzhaneksi",
        artifactId = "media3watch-overlay",
        version = project.property("VERSION_NAME") as String
    )

    pom {
        name.set("Media3Watch Overlay")
        description.set("Views-based debug overlay for Media3Watch real-time session snapshots.")
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

    publishToMavenCentral()
    signAllPublications()
}
