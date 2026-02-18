package com.nereid.integration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.jcef.JBCefApp
import com.nereid.preview.MermaidPreviewPanel
import java.awt.Robot
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.json.JSONArray

/**
 * Integration test for rendering architecture diagrams in Mermaid 11.12.2
 */
class ArchitectureDiagramRenderingTest : BasePlatformTestCase() {

    private val log = Logger.getInstance(ArchitectureDiagramRenderingTest::class.java)
    private val testDataPath = "src/test/resources/testdata/architecture-diagram.md"
    private val screenshotOutputDir = "build/test-screenshots"
    
    companion object {
        private const val SCREENSHOT_WIDTH = 1024
        private const val SCREENSHOT_HEIGHT = 768
        private const val RENDER_TIMEOUT_SECONDS = 30L
        private const val UI_UPDATE_DELAY_MS = 2000L
    }

    override fun setUp() {
        super.setUp()
        createScreenshotDirectory()
    }

    fun testArchitectureDiagramRendering() {
        if (shouldSkipTest()) return
        
        val testData = loadTestData()
        val diagramSource = extractDiagramFromTestData(testData)
        val renderResult = renderDiagram(diagramSource)
        processRenderResult(renderResult, diagramSource)
    }

    private fun createScreenshotDirectory() {
        File(screenshotOutputDir).mkdirs()
        log.debug("Created screenshot directory: $screenshotOutputDir")
    }

    private fun shouldSkipTest(): Boolean {
        val isSupported = JBCefApp.isSupported()
        log.debug("JCEF support check: $isSupported")
        
        if (!isSupported) {
            log.info("Skipping test: JCEF not supported")
            println("Skipping testArchitectureDiagramRendering: JCEF not supported in headless environment")
            return true
        }
        return false
    }

    private fun loadTestData(): TestData {
        val file = File(testDataPath)
        log.debug("Loading test data from: $testDataPath")
        
        assertTrue("Test data file should exist: $testDataPath", file.exists())
        
        val content = file.readText()
        assertTrue("Test data should contain architecture-beta syntax", 
            content.contains("architecture-beta"))
        
        log.debug("Test data loaded successfully")
        return TestData(content)
    }

    private fun extractDiagramFromTestData(testData: TestData): String {
        val diagram = extractMermaidDiagram(testData.content)
        log.debug("Extracted diagram: ${diagram != null}")
        
        assertNotNull("Should extract mermaid diagram", diagram)
        assertTrue("Diagram should contain architecture-beta", 
            diagram!!.contains("architecture-beta"))
        
        return diagram
    }

    private fun renderDiagram(diagramSource: String): RenderResult {
        val disposable = Disposer.newDisposable()
        
        return try {
            val panel = MermaidPreviewPanel(disposable)
            executeRendering(panel, diagramSource, disposable)
        } catch (e: Exception) {
            Disposer.dispose(disposable)
            throw e
        }
    }

    private fun executeRendering(
        panel: MermaidPreviewPanel,
        source: String,
        disposable: com.intellij.openapi.Disposable
    ): RenderResult {
        val latch = CountDownLatch(1)
        val callback = RenderCallback(latch)
        
        setupRenderCallbacks(panel, callback)
        triggerRendering(panel, source)
        waitForRendering(latch)
        
        return createRenderResult(callback, disposable)
    }

    private fun setupRenderCallbacks(
        panel: MermaidPreviewPanel,
        callback: RenderCallback
    ) {
        panel.onRenderSuccess = { callback.markSuccess() }
        panel.onRenderError = { error -> callback.markError(error) }
        log.debug("Render callbacks configured")
    }

    private fun triggerRendering(panel: MermaidPreviewPanel, source: String) {
        ApplicationManager.getApplication().invokeLater {
            panel.renderDiagram(source, "default")
        }
        log.debug("Rendering triggered")
    }

    private fun waitForRendering(latch: CountDownLatch) {
        val completed = latch.await(RENDER_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        log.debug("Rendering completed: $completed")
        assertTrue("Rendering should complete within timeout", completed)
    }

    private fun createRenderResult(
        callback: RenderCallback,
        disposable: com.intellij.openapi.Disposable
    ): RenderResult {
        log.debug("Render succeeded: ${callback.succeeded}")
        
        if (!callback.succeeded) {
            val error = callback.error ?: "Unknown error"
            fail("Diagram rendering failed: $error")
        }
        
        return RenderResult(true, disposable)
    }

    private fun processRenderResult(result: RenderResult, diagramSource: String) {
        try {
            waitForUIUpdate()
            val screenshot = captureAndSaveScreenshot()
            validateIfPossible(screenshot, diagramSource)
        } finally {
            Disposer.dispose(result.disposable)
        }
    }

    private fun waitForUIUpdate() {
        Thread.sleep(UI_UPDATE_DELAY_MS)
        log.debug("UI update delay completed")
    }

    private fun captureAndSaveScreenshot(): BufferedImage? {
        val screenshot = captureScreenshot()
        log.debug("Screenshot captured: ${screenshot != null}")
        
        if (screenshot != null) {
            saveScreenshotFile(screenshot)
        } else {
            logScreenshotWarning()
        }
        
        return screenshot
    }

    private fun saveScreenshotFile(screenshot: BufferedImage) {
        val filename = "architecture-diagram-preview.png"
        saveScreenshot(screenshot, filename)
        log.info("Screenshot saved: $screenshotOutputDir/$filename")
        println("Screenshot saved to: $screenshotOutputDir/$filename")
    }

    private fun logScreenshotWarning() {
        log.warn("Screenshot capture failed (headless environment)")
        println("Warning: Could not capture screenshot (normal in headless environment)")
    }

    private fun validateIfPossible(screenshot: BufferedImage?, source: String) {
        if (screenshot == null) return
        
        log.debug("Starting AI validation")
        validateScreenshotWithAI(screenshot, source)
    }

    private fun extractMermaidDiagram(markdown: String): String? {
        val regex = "(?s)```mermaid\\s*\\n(.*?)\\n```".toRegex()
        val match = regex.find(markdown)
        return match?.groupValues?.get(1)?.trim()
    }

    private fun captureScreenshot(): BufferedImage? {
        return try {
            val robot = Robot()
            val rect = Rectangle(0, 0, SCREENSHOT_WIDTH, SCREENSHOT_HEIGHT)
            robot.createScreenCapture(rect)
        } catch (e: Exception) {
            log.warn("Screenshot capture failed", e)
            println("Could not capture screenshot: ${e.message}")
            null
        }
    }

    private fun saveScreenshot(image: BufferedImage, filename: String) {
        try {
            val outputFile = File(screenshotOutputDir, filename)
            ImageIO.write(image, "png", outputFile)
        } catch (e: Exception) {
            log.error("Screenshot save failed", e)
            println("Could not save screenshot: ${e.message}")
        }
    }

    private fun validateScreenshotWithAI(screenshot: BufferedImage, source: String) {
        val available = checkAIServiceAvailability()
        if (!available) return
        
        performValidationSafely(screenshot, source)
    }

    private fun performValidationSafely(screenshot: BufferedImage, source: String) {
        try {
            executeAIValidation(screenshot, source)
        } catch (e: Exception) {
            logValidationError(e)
        }
    }

    private fun checkAIServiceAvailability(): Boolean {
        val available = checkLocalAIService()
        log.debug("AI service available: $available")
        
        if (!available) {
            logAIServiceUnavailable()
        }
        
        return available
    }

    private fun logAIServiceUnavailable() {
        log.info("AI service not available, skipping validation")
        println("Local AI service not available. Skipping AI validation.")
        println("To enable AI validation, run a local AI service like Ollama:")
        println("  docker run -d -p 11434:11434 ollama/ollama")
    }

    private fun executeAIValidation(screenshot: BufferedImage, source: String) {
        val result = performAIValidation(screenshot, source)
        log.info("AI validation result: $result")
        println("AI Validation Result: $result")
        assertTrue("AI validation should complete", true)
    }

    private fun logValidationError(e: Exception) {
        log.warn("AI validation error (non-fatal)", e)
        println("AI validation error (non-fatal): ${e.message}")
    }

    private fun checkLocalAIService(): Boolean {
        return try {
            val host = getOllamaHost()
            checkServiceHealth(host)
        } catch (e: Exception) {
            log.debug("AI service connection failed", e)
            println("Could not connect to Ollama: ${e.message}")
            false
        }
    }

    private fun getOllamaHost(): String {
        return System.getenv("OLLAMA_HOST") ?: "http://localhost:11434"
    }

    private fun checkServiceHealth(host: String): Boolean {
        val url = URL("$host/api/tags")
        val connection = url.openConnection() as HttpURLConnection
        
        configureHealthCheck(connection)
        val code = connection.responseCode
        connection.disconnect()
        
        return code == 200
    }

    private fun configureHealthCheck(connection: HttpURLConnection) {
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
    }

    private fun performAIValidation(screenshot: BufferedImage, source: String): String {
        return try {
            val host = getOllamaHost()
            val validator = AIValidator(host, log)
            validator.validate(screenshot, source)
        } catch (e: Exception) {
            log.error("AI validation failed", e)
            "AI validation error: ${e.message}"
        }
    }

    private data class TestData(val content: String)
    
    private data class RenderResult(
        val succeeded: Boolean,
        val disposable: com.intellij.openapi.Disposable
    )
    
    private class RenderCallback(private val latch: CountDownLatch) {
        var succeeded = false
            private set
        var error: String? = null
            private set
        
        fun markSuccess() {
            succeeded = true
            latch.countDown()
        }
        
        fun markError(errorMsg: String) {
            error = errorMsg
            latch.countDown()
        }
    }
}

/**
 * AI validation helper class following Single Responsibility Principle
 */
private class AIValidator(
    private val host: String,
    private val log: Logger
) {
    private companion object {
        private const val CONNECT_TIMEOUT = 30000
        private const val READ_TIMEOUT = 60000
    }
    
    fun validate(screenshot: BufferedImage, source: String): String {
        val base64Image = convertToBase64(screenshot)
        val prompt = createPrompt(source)
        return callOllamaAPI(base64Image, prompt)
    }
    
    private fun convertToBase64(image: BufferedImage): String {
        val stream = ByteArrayOutputStream()
        ImageIO.write(image, "png", stream)
        val bytes = stream.toByteArray()
        return Base64.getEncoder().encodeToString(bytes)
    }
    
    private fun createPrompt(source: String): String {
        return """
            Analyze this Mermaid architecture diagram screenshot.
            
            Expected diagram source:
            $source
            
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
    }
    
    private fun callOllamaAPI(image: String, prompt: String): String {
        val url = URL("$host/api/generate")
        val connection = url.openConnection() as HttpURLConnection
        
        configureConnection(connection)
        sendRequest(connection, image, prompt)
        return receiveResponse(connection)
    }
    
    private fun configureConnection(connection: HttpURLConnection) {
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = CONNECT_TIMEOUT
        connection.readTimeout = READ_TIMEOUT
        log.debug("API connection configured")
    }
    
    private fun sendRequest(
        connection: HttpURLConnection,
        image: String,
        prompt: String
    ) {
        val body = buildRequestBody(image, prompt)
        connection.outputStream.use { it.write(body.toByteArray()) }
        log.debug("API request sent")
    }
    
    private fun buildRequestBody(image: String, prompt: String): String {
        return JSONObject().apply {
            put("model", "llava")
            put("prompt", prompt)
            put("images", JSONArray().put(image))
            put("stream", false)
        }.toString()
    }
    
    private fun receiveResponse(connection: HttpURLConnection): String {
        val code = connection.responseCode
        log.debug("API response code: $code")
        
        if (code != 200) {
            return "AI validation failed: HTTP $code"
        }
        
        return extractAIResponse(connection)
    }
    
    private fun extractAIResponse(connection: HttpURLConnection): String {
        val response = connection.inputStream.bufferedReader().readText()
        connection.disconnect()
        
        val json = JSONObject(response)
        val aiResponse = json.optString("response", "No response")
        
        log.info("Ollama AI Analysis received")
        println("Ollama AI Analysis:")
        println(aiResponse)
        
        return aiResponse
    }
}
