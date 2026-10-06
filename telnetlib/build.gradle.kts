import com.vanniktech.maven.publish.DeploymentValidation
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.publish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.metalava)
    alias(libs.plugins.kover)
    `java-library`
    signing
}

java {
    withSourcesJar()
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    api(libs.coroutines.core)
    testImplementation(kotlin("test"))
    testImplementation(libs.coroutines.test)
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    testRuntimeOnly(libs.junit.launcher)
}

val testJdkVersion = providers.gradleProperty("jdkVersion").map(String::toInt).orElse(17)

tasks.test {
    useJUnitPlatform()
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(testJdkVersion.get()))
        },
    )
}

metalava {
    javaSourceLevel.set(JavaVersion.VERSION_17)
}

// Use the pinned plugin's default output without referencing its internal task class.
val currentApi = layout.buildDirectory.file("metalava/current.txt")
tasks.named("metalavaCheckCompatibility") {
    doFirst {
        currentApi
            .get()
            .asFile.parentFile
            .mkdirs()
    }
}

tasks.register("checkApiSignature") {
    group = "verification"
    dependsOn("metalavaCheckCompatibility")
    inputs.file("api.txt")
    inputs.file(currentApi)
    doLast {
        check(file("api.txt").readText() == currentApi.get().asFile.readText()) {
            "Public API changed. Review it, then run :${project.name}:metalavaGenerateSignature."
        }
    }
}

val gitHubUrl = "https://github.com/connectbot/cbtelnet"

dokka {
    moduleName.set("ConnectBot Telnet Library")
    dokkaSourceSets.configureEach {
        sourceLink {
            localDirectory.set(layout.projectDirectory.asFile)
            remoteUrl.set(uri("$gitHubUrl/blob/main/${project.name}"))
            remoteLineSuffix.set("#L")
        }
    }
    pluginsConfiguration {
        html.footerMessage.set("Copyright Kenny Root")
    }
}

mavenPublishing {
    configure(KotlinJvm(javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml")))
    publishToMavenCentral(automaticRelease = true, validateDeployment = DeploymentValidation.PUBLISHED)
    signAllPublications()
    coordinates("org.connectbot.telnetlib", project.name, project.version.toString())
    pom {
        name.set(project.name)
        description.set("Streaming Telnet client engine and coroutine session")
        inceptionYear.set("2026")
        url.set(gitHubUrl)
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("kruton")
                name.set("Kenny Root")
                url.set("https://github.com/kruton")
            }
        }
        scm {
            connection.set("scm:git:$gitHubUrl.git")
            developerConnection.set("scm:git:ssh://git@github.com/connectbot/cbtelnet.git")
            url.set(gitHubUrl)
        }
    }
}

signing { isRequired = providers.gradleProperty("signingInMemoryKey").isPresent }
publishing {
    repositories {
        maven {
            name = "Build"
            url =
                rootProject.layout.buildDirectory
                    .dir("repository")
                    .get()
                    .asFile
                    .toURI()
        }
    }
}

tasks.register("assemblePublication") {
    group = "verification"
    description = "Assembles an unsigned Maven repository under build/repository without credentials."
    dependsOn("publishAllPublicationsToBuildRepository")
}

val validatePublicationCredentials =
    tasks.register("validatePublicationCredentials") {
        doLast {
            listOf("mavenCentralUsername", "mavenCentralPassword", "signingInMemoryKey", "signingInMemoryKeyPassword").forEach {
                require(!providers.gradleProperty(it).orNull.isNullOrBlank()) { "Missing publication credential: $it" }
            }
        }
    }

tasks.matching { it.name.contains("MavenCentral") }.configureEach {
    dependsOn(validatePublicationCredentials)
}
