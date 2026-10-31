package com.example

import com.example.ui.CodeinUpdateCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CodeinUpdateCatalogTest {
    @Test
    fun listsOnlySmallPlainTextPackagesFromTheExpectedFolder() {
        val packages = CodeinUpdateCatalog.parseListing(
            """[
              {"name":"vpn-links.txt","type":"file","size":42,"download_url":"https://raw.githubusercontent.com/aishervin/codein/main/updates/vpn-links.txt"},
              {"name":"README.md","type":"file","size":42,"download_url":"https://raw.githubusercontent.com/aishervin/codein/main/updates/README.md"},
              {"name":"bad.txt","type":"symlink","size":4,"download_url":"https://raw.githubusercontent.com/aishervin/codein/main/updates/bad.txt"},
              {"name":"../unsafe.txt","type":"file","size":4,"download_url":"https://raw.githubusercontent.com/aishervin/codein/main/updates/../unsafe.txt"},
              {"name":"wrong-source.txt","type":"file","size":4,"download_url":"https://example.com/wrong-source.txt"},
              {"name":"large.txt","type":"file","size":999999,"download_url":"https://raw.githubusercontent.com/aishervin/codein/main/updates/large.txt"}
            ]"""
        )

        assertEquals(listOf("vpn-links.txt"), packages.map { it.fileName })
        assertEquals("vpn links", packages.single().title)
    }

    @Test
    fun reviewPromptRequiresExplanationAndConfirmationBeforeInstall() {
        val item = CodeinUpdateCatalog.parseListing(
            """[{"name":"sample-skill.txt","type":"file","size":10,"download_url":"https://raw.githubusercontent.com/aishervin/codein/main/updates/sample-skill.txt"}]"""
        ).single()
        val prompt = CodeinUpdateCatalog.reviewPrompt(item, "install this skill")

        assertTrue(prompt.contains("untrusted data"))
        assertTrue(prompt.contains("Do not install it"))
        assertTrue(prompt.contains("Package filename: sample-skill.txt"))
        assertTrue(prompt.contains("install this skill"))
    }
}
