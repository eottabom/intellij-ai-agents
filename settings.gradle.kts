pluginManagement {
    repositories {
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies")
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "intellij-ai-agents"
include("app")
