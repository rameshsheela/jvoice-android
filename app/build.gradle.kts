import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * Is a real Firebase config present?
 *
 * The google-services plugin fails the build outright when google-services.json
 * is missing, which would mean nobody can compile this project until the Firebase
 * project exists. So the plugin is applied only when the file is actually there,
 * and the app checks the same condition at runtime (see FirebaseAvailability) to
 * decide between real Firebase auth and the local demo logins.
 *
 * Consequence worth knowing: dropping google-services.json in is the ONLY step
 * needed to switch the app over. No code or Gradle edit.
 */
/**
 * Release signing. The keystore and its passwords live in keystore.properties,
 * which is gitignored - so a clone without that file still builds debug, and
 * only this machine (and your backup) can produce an upload-signed bundle.
 */
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val canSignRelease = keystoreProps.getProperty("storeFile") != null

val googleServicesFile = file("google-services.json")
val hasFirebaseConfig = googleServicesFile.exists()

if (hasFirebaseConfig) {
    apply(plugin = "com.google.gms.google-services")
    logger.lifecycle("J Voice: google-services.json found - Firebase is wired in.")
} else {
    logger.lifecycle(
        "J Voice: no google-services.json - building WITHOUT Firebase. " +
            "The app will run on local demo logins. See firebase/README.md."
    )
}

android {
    namespace = "com.jvoice.news"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jvoice.news"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.0.2"
        vectorDrawables { useSupportLibrary = true }

        // Mirrors the Gradle-time check above into the generated BuildConfig, so
        // the runtime guard and the build can never disagree about whether
        // Firebase is present.
        buildConfigField("boolean", "HAS_FIREBASE_CONFIG", hasFirebaseConfig.toString())
    }

    signingConfigs {
        if (canSignRelease) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Shrinking matters for the Play download size; the keep rules that
            // make it safe for Firestore reflection are in proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (canSignRelease) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }

    // The study-content export (SeedExportTest) writes its JSON next to the
    // uploader that consumes it, in the sibling `firebase` directory. Passed as a
    // property rather than assumed from the test's working directory, which AGP
    // does not guarantee.
    testOptions {
        unitTests.all {
            it.systemProperty(
                "seed.out",
                rootProject.file("../firebase/jvoice-seed.json").absolutePath
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    // The system splash, styled as the brand splash - see Theme.JVoice.Starting.
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.0")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("io.coil-kt:coil-compose:2.6.0")

    // Story videos from our own storage. YouTube links play in a WebView and
    // need nothing extra.
    // Face scan for a reporter's AI avatar: front camera preview (CameraX) and
    // on-device head-pose checks (ML Kit face detection, bundled - no network).
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")

    // ------------------------------------------------------------------ Firebase
    // The BOM pins every Firebase artifact to one compatible set, so individual
    // dependencies below carry no version.
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    // Auth: email/password sign-in.
    implementation("com.google.firebase:firebase-auth")
    // Realtime Database: the users/ tree and the session signals that hang off it
    // (role, isLogin, forceLogoutAt, update). Chosen for auth because the
    // kill-switch and force-logout guards are built on RTDB value events.
    implementation("com.google.firebase:firebase-database")
    // Firestore: the news and study content, which needs real queries. The News
    // module reads and writes it; the Study module is still on bundled data.
    implementation("com.google.firebase:firebase-firestore")
    // Cloud Messaging: the push behind the desk's "Send notification" switch.
    // Devices subscribe to the `news` topic (NewsPush) and a Cloud Function in
    // firebase/functions relays each newsNotifications write to it.
    implementation("com.google.firebase:firebase-messaging")
    // Functions: the staffAccounts callable, which creates and manages reporter
    // and editor logins - Auth users and their role claims cannot be made from
    // the phone itself.
    implementation("com.google.firebase:firebase-functions")
    // Storage: photos and videos a reporter uploads from the phone for a story
    // (news-photos/, news-videos/ - the folders the storage rules open to the desk).
    implementation("com.google.firebase:firebase-storage")
    // Play Install Referrer: which reporter's link a reader installed the app from.
    implementation("com.android.installreferrer:installreferrer:2.2")
    // Coroutine adapters for Task<T>, so the auth layer can `await()` instead of
    // nesting completion listeners.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Unit tests only: the one-off study-content export. No Android or Robolectric
    // dependency, because the data and codecs it walks are plain Kotlin.
    testImplementation("junit:junit:4.13.2")
}
