package com.example

import com.example.ui.BROWSER_ACTION_END
import com.example.ui.BROWSER_ACTION_START
import com.example.ui.browserElementExpression
import com.example.ui.browserFillScript
import com.example.ui.normalizeBrowserUrl
import com.example.ui.parseBrowserActionRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class InteractiveBrowserTest {
    @Test
    fun parsesAnInspectRequestWithoutASelector() {
        val request = parseBrowserActionRequest(
            "$BROWSER_ACTION_START{\"protocol\":\"codein.browser.v1\",\"action\":\"inspect\",\"reason\":\"Read the current page\"}$BROWSER_ACTION_END"
        )

        assertEquals("inspect", request.request?.action)
        assertEquals("Read the current page", request.request?.reason)
        assertFalse(request.request?.requiresApproval ?: true)
    }

    @Test
    fun acceptsReorderedFieldsAndIgnoresExtraFields() {
        val parsed = parseBrowserActionRequest(
            "$BROWSER_ACTION_START{\"extra\":{\"trace\":true,\"marker\":\"[[/CODEIN_BROWSER_REQUEST]]\"},\"reason\":\"Read the page\",\"action\":\"inspect\",\"protocol\":\"codein.browser.v1\"}$BROWSER_ACTION_END"
        )

        assertEquals("inspect", parsed.request?.action)
        assertNull(parsed.error)
    }

    @Test
    fun fillMayTargetTheFocusedEditableElement() {
        val parsed = parseBrowserActionRequest(
            "$BROWSER_ACTION_START{\"protocol\":\"codein.browser.v1\",\"action\":\"fill\",\"reason\":\"Enter the requested message\",\"value\":\"Hello\"}$BROWSER_ACTION_END"
        )

        assertEquals("fill", parsed.request?.action)
        assertEquals("", parsed.request?.selector)
        assertTrue(parsed.request?.requiresApproval == true)
        val script = browserFillScript("", "Hello", secret = false)
        assertTrue(script.contains("document.activeElement"))
        assertTrue(script.contains("e.isContentEditable"))
        assertTrue(script.contains("No focused element found"))
        val savedCredentialScript = browserFillScript("input[type=password]", "secret", secret = true)
        assertTrue(savedCredentialScript.contains("password"))
        assertFalse(savedCredentialScript.contains("e.isContentEditable"))
    }

    @Test
    fun inspectedElementIdsResolveToTheirTaggedPageElements() {
        assertEquals(
            "document.querySelector('[data-codein-owned-id=\\\"true\\\"][data-codein-id=\\\"el_5\\\"]')",
            browserElementExpression("el_5")
        )
        assertEquals(
            "document.querySelector(\"button.submit\")",
            browserElementExpression("button.submit")
        )
    }

    @Test
    fun clickAndFieldEntryRequireApproval() {
        val click = parseBrowserActionRequest(
            "$BROWSER_ACTION_START{\"protocol\":\"codein.browser.v1\",\"action\":\"click\",\"reason\":\"Open the menu\",\"selector\":\"button.menu\"}$BROWSER_ACTION_END"
        )
        val fill = parseBrowserActionRequest(
            "$BROWSER_ACTION_START{\"protocol\":\"codein.browser.v1\",\"action\":\"fill\",\"reason\":\"Enter the requested search\",\"selector\":\"input[name=q]\",\"value\":\"weather\"}$BROWSER_ACTION_END"
        )

        assertTrue(click.request?.requiresApproval == true)
        assertTrue(fill.request?.requiresApproval == true)
        assertEquals("weather", fill.request?.value)
    }

    @Test
    fun privateAddressNavigationNeedsApprovalAndPublicNavigationDoesNot() {
        val local = parseBrowserActionRequest(
            "$BROWSER_ACTION_START{\"protocol\":\"codein.browser.v1\",\"action\":\"navigate\",\"reason\":\"Open the local preview\",\"url\":\"http://127.0.0.1:3000\"}$BROWSER_ACTION_END"
        )
        val public = parseBrowserActionRequest(
            "$BROWSER_ACTION_START{\"protocol\":\"codein.browser.v1\",\"action\":\"navigate\",\"reason\":\"Open the public site\",\"url\":\"https://example.com\"}$BROWSER_ACTION_END"
        )
        val publicBareHost = parseBrowserActionRequest(
            "$BROWSER_ACTION_START{\"protocol\":\"codein.browser.v1\",\"action\":\"navigate\",\"reason\":\"Open the public site\",\"url\":\"example.com\"}$BROWSER_ACTION_END"
        )

        assertTrue(local.request?.requiresApproval == true)
        assertFalse(public.request?.requiresApproval ?: true)
        assertFalse(publicBareHost.request?.requiresApproval ?: true)
    }

    @Test
    fun rejectsInvalidActionsMissingReasonAndUnsafeUrls() {
        val missingReason = "$BROWSER_ACTION_START{\"protocol\":\"codein.browser.v1\",\"action\":\"inspect\"}$BROWSER_ACTION_END"
        val invalidAction = "$BROWSER_ACTION_START{\"protocol\":\"codein.browser.v1\",\"action\":\"execute_script\",\"reason\":\"test\"}$BROWSER_ACTION_END"
        val unsafeUrl = "$BROWSER_ACTION_START{\"protocol\":\"codein.browser.v1\",\"action\":\"navigate\",\"reason\":\"open\",\"url\":\"javascript:alert(1)\"}$BROWSER_ACTION_END"
        val clickWithoutSelector = "$BROWSER_ACTION_START{\"protocol\":\"codein.browser.v1\",\"action\":\"click\",\"reason\":\"click\"}$BROWSER_ACTION_END"
        val malformedJson = "$BROWSER_ACTION_START{\"protocol\":}$BROWSER_ACTION_END"
        val multipleMarkers = "$BROWSER_ACTION_START{\"protocol\":\"codein.browser.v1\",\"action\":\"inspect\",\"reason\":\"read\"}$BROWSER_ACTION_END\n$BROWSER_ACTION_START{}$BROWSER_ACTION_END"

        assertEquals("A non-empty reason is required.", parseBrowserActionRequest(missingReason).error)
        assertTrue(parseBrowserActionRequest(invalidAction).error.orEmpty().contains("Unsupported browser action"))
        assertTrue(parseBrowserActionRequest(unsafeUrl).error.orEmpty().contains("valid HTTP or HTTPS"))
        assertEquals("A CSS selector is required for click.", parseBrowserActionRequest(clickWithoutSelector).error)
        assertEquals("The browser request payload is not valid JSON.", parseBrowserActionRequest(malformedJson).error)
        assertTrue(parseBrowserActionRequest(multipleMarkers).error.orEmpty().contains("exactly one"))
        assertNull(normalizeBrowserUrl("https://user:password@example.com"))
        assertEquals("https://example.com", normalizeBrowserUrl("example.com"))
    }
}
