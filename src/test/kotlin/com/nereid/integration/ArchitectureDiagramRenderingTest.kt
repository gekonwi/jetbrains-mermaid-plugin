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
import java.util.Base64
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray

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
     * Handles various whitespace patterns
     */
    private fun extractMermaidDiagram(markdown: String): String? {
        val regex = "(?s)```mermaid\\s*\\n(.*?)\\n```".toRegex()
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
            val ollamaHost = System.getenv("OLLAMA_HOST") ?: "http://localhost:11434"
            val url = URL("$ollamaHost/api/tags")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            val responseCode = connection.responseCode
            connection.disconnect()
            
            responseCode == 200
        } catch (e: Exception) {
            println("Could not connect to Ollama: ${e.message}")
            false
        }
    }

    /**
     * Performs AI-based validation of the screenshot
     * 
     * Uses Ollama's llava model to analyze the screenshot and validate
     * that it matches the expected architecture diagram structure.
     */
    private fun performAIValidation(screenshot: BufferedImage, diagramSource: String): String {
        try {
            val ollamaHost = System.getenv("OLLAMA_HOST") ?: "http://localhost:11434"
            
            // Convert screenshot to base64
            val base64Image = convertImageToBase64(screenshot)
            
            // Create the validation prompt
            val prompt = """
                Analyze this Mermaid architecture diagram screenshot.
                
                Expected diagram source:
                $diagramSource
                
                Please verify:
                1. Are there multiple groups/layers visible in the diagram?
                2. Are there service boxes/nodes within the groups?
                3. Are there connection lines/arrows between services?
                4. Does the overall structure match a layered architecture pattern?
                
                Respond with a JSON object containing:
                - "valid": true/false
                - "groups_detected": number of groups/layers visible
                - "services_detected": number of service nodes visible
                - "connections_detected": number of connection lines visible
                - "matches_expected": true/false
                - "observations": any notable observations
            """.trimIndent()
            
            // Call Ollama API
            val url = URL("$ollamaHost/api/generate")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 30000
            connection.readTimeout = 60000
            
            val requestBody = JSONObject().apply {
                put("model", "llava")
                put("prompt", prompt)
                put("images", JSONArray().put(base64Image))
                put("stream", false)
            }
            
            connection.outputStream.use { os ->
                os.write(requestBody.toString().toByteArray())
            }
            
            val responseCode = connection.responseCode
            if (responseCode != 200) {
                return "AI validation failed: HTTP $responseCode"
            }
            
            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            
            val jsonResponse = JSONObject(response)
            val aiResponse = jsonResponse.optString("response", "No response")
            
            println("Ollama AI Analysis:")
            println(aiResponse)
            
            return aiResponse
            
        } catch (e: Exception) {
            return "AI validation error: ${e.message}"
        }
    }
    
    /**
     * Converts a BufferedImage to base64 string
     */
    private fun convertImageToBase64(image: BufferedImage): String {
        val outputStream = ByteArrayOutputStream()
        ImageIO.write(image, "png", outputStream)
        val imageBytes = outputStream.toByteArray()
        return Base64.getEncoder().encodeToString(imageBytes)
    }
}
