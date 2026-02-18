import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import java.net.URL

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.21"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity(providers.gradleProperty("platformVersion").get())
        bundledPlugin("org.intellij.plugins.markdown")
        pluginVerifier()
        testFramework(TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = "233"
            untilBuild = provider { null }
        }
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            // Check if specific IDE is requested via command line for matrix builds
            val ideType = providers.gradleProperty("plugin.verifier.ide.type").orNull
            val ideVersion = providers.gradleProperty("plugin.verifier.ide.version").orNull

            if (ideType != null && ideVersion != null) {
                // Single IDE verification for matrix builds
                when (ideType) {
                    "IC" -> ide(IntelliJPlatformType.IntellijIdeaCommunity, ideVersion)
                    "PC" -> ide(IntelliJPlatformType.PyCharmCommunity, ideVersion)
                    "WS" -> ide(IntelliJPlatformType.WebStorm, ideVersion)
                }
            } else {
                // Full verification suite
                ide(IntelliJPlatformType.IntellijIdeaCommunity, "2023.3.8")    // 233 - minimum supported
                ide(IntelliJPlatformType.IntellijIdeaCommunity, "2024.1.7")    // 241
                ide(IntelliJPlatformType.IntellijIdeaCommunity, "2024.2.4")    // 242
                ide(IntelliJPlatformType.IntellijIdeaCommunity, "2024.3.1")    // 243 - latest stable

                // Also verify with other JetBrains IDEs to ensure cross-IDE compatibility
                ide(IntelliJPlatformType.PyCharmCommunity, "2024.3.1")
                ide(IntelliJPlatformType.WebStorm, "2024.3.1")
            }
        }
    }
}

// Mermaid.js dependency configuration
val mermaidVersion = "11.12.2"
val mermaidDownloadDir = layout.buildDirectory.dir("downloaded-resources/mermaid").get().asFile
val mermaidDownloadedFile = file("${mermaidDownloadDir}/mermaid.min.js")
val mermaidResourcesDir = "src/main/resources/mermaid"

/**
 * Task to download Mermaid.js 
 * Downloads to build/downloaded-resources instead of src/main/resources
 * Following Gradle best practices for build-time dependencies
 */
tasks.register("downloadMermaid") {
    description = "Downloads Mermaid.js ${mermaidVersion}"
    group = "build setup"
    
    inputs.property("mermaidVersion", mermaidVersion)
    outputs.file(mermaidDownloadedFile)
    
    // Skip if file already exists and is not empty
    onlyIf { !mermaidDownloadedFile.exists() || mermaidDownloadedFile.length() == 0L }
    
    doLast {
        if (!mermaidDownloadDir.exists()) {
            mermaidDownloadDir.mkdirs()
        }
        
        println("Downloading Mermaid.js ${mermaidVersion}...")
        
        val tempDir = layout.buildDirectory.dir("tmp/mermaid-download").get().asFile
        tempDir.mkdirs()
        
        try {
            // Try using npm to download (most reliable in restricted environments)
            println("Using npm to download Mermaid.js...")
            
            exec {
                workingDir = tempDir
                commandLine("npm", "install", "--no-save", "mermaid@${mermaidVersion}")
            }
            
            val npmFile = file("${tempDir}/node_modules/mermaid/dist/mermaid.min.js")
            if (npmFile.exists()) {
                npmFile.copyTo(mermaidDownloadedFile, overwrite = true)
                val fileSizeMB = mermaidDownloadedFile.length() / (1024.0 * 1024.0)
                println("✓ Successfully downloaded Mermaid.js ${mermaidVersion} (%.2f MB)".format(fileSizeMB))
                println("  Saved to: ${mermaidDownloadedFile.absolutePath}")
            } else {
                throw Exception("mermaid.min.js not found in npm package")
            }
            
        } catch (npmError: Exception) {
            println("⚠ npm download failed: ${npmError.message}")
            
            // Fallback: Try CDN download
            try {
                println("Trying CDN download...")
                val cdnUrl = "https://cdn.jsdelivr.net/npm/mermaid@${mermaidVersion}/dist/mermaid.min.js"
                
                URL(cdnUrl).openStream().use { input ->
                    mermaidDownloadedFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                val fileSizeMB = mermaidDownloadedFile.length() / (1024.0 * 1024.0)
                println("✓ Successfully downloaded from CDN (%.2f MB)".format(fileSizeMB))
                
            } catch (cdnError: Exception) {
                throw Exception("""
                    Failed to download Mermaid.js from all sources:
                    - npm: ${npmError.message}
                    - CDN: ${cdnError.message}
                    
                    Manual download:
                    curl -L -o ${mermaidDownloadedFile.absolutePath} \
                      https://cdn.jsdelivr.net/npm/mermaid@${mermaidVersion}/dist/mermaid.min.js
                """.trimIndent())
            }
        } finally {
            // Clean up temp directory
            tempDir.deleteRecursively()
        }
    }
}

tasks {
    // Copy downloaded Mermaid.js to resources during processResources
    processResources {
        dependsOn("downloadMermaid")
        
        // Copy the downloaded file to the resources output
        from(mermaidDownloadDir) {
            into("mermaid")
            include("mermaid.min.js")
        }
    }
    
    // Also ensure it's available for tests
    processTestResources {
        dependsOn("downloadMermaid")
    }
    
    test {
        // Using JUnit 4 with IntelliJ Platform test framework
        dependsOn("downloadMermaid")
        
        // Make the downloaded file available during tests
        systemProperty("mermaid.file.path", mermaidDownloadedFile.absolutePath)
    }
    
    // Clean task should remove downloaded files
    clean {
        doLast {
            if (mermaidDownloadDir.exists()) {
                mermaidDownloadDir.deleteRecursively()
                println("Removed downloaded Mermaid.js from build directory")
            }
        }
    }
}
