pluginManagement {
    repositories {
        mavenCentral {
            content {
                includeGroup("com.vanniktech.maven.publish")
                includeGroup("com.vanniktech")
            }
        }
        gradlePluginPortal {
            content {
                // Keep plugin marker POMs on the repository used by verification metadata.
                // The publishing plugin and its implementation resolve from Maven Central.
                excludeGroup("com.vanniktech.maven.publish")
                excludeGroup("com.vanniktech")
            }
        }
    }
}

rootProject.name = "cbtelnet"
include(":telnetlib", ":telnetlib-ktor")

enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
