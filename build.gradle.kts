import com.github.gradle.node.task.NodeTask

plugins {
    java
    jacoco
    id("org.springframework.boot") version "4.1.1"
    id("com.github.node-gradle.node") version "7.1.0"
    id("com.diffplug.spotless") version "8.10.1"
}

group = "dev.riskexplorer"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.1"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.7.1.202607240634-r")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

node {
    version = "24.20.0"
    download = true
    npmInstallCommand = "ci"
    nodeProjectDir = file("frontend")
}

spotless {
    java {
        googleJavaFormat()
        removeUnusedImports()
    }
    format("buildFiles") {
        target("*.gradle.kts", "*.properties", ".gitattributes", ".gitignore")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
    options.encoding = "UTF-8"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports {
        xml.required = true
        html.required = true
    }
}

val frontendFormatCheck = tasks.register<NodeTask>("frontendFormatCheck") {
    dependsOn(tasks.npmInstall)
    script = file("frontend/node_modules/prettier/bin/prettier.cjs")
    args = listOf("--check", ".")
}

val frontendTypeCheck = tasks.register<NodeTask>("frontendTypeCheck") {
    dependsOn(tasks.npmInstall)
    script = file("frontend/node_modules/typescript/bin/tsc")
    args = listOf("--noEmit")
}

val frontendTest = tasks.register<NodeTask>("frontendTest") {
    dependsOn(tasks.npmInstall)
    script = file("frontend/node_modules/vitest/vitest.mjs")
    args = listOf("run")
}

val frontendCheck = tasks.register("frontendCheck") {
    dependsOn(frontendFormatCheck, frontendTypeCheck, frontendTest)
}

val frontendBuild = tasks.register<NodeTask>("frontendBuild") {
    dependsOn(frontendTypeCheck)
    script = file("frontend/node_modules/vite/bin/vite.js")
    args = listOf("build")
    inputs.files(fileTree("frontend/src"), file("frontend/index.html"), file("frontend/package.json"))
    outputs.dir("frontend/dist")
}

tasks.register<NodeTask>("frontendDev") {
    group = "application"
    description = "Starts the Vite development server on http://localhost:5173."
    dependsOn(tasks.npmInstall)
    script = file("frontend/node_modules/vite/bin/vite.js")
}

tasks.register<JavaExec>("createDemoRepository") {
    group = "application"
    description = "Creates the deterministic demonstration Git repository."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.riskexplorer.demo.DemoRepositoryGenerator"
    args = listOf(layout.projectDirectory.dir("demo-repository").asFile.absolutePath)
}

tasks.processResources {
    dependsOn(frontendBuild)
    from("frontend/dist") {
        into("static")
    }
}

tasks.check {
    dependsOn(frontendCheck, tasks.spotlessCheck)
}
