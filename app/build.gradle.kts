import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingProperties = Properties().apply {
    val localFile = rootProject.file("signing.local.properties")
    if (localFile.isFile) localFile.inputStream().use { load(it) }
}
fun signingValue(name: String): String? = providers.environmentVariable(name).orNull
    ?: signingProperties.getProperty(name)
val signingStore = signingValue("ADFREE_KEYSTORE_PATH")
val signingPassword = signingValue("ADFREE_KEYSTORE_PASSWORD")
val signingAlias = signingValue("ADFREE_KEY_ALIAS")
val signingKeyPassword = signingValue("ADFREE_KEY_PASSWORD")
val hasReleaseSigning = listOf(signingStore, signingPassword, signingAlias, signingKeyPassword)
    .all { !it.isNullOrBlank() }

android {
    namespace = "xyz.fearr.adfree"
    compileSdk = 37

    defaultConfig {
        applicationId = "xyz.fearr.adfree"
        minSdk = 26
        targetSdk = 37
        versionCode = 12
        versionName = "0.11.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("distribution") {
                storeFile = rootProject.file(requireNotNull(signingStore))
                storePassword = signingPassword
                keyAlias = signingAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("distribution")
        }
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("distribution")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
}

val validateReleaseSigning = tasks.register("validateReleaseSigning") {
    doLast {
        check(hasReleaseSigning) {
            "Release signing is missing. Configure signing.local.properties or ADFREE_KEYSTORE_* environment variables."
        }
        check(rootProject.file(requireNotNull(signingStore)).isFile) { "Release keystore does not exist." }
    }
}
tasks.matching { it.name == "packageRelease" }.configureEach {
    dependsOn(validateReleaseSigning)
}

dependencies {
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.4")
    compileOnly("io.github.libxposed:api:102.0.0")
    implementation("io.github.libxposed:service:102.0.0")
    testImplementation("io.github.libxposed:api:102.0.0")
    testImplementation("junit:junit:4.13.2")
}
