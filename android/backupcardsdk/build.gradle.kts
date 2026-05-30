plugins {
    alias(libs.plugins.android.library)
    id("maven-publish") 
}

android {
    namespace = "com.ziancube.backupcardsdk"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24

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

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
        }
    }

    defaultConfig {
        externalNativeBuild {
            cmake {
                cppFlags(
                    "",
                    "-std=c++11",
                    "-frtti",
                    "-fexceptions",
                    "-DHAVE_ENDIAN_H"
                )

                arguments(
                    "-DANDROID_TOOLCHAIN=clang",
                    "-DANDROID_STL=c++_static",
                    "-DANDROID_ARM_MODE=arm",
                    "-DANDROID_PLATFORM=android-19"
                )
            }
        }

        ndk {
            abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86"))
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

afterEvaluate {
    extensions.configure<org.gradle.api.publish.PublishingExtension>("publishing") {

        publications {
            create("release", org.gradle.api.publish.maven.MavenPublication::class.java) {

                from(components.getByName("release"))

                groupId = "com.ziancube"
                artifactId = "backupcardsdk"
                version = "1.0.0"
            }
        }
    }
}