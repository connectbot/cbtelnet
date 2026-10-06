import net.researchgate.release.ReleaseExtension

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.publish) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.metalava) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.spotless)
    alias(libs.plugins.release)
}

allprojects {
    group = rootProject.group
    version = rootProject.version
    repositories {
        google()
        mavenCentral()
    }
}

dependencies {
    kover(project(":telnetlib"))
    kover(project(":telnetlib-ktor"))
}

configure<ReleaseExtension> {
    tagTemplate.set("v\${version}")
    preTagCommitMessage.set("chore(release): ")
    tagCommitMessage.set("Release ")
    newVersionCommitMessage.set("chore(release): start ")
    buildTasks.set(listOf("verify"))
    git {
        requireBranch.set("^(main|release/[0-9]+\\.[0-9]+|release-work/.+)$")
        commitVersionFileOnly.set(true)
        if (providers.gradleProperty("release.noPush").isPresent) {
            pushToRemote.set(false)
        }
    }
}

spotless {
    kotlin {
        target("**/src/**/*.kt")
        ktlint("1.8.0")
        licenseHeaderFile("spotless/license-header.txt")
    }

    kotlinGradle {
        target("*.gradle.kts", "*/build.gradle.kts")
        ktlint("1.8.0")
    }

    yaml {
        target(".github/**/*.yml", ".github/**/*.yaml")
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("xml") {
        target("gradle/*.xml")
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("toml") {
        target("gradle/*.toml", "mise.toml")
        trimTrailingWhitespace()
        endWithNewline()
    }

    format("misc") {
        target("*.md", ".gitignore", ".gitattributes", ".editorconfig", "*.properties")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.register("verify") {
    group = "verification"
    description = "Runs the complete release gate without credentials or a Git remote."
    dependsOn("spotlessCheck", "koverXmlReport")
    subprojects.forEach {
        dependsOn("${it.path}:build", "${it.path}:metalavaCheckCompatibility", "${it.path}:checkApiSignature")
        dependsOn("${it.path}:dokkaGenerate", "${it.path}:koverXmlReport", "${it.path}:assemblePublication")
    }
}
