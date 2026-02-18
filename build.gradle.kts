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
val mermaidResourcesDir = "src/main/resources/mermaid"
val mermaidTargetFile = file("$mermaidResourcesDir/mermaid.min.js")

/**
 * Task to download Mermaid.js 
 * Tries multiple sources: npm, CDN, or manual download
 */
tasks.register("downloadMermaid") {
    description = "Downloads Mermaid.js ${mermaidVersion}"
    group = "build setup"
    
    inputs.property("mermaidVersion", mermaidVersion)
    outputs.file(mermaidTargetFile)
    
    // Skip if file already exists and is not empty
    onlyIf { !mermaidTargetFile.exists() || mermaidTargetFile.length() == 0L }
    
    doLast {
        val targetDir = file(mermaidResourcesDir)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        
        println("Downloading Mermaid.js ${mermaidVersion}...")
        
        val tempDir = file("${buildDir}/tmp/mermaid-download")
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
                npmFile.copyTo(mermaidTargetFile, overwrite = true)
                val fileSizeMB = mermaidTargetFile.length() / (1024.0 * 1024.0)
                println("✓ Successfully downloaded Mermaid.js ${mermaidVersion} (%.2f MB)".format(fileSizeMB))
                println("  Saved to: ${mermaidTargetFile.absolutePath}")
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
                    mermaidTargetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                val fileSizeMB = mermaidTargetFile.length() / (1024.0 * 1024.0)
                println("✓ Successfully downloaded from CDN (%.2f MB)".format(fileSizeMB))
                
            } catch (cdnError: Exception) {
                throw Exception("""
                    Failed to download Mermaid.js from all sources:
                    - npm: ${npmError.message}
                    - CDN: ${cdnError.message}
                    
                    Manual download:
                    curl -L -o ${mermaidTargetFile.absolutePath} \
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
    // Ensure Mermaid is downloaded before processing resources
    processResources {
        dependsOn("downloadMermaid")
    }
    
    // Also ensure it's available for tests
    processTestResources {
        dependsOn("downloadMermaid")
    }
    
    test {
        // Using JUnit 4 with IntelliJ Platform test framework
        dependsOn("downloadMermaid")
    }
    
    // Clean task should also remove downloaded Mermaid
    clean {
        doLast {
            if (mermaidTargetFile.exists()) {
                mermaidTargetFile.delete()
                println("Removed downloaded Mermaid.js")
            }
        }
    }
}
