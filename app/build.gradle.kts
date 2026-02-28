plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

group = "io.github.eottabom.aiagents"
version = "0.0.1"

val npmCmd = if (System.getProperty("os.name").lowercase().contains("win")) "npm.cmd" else "npm"
val runIdeSandboxConfigDir = layout.buildDirectory.dir("idea-sandbox/IC-2025.1/config")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation(libs.gson)
    implementation(libs.slf4j.api)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)

    intellijPlatform {
        intellijIdeaCommunity("2025.1")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
            untilBuild = "251.*"
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// React 빌드 후 리소스로 복사
tasks.register<Exec>("installWebviewDeps") {
    workingDir("frontend")
    commandLine(npmCmd, "ci")
    inputs.files("frontend/package.json", "frontend/package-lock.json")
    outputs.dir("frontend/node_modules")
}

tasks.register<Exec>("buildWebview") {
    dependsOn("installWebviewDeps")
    workingDir("frontend")
    commandLine(npmCmd, "run", "build")
}

tasks.register<Sync>("copyWebview") {
    dependsOn("buildWebview")
    from("frontend/dist")
    into("src/main/resources/webview")
}

tasks.named("processResources") { dependsOn("copyWebview") }

tasks.test {
    useJUnitPlatform()
}

tasks.register("configureRunIdeSandbox") {
    doLast {
        val configDir = runIdeSandboxConfigDir.get().asFile
        configDir.mkdirs()
        val disabledPlugins = listOf(
            "com.jetbrains.codeWithMe",
            "org.jetbrains.kotlin",
            "org.jetbrains.plugins.kotlin.jupyter",
            "intellij.jupyter",
            "intellij.jupyter.plugin.frontend",
            "com.intellij.notebooks.core",
            "org.jetbrains.completion.full.line",
        )
        configDir.resolve("disabled_plugins.txt")
            .writeText(disabledPlugins.joinToString(separator = System.lineSeparator(), postfix = System.lineSeparator()))
    }
}

tasks.named("runIde") {
    dependsOn("configureRunIdeSandbox")
    doFirst {
        val configDir = runIdeSandboxConfigDir.get().asFile
        configDir.mkdirs()
        configDir.resolve("disabled_plugins.txt").writeText(
            listOf(
                "com.jetbrains.codeWithMe",
                "org.jetbrains.kotlin",
                "org.jetbrains.plugins.kotlin.jupyter",
                "intellij.jupyter",
                "intellij.jupyter.plugin.frontend",
                "com.intellij.notebooks.core",
                "org.jetbrains.completion.full.line",
            ).joinToString(separator = System.lineSeparator(), postfix = System.lineSeparator())
        )
    }
}
