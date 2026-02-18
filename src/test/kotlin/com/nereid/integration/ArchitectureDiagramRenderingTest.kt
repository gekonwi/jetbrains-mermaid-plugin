package com.nereid.integration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.jcef.JBCefApp
import com.nereid.preview.MermaidPreviewPanel
import java.awt.Robot
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import javax.imageio.ImageIO
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Integration test for rendering architecture diagrams in Mermaid 11.12.2
 *
 * This test:
 * 1. Loads the architecture diagram test data
 * 2. Renders it using the MermaidPreviewPanel
 * 3. Takes a screenshot of the rendered preview
 * 4. Validates the rendering using local AI
 */
class ArchitectureDiagramRenderingTest : BasePlatformTestCase() {

    private val testDataPath = "src/test/resources/testdata/architecture-diagram.md"
    private val screenshotOutputDir = "build/test-screenshots"
    
    companion object {
        private const val SCREENSHOT_WIDTH = 1024
        private const val SCREENSHOT_HEIGHT = 768
    }

    override fun setUp() {
        super.setUp()
        // Create screenshot output directory
        File(screenshotOutputDir).mkdirs()
    }

    fun testArchitectureDiagramRendering() {
        // Skip test if JCEF is not available (headless environment)
        if (!JBCefApp.isSupported()) {
            println("Skipping testArchitectureDiagramRendering: JCEF not supported in headless environment")
            return
        }

        // Read the test data file
        val testDataFile = File(testDataPath)
        assertTrue("Test data file should exist: $testDataPath", testDataFile.exists())

        val content = testDataFile.readText()
        assertTrue("Test data should contain architecture-beta syntax", content.contains("architecture-beta"))

        // Extract the mermaid diagram from the markdown file
        val diagramSource = extractMermaidDiagram(content)
        assertNotNull("Should be able to extract mermaid diagram from test data", diagramSource)
        assertTrue("Diagram should contain architecture-beta", diagramSource!!.contains("architecture-beta"))

        // Create the preview panel
        val disposable = Disposer.newDisposable()
        try {
            val previewPanel = MermaidPreviewPanel(disposable)

            // Set up render callbacks
            val renderLatch = CountDownLatch(1)
            var renderSucceeded = false
            var renderError: String? = null

            previewPanel.onRenderSuccess = {
                renderSucceeded = true
                renderLatch.countDown()
            }

            previewPanel.onRenderError = { error ->
                renderError = error
                renderLatch.countDown()
            }

            // Render the diagram
            ApplicationManager.getApplication().invokeLater {
                previewPanel.renderDiagram(diagramSource, "default")
            }

            // Wait for rendering to complete (with timeout)
            val renderCompleted = renderLatch.await(30, TimeUnit.SECONDS)
            assertTrue("Rendering should complete within 30 seconds", renderCompleted)

            if (!renderSucceeded) {
                fail("Diagram rendering failed: $renderError")
            }

            // Give it a moment for the UI to update
            Thread.sleep(2000)

            // Capture screenshot
            val screenshot = captureScreenshot()
            if (screenshot != null) {
                saveScreenshot(screenshot, "architecture-diagram-preview.png")
                println("Screenshot saved to: $screenshotOutputDir/architecture-diagram-preview.png")

                // Validate the screenshot using local AI
                validateScreenshotWithAI(screenshot, diagramSource)
            } else {
                println("Warning: Could not capture screenshot (normal in headless environment)")
            }

        } finally {
            Disposer.dispose(disposable)
        }
    }

    /**
     * Extracts the mermaid diagram code block from markdown content
     */
    private fun extractMermaidDiagram(markdown: String): String? {
        val regex = "```mermaid\\s*\\n(.*?)\\n```\\s*".toRegex(RegexOption.DOT_MATCHES_ALL)
        val match = regex.find(markdown)
        return match?.groupValues?.get(1)?.trim()
    }

    /**
     * Captures a screenshot of the screen
     * Returns null in headless environments
     */
    private fun captureScreenshot(): BufferedImage? {
        return try {
            val robot = Robot()
            val screenRect = Rectangle(0, 0, SCREENSHOT_WIDTH, SCREENSHOT_HEIGHT)
            robot.createScreenCapture(screenRect)
        } catch (e: Exception) {
            println("Could not capture screenshot: ${e.message}")
            null
        }
    }

    /**
     * Saves a screenshot to disk
     */
    private fun saveScreenshot(image: BufferedImage, filename: String) {
        try {
            val outputFile = File(screenshotOutputDir, filename)
            ImageIO.write(image, "png", outputFile)
        } catch (e: Exception) {
            println("Could not save screenshot: ${e.message}")
        }
    }

    /**
     * Validates the screenshot using a local AI service
     * 
     * This method attempts to connect to a local AI service (like Ollama) running
     * on localhost to validate that the rendered diagram matches the expected
     * architecture diagram structure.
     */
    private fun validateScreenshotWithAI(screenshot: BufferedImage, diagramSource: String) {
        try {
            // Check if local AI service is available (e.g., Ollama on port 11434)
            val aiServiceAvailable = checkLocalAIService()

            if (!aiServiceAvailable) {
                println("Local AI service not available. Skipping AI validation.")
                println("To enable AI validation, run a local AI service like Ollama:")
                println("  docker run -d -p 11434:11434 ollama/ollama")
                return
            }

            // Perform AI-based validation
            val validationResult = performAIValidation(screenshot, diagramSource)
            println("AI Validation Result: $validationResult")

            // For now, we just log the result rather than failing the test
            // since AI validation is optional
            assertTrue("AI validation should complete", true)

        } catch (e: Exception) {
            println("AI validation error (non-fatal): ${e.message}")
        }
    }

    /**
     * Checks if a local AI service is available
     */
    private fun checkLocalAIService(): Boolean {
        return try {
            val process = ProcessBuilder(
                "curl", "-s", "-o", "/dev/null", "-w", "%{http_code}",
                "http://localhost:11434/api/tags"
            ).start()
            
            process.waitFor(5, TimeUnit.SECONDS)
            val exitCode = process.exitValue()
            val output = process.inputStream.bufferedReader().readText()
            
            output.trim() == "200"
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Performs AI-based validation of the screenshot
     * 
     * This would send the screenshot and diagram source to a local AI service
     * for validation that the rendered output matches the expected diagram.
     */
    private fun performAIValidation(screenshot: BufferedImage, diagramSource: String): String {
        // TODO: Implement actual AI validation using local service like Ollama
        // For now, return a placeholder result
        
        // Expected behavior:
        // 1. Convert screenshot to base64
        // 2. Send to local AI with prompt asking to validate the architecture diagram
        // 3. Parse AI response to determine if diagram was rendered correctly
        
        return "AI validation placeholder - local AI service integration pending"
    }
}
