plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

group = "dev.taghizadeh.flextrack"
version = "0.1.0-SNAPSHOT"

android {
    namespace = "dev.taghizadeh.flextrack"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit.jupiter)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "flextrack"
            version = project.version.toString()

            afterEvaluate {
                from(components["release"])
            }

            pom {
                name.set("FlexTrack Kotlin")
                description.set("Consent-aware, deterministic analytics routing for Android and Kotlin.")
                url.set("https://github.com/alirezat66/flex_track_kotlin")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("alirezat66")
                        name.set("Reza Taghizadeh")
                    }
                }
                scm {
                    url.set("https://github.com/alirezat66/flex_track_kotlin")
                    connection.set("scm:git:https://github.com/alirezat66/flex_track_kotlin.git")
                    developerConnection.set("scm:git:ssh://git@github.com/alirezat66/flex_track_kotlin.git")
                }
            }
        }
    }
}
