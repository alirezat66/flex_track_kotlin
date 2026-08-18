import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
}

group = "io.github.alirezat66"
version = "1.2.0"

android {
    namespace = "dev.flextrack"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

mavenPublishing {
    configure(
        AndroidSingleVariantLibrary(
            variant = "release",
            sourcesJar = SourcesJar.Sources(),
            javadocJar = JavadocJar.Empty(),
        ),
    )
    coordinates(
        groupId = "io.github.alirezat66",
        artifactId = "flextrack",
        version = project.version.toString(),
    )
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()

    pom {
        name.set("FlexTrack Kotlin")
        description.set("Consent-aware, deterministic analytics routing for Android and Kotlin.")
        inceptionYear.set("2026")
        url.set("https://github.com/alirezat66/flex_track_kotlin")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("alirezat66")
                name.set("Reza Taghizadeh")
                email.set("alirezataghizadeh66@gmail.com")
                url.set("https://github.com/alirezat66")
            }
        }
        scm {
            url.set("https://github.com/alirezat66/flex_track_kotlin")
            connection.set("scm:git:git://github.com/alirezat66/flex_track_kotlin.git")
            developerConnection.set("scm:git:ssh://git@github.com/alirezat66/flex_track_kotlin.git")
        }
    }
}
