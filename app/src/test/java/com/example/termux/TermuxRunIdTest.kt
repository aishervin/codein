package com.example.termux

import org.junit.Assert.assertEquals
import org.junit.Test

class TermuxRunIdTest {
    @Test
    fun matchesTheServerUuid5RunId() {
        assertEquals("dd6298230f8e58ec97d6356bc517186b", durableRunId("sample-key"))
    }
}
