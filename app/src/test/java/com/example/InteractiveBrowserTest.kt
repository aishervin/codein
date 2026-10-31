package com.example

import com.example.ui.BROWSER_ACTION_END
import com.example.ui.BROWSER_ACTION_START
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
@Config(sdk = [36])
class InteractiveBrowserTest {
    @Test
    fun parsesAnInspectRequestWithoutASelector() {
        val request = parseBrowserActionRequest(
            "$BROWSER_ACTION_START{\"protocol\":\"codein.browser.v1\",\"action\":\"inspect\",\"reason\":\"Read the current page\"}$BROWSER_ACTION_END"
        )

        assertEquals("inspect", request?.action)
        assertEquals("Read the current page", request?.reason)
        assertFalse(request?.requiresApproval ?: true)
    }

    @Test
    fun clickAndFieldEntryRequireApproval() {
        val click = parseBrowserActionRequest(
            "$BROWSER_ACTION_START{\"protocol\":\"codein.browser.v1\",\"action\":\"click\",\"reason\":\"Open the menu\",\"selector\":\"button.menu\"}$BROWSER_ACTION_END"
        )
        val fill = parseBrowserActionRequest(
            "$BROWSER_ACTION_START{\"protocol\":\"codein.browser.v1\",\"action\":\"fill\",\"reason\":\"Enter the requested search\",\"selector\":\"input[name=q]\",\"value\":\"weather\"}$BROWSER_ACTION_END"
        )

        assertTrue(click?.requiresApproval == true)
        assertTrue(fill?.requiresApproval == true)
        assertEquals("weather", fill?.value)
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

        assertTrue(local?.requiresApproval == true)
        assertFalse(public?.requiresApproval ?: true)
        assertFalse(publicBareHost?.requiresApproval ?: true)
    }

    @Test
    fun rejectsInvalidActionsMissingReasonAndUnsafeUrls() {
        val missingReason = "$BROWSER_ACTION_START{\"protocol\":\"codein.browser.v1\",\"action\":\"inspect\"}$BROWSER_ACTION_END"
        val invalidAction = "$BROWSER_ACTION_START{\"protocol\":\"codein.browser.v1\",\"action\":\"execute_script\",\"reason\":\"test\"}$BROWSER_ACTION_END"
        val unsafeUrl = "$BROWSER_ACTION_START{\"protocol\":\"codein.browser.v1\",\"action\":\"navigate\",\"reason\":\"open\",\"url\":\"javascript:alert(1)\"}$BROWSER_ACTION_END"
        val multipleMarkers = "$BROWSER_ACTION_START{\"protocol\":\"codein.browser.v1\",\"action\":\"inspect\",\"reason\":\"read\"}$BROWSER_ACTION_END\n$BROWSER_ACTION_START{}$BROWSER_ACTION_END"

        assertNull(parseBrowserActionRequest(missingReason))
        assertNull(parseBrowserActionRequest(invalidAction))
        assertNull(parseBrowserActionRequest(unsafeUrl))
        assertNull(parseBrowserActionRequest(multipleMarkers))
        assertNull(normalizeBrowserUrl("https://user:password@example.com"))
        assertEquals("https://example.com", normalizeBrowserUrl("example.com"))
    }
}
