import java.util.Properties

val localProperties = Properties().apply {
    load(rootProject.file("local.properties").inputStream())
}

val myGeminiApiKey: String = localProperties["MY_GEMINI_API_KEY"] as String
val jdoodleUrl: String = localProperties["JDOODLE_URL"] as String
val jdoodleClientId: String = localProperties["JDOODLE_CLIENT_ID"] as String
val jdoodleClientSecret: String = localProperties["JDOODLE_CLIENT_SECRET"] as String
val jdoodlePostUrl: String = localProperties["JDOODLE_POST_URL"] as String
val cloudinaryUrl: String = localProperties["CLOUDINARY_URL"] as String
val quotesApiUrl: String = localProperties["QUOTES_API_URL"] as String




plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.jsmart"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.jsmart"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildFeatures {
            buildConfig = true
        }
        buildConfigField(
            "String",
            "MY_GEMINI_API_KEY",
            "\"$myGeminiApiKey\""
        )
        buildConfigField(
            "String",
            "JDOODLE_URL",
            "\"$jdoodleUrl\""
        )
        buildConfigField(
            "String",
            "JDOODLE_CLIENT_ID",
            "\"$jdoodleClientId\""
        )
        buildConfigField(
            "String",
            "JDOODLE_CLIENT_SECRET",
            "\"$jdoodleClientSecret\""
        )
        buildConfigField(
            "String",
            "CLOUDINARY_URL",
            "\"$cloudinaryUrl\""
            )
        buildConfigField(
            "String",
            "JDOODLE_POST_URL",
            "\"$jdoodlePostUrl\""
        )
        buildConfigField(
            "String",
            "QUOTES_API_URL",
            "\"$quotesApiUrl\""
        )


        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    kotlinOptions {
        jvmTarget = "11"
        languageVersion = "2.1"
    }
    buildFeatures{
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.splashscreen)
    implementation (libs.glide)
    implementation (libs.gson)

    implementation ("com.itextpdf:itextg:5.5.10")
    implementation ("com.github.yalantis:ucrop:2.2.11")


    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation ("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    implementation ("com.google.ai.client.generativeai:generativeai:0.9.0")
    implementation ("com.cloudinary:cloudinary-android:2.0.0")
    implementation ("com.squareup.picasso:picasso:2.8")
    implementation ("io.github.amrdeveloper:codeview:1.3.9")
    implementation (libs.retrofit)
    implementation (libs.converter.gson)

    implementation(platform("com.google.firebase:firebase-bom:34.1.0"))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.database)
    implementation(libs.firebase.firestore.ktx)


    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    annotationProcessor (libs.compiler)
    implementation(libs.play.services.auth)


    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}