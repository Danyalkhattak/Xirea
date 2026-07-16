package com.dannyk.xirea.util

object ResponseNormalizer {
    fun cleanAssistantResponse(raw: String): String {
        var fullResponse = raw.trim()

        fullResponse = normalizeFencedCodeBlocks(fullResponse)

        val stopPatterns = listOf(
            "\nUser:", "\nuser:", "\nHuman:", "\nhuman:",
            "\nQ:", "\nQuestion:", "\nXirea:", "\nAssistant:",
            "\nSystem:", "\nsystem:"
        )
        for (pattern in stopPatterns) {
            val index = fullResponse.indexOf(pattern)
            if (index > 0) {
                fullResponse = fullResponse.substring(0, index).trim()
            }
        }

        return fullResponse
    }

    fun normalizeFencedCodeBlocks(text: String): String {
        val lines = text.split("\n")
        val out = StringBuilder()
        val fenceRegex = Regex("^```(\\S+)(\\s+)(.+)$")
        for ((idx, line) in lines.withIndex()) {
            val match = fenceRegex.find(line)
            if (match != null) {
                val lang = match.groupValues[1]
                val code = match.groupValues[3]
                out.append("```").append(lang).append("\n").append(code)
            } else {
                out.append(line)
            }
            if (idx != lines.lastIndex) out.append("\n")
        }
        return out.toString()
    }
}
