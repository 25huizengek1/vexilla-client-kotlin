package me.huizengek.vexilla.client

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClientTests {
    private val client = VexillaClient(
        baseUrl = "http://localhost:8000",
        environmentName = "dev",
        customInstanceHash = "b7e91cc5-ec76-4ec3-9c1c-075032a13a1a",
        enableLogging = true
    )

    @Test
    fun testClient() = runTest {
        client.getManifest()

        // Run the test cases in parallel
        launch {
            // Gradual
            client.getFlags("Gradual")
            assertTrue(client.should("Gradual", "testingWorkingGradual"))
            assertFalse(client.should("Gradual", "testingNonWorkingGradual"))
        }

        launch {
            // Selective
            client.getFlags("Selective")
            assertTrue(client.should("Selective", "String", "shouldBeInList"))
            assertFalse(client.should("Selective", "String", "shouldNOTBeInList"))
        }

        launch {
            client.getFlags("Value")
            assertEquals("foo", client.value("Value", "String", ""))
            assertEquals(42, client.value("Value", "Integer", 0))
        }
    }
}
