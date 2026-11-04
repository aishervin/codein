package com.example

import com.example.ui.CodeinModel
import com.example.ui.isBuiltInChatModel
import com.example.ui.modelFromStoredCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRoutingTest {
    @Test
    fun heroAndZeroAreTheOnlyProviderlessBuiltInChatModels() {
        val zero = modelFromStoredCatalog("zero", "ZERO", "")!!
        val hero = modelFromStoredCatalog("hero", "HERO", "")!!

        assertTrue(isBuiltInChatModel(zero))
        assertTrue(isBuiltInChatModel(hero))
        assertFalse(isBuiltInChatModel(CodeinModel("openai", "Pollinations", "pollinations")))
    }

    @Test
    fun retiredProviderEntriesAreDiscardedInsteadOfConvertedToBuiltIns() {
        assertNull(modelFromStoredCatalog("mimo-v2.6-flash-free", "OpenCode · MiMo", "opencode"))
        assertNull(modelFromStoredCatalog("mimo-v2.6-flash-free", "MiMo", ""))
    }

    @Test
    fun activeProviderEntriesKeepTheirProviderRoute() {
        val model = modelFromStoredCatalog("openai-fast", "Pollinations · openai-fast", "pollinations")!!

        assertEquals("pollinations", model.providerId)
        assertFalse(isBuiltInChatModel(model))
    }
}
