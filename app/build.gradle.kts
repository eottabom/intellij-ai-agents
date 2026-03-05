import java.util.Locale

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.5.0"
}

abstract class ConfigureRunIdeSandboxTask : DefaultTask() {
    @get:OutputFile
    abstract val disabledPluginsFile: RegularFileProperty

    @get:Input
    abstract val disabledPluginIds: ListProperty<String>

    @TaskAction
    fun writeDisabledPlugins() {
        val outputFile = disabledPluginsFile.get().asFile
        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            disabledPluginIds.get().joinToString(
                separator = System.lineSeparator(),
                postfix = System.lineSeparator(),
            ),
        )
    }
}

group = "io.github.eottabom.aiagents"
version = "0.0.1"

val ideVersion = "2025.1"
val npmCmd: String = if (System.getProperty("os.name").lowercase(Locale.ROOT).contains("win")) {
    "npm.cmd"
} else {
    val candidates = listOf("/opt/homebrew/bin/npm", "/usr/local/bin/npm")
    candidates.firstOrNull { File(it).exists() } ?: "npm"
}
val runIdeSandboxConfigDir = layout.buildDirectory.dir("idea-sandbox/IC-$ideVersion/config")

val disabledPlugins = listOf(
    "com.jetbrains.codeWithMe",
    "org.jetbrains.kotlin",
    "org.jetbrains.plugins.kotlin.jupyter",
    "intellij.jupyter",
    "intellij.jupyter.plugin.frontend",
    "com.intellij.notebooks.core",
    "org.jetbrains.completion.full.line",
)

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
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit.platform.launcher)
    // IntelliJ test runtime initializes JUnit internals that still reference org.junit.runners.model.Statement.
    // Keep tests on JUnit5; add JUnit4 only as runtime compatibility for the IntelliJ test environment.
    testRuntimeOnly(libs.junit4)

    intellijPlatform {
        intellijIdeaCommunity(ideVersion)
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
    inputs.files("frontend/package.json", "frontend/package-lock.json")
    inputs.dir("frontend/src")
    inputs.file("frontend/index.html")
    inputs.file("frontend/vite.config.ts")
    inputs.file("frontend/tsconfig.json")
    outputs.dir("frontend/dist")
}

tasks.register<Sync>("copyWebview") {
    dependsOn("buildWebview")
    from("frontend/dist")
    into(layout.buildDirectory.dir("generated/webview-resources/webview"))
}

sourceSets.main {
    resources.srcDir(layout.buildDirectory.dir("generated/webview-resources"))
}

tasks.named("processResources") { dependsOn("copyWebview") }

tasks.test {
    useJUnitPlatform()
}

tasks.named("buildPlugin") { dependsOn("copyWebview") }

tasks.register<ConfigureRunIdeSandboxTask>("configureRunIdeSandbox") {
    disabledPluginsFile.set(runIdeSandboxConfigDir.map { it.file("disabled_plugins.txt") })
    disabledPluginIds.set(disabledPlugins)
}

tasks.named("runIde") {
    dependsOn("copyWebview")
    dependsOn("configureRunIdeSandbox")
}
