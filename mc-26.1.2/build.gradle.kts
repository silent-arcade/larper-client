plugins {
    alias(libs.plugins.fabric.loom)
}

base {
    archivesName = properties["archives_base_name"] as String
    version = libs.versions.mod.version.get()
    group = properties["maven_group"] as String
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
    mavenCentral()
    exclusiveContent {
        forRepository { maven { url = uri("https://babbaj.github.io/maven/") } }
        filter { includeGroup("dev.babbaj") }
    }
}

dependencies {
    // Fabric
    minecraft(libs.minecraft)
    implementation(libs.fabric.loader)

    // Meteor
    implementation(libs.meteor.client)
    // compile-only: Fabric API interfaces are injected into some Minecraft classes we call
    compileOnly(libs.fabric.api)
    runtimeOnly(libs.fabric.api) // dev runs only; not bundled
    // Baritone (LGPL-3.0), bundled inside the addon jar; it already carries nether-pathfinder
    runtimeOnly("meteordevelopment:baritone:26.1-SNAPSHOT")
    include("meteordevelopment:baritone:26.1-SNAPSHOT")
    runtimeOnly("dev.babbaj:nether-pathfinder:1.4.1") // dev runs don't unpack nested jars

    // MP3 decoding for custom hit sounds, bundled inside the addon jar
    implementation(libs.jlayer)
    include(libs.jlayer)
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to libs.versions.minecraft.get()
        )

        inputs.properties(propertyMap)

        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        inputs.property("archivesName", project.base.archivesName.get())

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 25
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
    }
}
