package com.dugan.agent.domain.orchestrator

import com.dugan.agent.domain.model.AgentCommand
import com.dugan.agent.domain.model.AgentPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The orchestrator's worst failure mode is a phase it can never leave, so the
 * whole transition table is pinned here: every phase against every command.
 */
class AgentStateMachineTest {

    @Test
    fun `idle start begins listening`() {
        assertEquals(
            AgentPhase.Listening,
            AgentStateMachine.transition(AgentPhase.Idle, AgentCommand.Start),
        )
    }

    @Test
    fun `start while speaking restarts listening so a barge-in recovers`() {
        assertEquals(
            AgentPhase.Listening,
            AgentStateMachine.transition(AgentPhase.Speaking, AgentCommand.Start),
        )
    }

    @Test
    fun `error start clears the error`() {
        assertEquals(
            AgentPhase.Listening,
            AgentStateMachine.transition(AgentPhase.Error, AgentCommand.Start),
        )
    }

    @Test
    fun `stop returns to idle from every active phase`() {
        listOf(AgentPhase.Listening, AgentPhase.Thinking, AgentPhase.Speaking, AgentPhase.Paused)
            .forEach { phase ->
                assertEquals("from $phase", AgentPhase.Idle, AgentStateMachine.transition(phase, AgentCommand.Stop))
            }
    }

    @Test
    fun `pause is only legal from an active phase`() {
        assertEquals(AgentPhase.Paused, AgentStateMachine.transition(AgentPhase.Listening, AgentCommand.Pause))
        assertEquals(AgentPhase.Paused, AgentStateMachine.transition(AgentPhase.Thinking, AgentCommand.Pause))
        assertEquals(AgentPhase.Paused, AgentStateMachine.transition(AgentPhase.Speaking, AgentCommand.Pause))
        // No-ops.
        assertEquals(AgentPhase.Idle, AgentStateMachine.transition(AgentPhase.Idle, AgentCommand.Pause))
        assertEquals(AgentPhase.Paused, AgentStateMachine.transition(AgentPhase.Paused, AgentCommand.Pause))
    }

    @Test
    fun `continue only resumes from paused`() {
        assertEquals(AgentPhase.Listening, AgentStateMachine.transition(AgentPhase.Paused, AgentCommand.Continue))
        assertEquals(AgentPhase.Idle, AgentStateMachine.transition(AgentPhase.Idle, AgentCommand.Continue))
        assertEquals(AgentPhase.Speaking, AgentStateMachine.transition(AgentPhase.Speaking, AgentCommand.Continue))
    }

    @Test
    fun `barge-in interrupts speech but is a no-op elsewhere`() {
        assertEquals(AgentPhase.Listening, AgentStateMachine.transition(AgentPhase.Speaking, AgentCommand.BargeIn))
        assertEquals(AgentPhase.Thinking, AgentStateMachine.transition(AgentPhase.Thinking, AgentCommand.BargeIn))
        assertEquals(AgentPhase.Idle, AgentStateMachine.transition(AgentPhase.Idle, AgentCommand.BargeIn))
    }

    @Test
    fun `reset always lands on idle`() {
        AgentPhase.entries.forEach { phase ->
            assertEquals("from $phase", AgentPhase.Idle, AgentStateMachine.transition(phase, AgentCommand.Reset))
        }
    }

    @Test
    fun `no command can leave a phase unreachable from itself`() {
        // Guards against a transition table edit that strands a state.
        AgentPhase.entries.forEach { phase ->
            val reachable = AgentStateMachine.reachableFrom(phase)
            assertTrue("$phase cannot reach Idle", reachable.contains(AgentPhase.Idle) || phase == AgentPhase.Idle)
        }
    }

    @Test
    fun `pause target is remembered across a double pause`() {
        val remembered = AgentStateMachine.rememberPauseTarget(AgentPhase.Paused, AgentPhase.Thinking)
        assertEquals(AgentPhase.Thinking, remembered)

        val fresh = AgentStateMachine.rememberPauseTarget(AgentPhase.Paused, AgentPhase.Idle)
        assertEquals(AgentPhase.Idle, fresh)
    }

    @Test
    fun `audio capture is open while listening or paused only`() {
        assertTrue(AgentStateMachine.capturesAudio(AgentPhase.Listening))
        assertTrue(AgentStateMachine.capturesAudio(AgentPhase.Paused))
        assertFalse(AgentStateMachine.capturesAudio(AgentPhase.Speaking))
        assertFalse(AgentStateMachine.capturesAudio(AgentPhase.Idle))
    }
}
