import net.researchgate.release.ReleaseExtension
import org.jetbrains.dokka.gradle.DokkaExtension
import org.jetbrains.dokka.gradle.engine.parameters.VisibilityModifier
import org.jetbrains.dokka.gradle.engine.plugins.DokkaHtmlPluginParameters
import org.jetbrains.dokka.gradle.formats.DokkaFormatPlugin
import org.jetbrains.dokka.gradle.internal.InternalDokkaGradlePluginApi

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.publish) apply false
    alias(libs.plugins.dokka)
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
    dependsOn("spotlessCheck", "koverXmlReport", "dokkaGenerate")
    subprojects.forEach {
        dependsOn("${it.path}:build", "${it.path}:metalavaCheckCompatibility", "${it.path}:checkApiSignature")
        dependsOn("${it.path}:dokkaGenerate", "${it.path}:koverXmlReport", "${it.path}:assemblePublication")
    }
}

// BEGIN SITE DOCUMENTATION
@OptIn(InternalDokkaGradlePluginApi::class)
abstract class DokkaMarkdownPlugin : DokkaFormatPlugin(formatName = "markdown") {
    override fun DokkaFormatPlugin.DokkaFormatPluginContext.configure() {
        project.dependencies {
            dokkaPlugin(dokka("gfm-plugin"))
            formatDependencies.dokkaPublicationPluginClasspathApiOnly.dependencies.addLater(
                dokka("gfm-template-processing-plugin"),
            )
        }
    }
}

dependencies {
    add("dokka", project(":telnetlib"))
    add("dokka", project(":telnetlib-ktor"))
}

allprojects {
    plugins.withId("org.jetbrains.dokka") {
        apply<DokkaMarkdownPlugin>()
        afterEvaluate {
            extensions.configure<DokkaExtension> {
                val docsVersion = providers.gradleProperty("docsVersion").orElse(project.version.toString())
                moduleVersion.set(docsVersion)
                if (project == rootProject) {
                    moduleName.set("ConnectBot Telnet")
                    dokkaPublications.configureEach {
                        includes.from(rootProject.file("README.md"))
                    }
                }
                dokkaSourceSets.configureEach {
                    documentedVisibilities.set(setOf(VisibilityModifier.Public))
                    perPackageOptions.configureEach {
                        documentedVisibilities.set(setOf(VisibilityModifier.Public))
                    }
                    sourceLinks.clear()
                    sourceLink {
                        localDirectory.set(project.file("src/main"))
                        val ref = providers.gradleProperty("docsSourceCommit").orElse("main").get()
                        remoteUrl.set(uri("https://github.com/connectbot/cbtelnet/blob/$ref/${project.name}/src/main"))
                        remoteLineSuffix.set("#L")
                    }
                }
                pluginsConfiguration.named<DokkaHtmlPluginParameters>("html") {
                    footerMessage.set("Copyright Kenny Root")
                    templatesDir.set(rootProject.file(".github/scripts/templates"))
                }
            }
        }
    }
}
// END SITE DOCUMENTATION
