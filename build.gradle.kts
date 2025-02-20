import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "2.1.10"
    id("fabric-loom") version "1.9-SNAPSHOT"
    id("maven-publish")
    id("com.diffplug.spotless") version "6.22.0"
}

// Apply the versioning script
apply(from = "versioning.gradle.kts")

// Retrieve the getCurrentVersion function from extra properties
val getCurrentVersion = extra["getCurrentVersion"] as () -> String

// Set the project version
version = getCurrentVersion()
group = project.property("maven_group") as String

base {
    archivesName.set(project.property("archives_base_name") as String)
}

val targetJavaVersion = 17
java {
    toolchain.languageVersion = JavaLanguageVersion.of(targetJavaVersion)
    // Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
    // if it is present.
    // If you remove this line, sources will not be generated.
    withSourcesJar()
}


repositories {
    maven {
        url = uri("https://jitpack.io")
        metadataSources {
            artifact() // Look directly for artifact
        }
    }
    mavenCentral()
}


dependencies {
    // To change the versions see the gradle.properties file
    minecraft("com.mojang:minecraft:${project.property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${project.property("yarn_mappings")}:v2")
    modImplementation("net.fabricmc:fabric-loader:${project.property("loader_version")}")
    modImplementation("net.fabricmc:fabric-language-kotlin:${project.property("kotlin_loader_version")}")

    modImplementation("net.fabricmc.fabric-api:fabric-api:${project.property("fabric_version")}")

    modImplementation("com.github.amblelabs:modkit:${project.property("modkit_version")}")
    include(modImplementation("com.github.DrTheodor:mc-scheduler:${project.property("scheduler_version")}")!!)
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("minecraft_version", project.property("minecraft_version"))
    inputs.property("loader_version", project.property("loader_version"))
    filteringCharset = "UTF-8"

    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "minecraft_version" to project.property("minecraft_version"),
            "loader_version" to project.property("loader_version"),
            "kotlin_loader_version" to project.property("kotlin_loader_version")
        )
    }
}

tasks.withType<JavaCompile>().configureEach {
    // ensure that the encoding is set to UTF-8, no matter what the system default is
    // this fixes some edge cases with special characters not displaying correctly
    // see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
    // If Javadoc is generated, this must be specified in that task too.
    options.encoding = "UTF-8"
    options.release.set(targetJavaVersion)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(JvmTarget.fromTarget(targetJavaVersion.toString()))
}

tasks.jar {
    from("LICENSE") {
        rename { "${it}_${project.base.archivesName}" }
    }
}

// configure the maven publication
publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            artifactId = project.property("archives_base_name") as String
            from(components["java"])
        }
    }

    // See https://docs.gradle.org/current/userguide/publishing_maven.html for information on how to set up publishing.
    repositories {
        // Add repositories to publish to here.
        // Notice: This block does NOT have the same function as the block in the top level.
        // The repositories here will be used for publishing your artifact, not for
        // retrieving dependencies.
    }
}

spotless {
    format("misc") {
        // Define the files to apply the 'misc' format to
        target("*.gradle", ".gitattributes", ".gitignore")
        trimTrailingWhitespace()
        indentWithSpaces()
        endWithNewline()
    }
    java {
        removeUnusedImports()
        // For importOrder, note that 'group' is now project.group (converted to a string)
        importOrder("java", "javax", "", "net.minecraft", project.group.toString())
        indentWithSpaces()
        trimTrailingWhitespace()
        formatAnnotations()
    }
    kotlin {
        // Specify the files relative to the project directory
        target(fileTree(".") {
            include("**/*.kt")
            exclude("**/.gradle/**")
        })
        ktlint().userData(mapOf("max_line_length" to "100", "insert_final_newline" to "true"))
        licenseHeaderFile("${rootDir}/gradle/spotless.kotlin.license")
        indentWithSpaces()
        trimTrailingWhitespace()
    }
}

tasks.named("spotlessKotlin") {
    dependsOn("spotlessJava")
}
tasks.named("spotlessMisc") {
    dependsOn("spotlessKotlin")
}