package com.dannyk.xirea.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ResponseNormalizerTest {

    @Test
    fun normalizeFencedCodeBlocks_splitsFenceAndCode() {
        val input = "```html <html>"
        val expected = "```html\n<html>"
        assertEquals(expected, ResponseNormalizer.normalizeFencedCodeBlocks(input))
    }

    @Test
    fun cleanAssistantResponse_trimsRoleMarkersAtLineStarts() {
        val input = "Here is the answer.\nUser: ignore this"
        val expected = "Here is the answer."
        assertEquals(expected, ResponseNormalizer.cleanAssistantResponse(input))
    }
}
