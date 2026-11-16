package com.example.data

import com.example.data.ai.JarvisActionProposal
import com.example.data.ai.JarvisAiRepository
import com.example.data.tools.JarvisToolsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class JarvisToolsAndAiUnitTest {

    @Test
    fun mathParser_evaluatesComplexExpressions() {
        val repo = JarvisToolsRepository()
        assertEquals(87.0, repo.evaluateMath("sqrt(144) + 25 * 3").getOrThrow(), 1e-6)
        assertEquals(16.0, repo.evaluateMath("2^4").getOrThrow(), 1e-6)
        assertEquals(1.0, repo.evaluateMath("sin(90)").getOrThrow(), 1e-6)
    }

    @Test
    fun aiActionParser_extractsStructuredProposalsAndCleansText() {
        val ai = JarvisAiRepository()
        val raw = """
            Certainly, Sir. I have prepared that task for your schedule.
            [[JARVIS_ACTION:{"type":"create_task","title":"Review diagnostics","priority":"high","dueLabel":"Tonight"}]]
        """.trimIndent()

        val parsed = ai.parseActionsFromResponse(raw)
        assertEquals("Certainly, Sir. I have prepared that task for your schedule.", parsed.displayText)
        assertEquals(1, parsed.proposedActions.size)
        val proposal = parsed.proposedActions.first()
        assertTrue(proposal is JarvisActionProposal.CreateTask)
        val task = proposal as JarvisActionProposal.CreateTask
        assertEquals("Review diagnostics", task.title)
        assertEquals("high", task.priority)
    }
}
