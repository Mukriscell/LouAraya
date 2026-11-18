import java.net.URI
import java.math.BigDecimal
import java.util.Properties
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.android.junit)
}

val identityUrl = providers.gradleProperty("louArayaIdentityUrl").orElse("").get().trim()
val tallerUrl = providers.gradleProperty("louArayaTallerUrl").orElse("").get().trim()
require(identityUrl.isEmpty() == tallerUrl.isEmpty()) {
    "Configura las URL de identidad y taller en conjunto."
}

fun backendUrl(value: String, allowEmulatorHttp: Boolean = false): String {
    if (value.isEmpty()) return value
    val uri = URI(value)
    val local = allowEmulatorHttp && uri.scheme == "http" && uri.host == "10.0.2.2"
    require((uri.scheme == "https" || local) && uri.host != null && uri.userInfo == null &&
        uri.query == null && uri.fragment == null && (uri.port == -1 || uri.port in 1..65535)) {
        "Usa URL de servicios HTTPS. Para debug se admite HTTP en 10.0.2.2 (emulador)."
    }
    return value.trimEnd('/') + "/"
}

val signingFile = rootProject.file("output/control3/private/keystore.properties")
val emulatorTlsResources = providers.gradleProperty("louArayaEmulatorTlsResources").orNull
val control3Coverage = providers.gradleProperty("louArayaCoverage").orNull == "true"
if (control3Coverage) apply(plugin = "jacoco")
if (emulatorTlsResources != null) {
    require(listOf(identityUrl, tallerUrl).all { URI(it).scheme == "https" && URI(it).host == "10.0.2.2" }) {
        "El certificado local de evaluación solo se usa con ambos servicios HTTPS en el emulador."
    }
}
val releaseSigning = Properties().apply {
    if (signingFile.isFile) signingFile.inputStream().use(::load)
}

android {
    namespace = "cl.duoc.louaraya"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "cl.duoc.louaraya"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resValue("string", "control3_identity_url", "")
        resValue("string", "control3_taller_url", "")
        manifestPlaceholders["control3NetworkPolicy"] = "@xml/control3_network_policy"
    }

    signingConfigs {
        if (signingFile.isFile) create("academic") {
            storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
            storePassword = releaseSigning.getProperty("storePassword")
            keyAlias = releaseSigning.getProperty("keyAlias")
            keyPassword = releaseSigning.getProperty("keyPassword")
        }
    }

    buildTypes {
        create("prototype") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".prototype"
            versionNameSuffix = "-sync"
            matchingFallbacks += "debug"
            val syncServer = providers.gradleProperty("louArayaSyncServer").orElse("localhost").get()
            require(syncServer.matches(Regex("""(?:localhost|https://[a-z0-9.-]+)""")))
            resValue("string", "prototype_server", syncServer)
        }
        debug {
            resValue("string", "control3_identity_url", backendUrl(identityUrl, allowEmulatorHttp = true))
            resValue("string", "control3_taller_url", backendUrl(tallerUrl, allowEmulatorHttp = true))
            if (emulatorTlsResources == null) manifestPlaceholders["control3NetworkPolicy"] = "@xml/control3_network_policy_debug"
            enableUnitTestCoverage = control3Coverage
        }
        release {
            if (emulatorTlsResources != null) applicationIdSuffix = ".control3"
            // HTTPS is required for the exported APK. Debug loopback endpoints are not embedded.
            if (identityUrl.startsWith("https://") && tallerUrl.startsWith("https://")) {
                resValue("string", "control3_identity_url", backendUrl(identityUrl))
                resValue("string", "control3_taller_url", backendUrl(tallerUrl))
            }
            signingConfigs.findByName("academic")?.let { signingConfig = it }
            optimization {
                enable = false
            }
        }
    }

    emulatorTlsResources?.let { directory ->
        sourceSets.getByName("debug").res.srcDir(rootProject.file(directory))
        sourceSets.getByName("release").res.srcDir(rootProject.file(directory))
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    buildFeatures {
        compose = true
        resValues = true
    }
}

if (control3Coverage) {
    tasks.withType<Test>().configureEach {
        extensions.getByType<JacocoTaskExtension>().apply {
            isEnabled = name == "testDebugUnitTest"
            // Robolectric loads app classes without a regular CodeSource in the JVM sandbox.
            isIncludeNoLocationClasses = true
            includes = listOf("cl.duoc.louaraya.*")
        }
    }
    val logicClasses = fileTree(layout.buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes")) {
        include(
            "**/domain/usecase/**", "**/data/repository/**", "**/data/remote/**",
            "**/data/weather/**", "**/data/security/**", "**/data/mapper/**",
            "**/push/CampaignPush.class", "**/push/CampaignPush\$*.class", "**/push/CampaignPushKt.class",
            "**/push/CampaignRegistrationStore*.class",
            "**/presentation/**/*ViewModel*.class", "**/presentation/**/LocalNotificationCoordinator*.class"
        )
    }
    val execData = layout.buildDirectory.file("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")
    val reportLogic = tasks.register<JacocoReport>("reportControl3LogicCoverage") {
        group = "verification"
        dependsOn("testDebugUnitTest")
        classDirectories.setFrom(logicClasses)
        executionData.from(execData)
        sourceDirectories.from("src/main/java")
        reports.xml.required.set(true)
        reports.html.required.set(true)
        doFirst { check(!logicClasses.isEmpty) { "No se encontraron las clases de lógica Android." } }
    }
    tasks.register<JacocoCoverageVerification>("verifyControl3LogicCoverage") {
        group = "verification"
        dependsOn(reportLogic)
        classDirectories.setFrom(logicClasses)
        executionData.from(execData)
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    value = "COVEREDRATIO"
                    minimum = BigDecimal("0.80")
                }
            }
        }
    }
}

dependencies {
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.unified.push.connector)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode)
    implementation(libs.osmdroid.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.kotest.runner)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.bytebuddy.core)
    testRuntimeOnly(libs.bytebuddy.agent)
    testRuntimeOnly(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.junit.bom))
    androidTestImplementation(libs.junit.jupiter.api)
    androidTestImplementation(libs.androidx.compose.ui.test.android)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
