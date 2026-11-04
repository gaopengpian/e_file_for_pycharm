plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

import java.text.SimpleDateFormat
import java.util.Date

val buildTime = SimpleDateFormat("MMdd.HHmm").format(Date())
val pluginVersion = "1.2.$buildTime"

group = "com.github.antigravity"
version = pluginVersion

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.2.1")
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = "17"
    targetCompatibility = "17"
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

intellijPlatform {
    pluginConfiguration {
        // These will patch the plugin.xml
        ideaVersion {
            sinceBuild.set("231")
            untilBuild.set("261.*")
        }
    }
}

tasks.test {
    testLogging {
        showStandardStreams = true
    }
}

val copyJarToRoot by tasks.registering(Copy::class) {
    description = "Copies the generated plugin JAR to the project root"
    dependsOn("buildPlugin")
    doNotTrackState("Destination is project root which changes during build")
    from(tasks.named("composedJar").get().outputs.files)
    into(layout.projectDirectory)
    rename { "e_file_for_pycharm.jar" }
}

// 自动更新 PyCharm 插件
val updatePlugin by tasks.registering(Copy::class) {
    description = "Copies the plugin JAR to PyCharm plugins directory"
    dependsOn("copyJarToRoot")
    from(layout.projectDirectory.file("e_file_for_pycharm.jar"))
    into(file("${System.getProperty("user.home")}/AppData/Roaming/JetBrains/PyCharm2026.1/plugins"))
    rename { "e_file_for_pycharm.jar" }
}
