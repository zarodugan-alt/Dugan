package com.dugan.agent.domain.orchestrator

import com.dugan.agent.domain.model.AgentCommand
import com.dugan.agent.domain.model.AgentPhase

/**
 * Pure transition table for the agent loop.
 *
 * Deliberately free of Android, coroutines and I/O so the whole surface can be
 * unit tested -- an orchestrator bug that strands the agent in `Thinking` is the
 * most annoying failure mode this app can have.
 */
object AgentStateMachine {

    /** @return the phase after [command], or [from] when the command is a no-op. */
    fun transition(from: AgentPhase, command: AgentCommand): AgentPhase = when (command) {
        AgentCommand.Start -> when (from) {
            AgentPhase.Idle, AgentPhase.Error -> AgentPhase.Listening
            // Start while speaking is a barge-in restart: go straight to listening.
            AgentPhase.Speaking -> AgentPhase.Listening
            AgentPhase.Listening, AgentPhase.Thinking -> from
            AgentPhase.Paused -> AgentPhase.Listening
        }

        AgentCommand.Stop -> when (from) {
            AgentPhase.Error -> AgentPhase.Error
            else -> AgentPhase.Idle
        }

        AgentCommand.Pause -> when (from) {
            AgentPhase.Listening, AgentPhase.Thinking, AgentPhase.Speaking -> AgentPhase.Paused
            AgentPhase.Idle, AgentPhase.Error, AgentPhase.Paused -> from
        }

        AgentCommand.Continue -> when (from) {
            AgentPhase.Paused -> AgentPhase.Listening
            else -> from
        }

        AgentCommand.Reset -> AgentPhase.Idle

        AgentCommand.BargeIn -> when (from) {
            AgentPhase.Speaking -> AgentPhase.Listening
            else -> from
        }
    }

    /** Where a Pause should return to. Pausing while Paused keeps the original. */
    fun rememberPauseTarget(from: AgentPhase, remembered: AgentPhase): AgentPhase =
        if (from == AgentPhase.Paused) remembered else from

    /** Legal next phases, used by the debug overlay and the transition tests. */
    fun reachableFrom(phase: AgentPhase): Set<AgentPhase> =
        AgentCommand.entries.map { transition(phase, it) }.toSet()

    /** Phases in which the microphone is open. */
    fun capturesAudio(phase: AgentPhase): Boolean =
        phase == AgentPhase.Listening || phase == AgentPhase.Paused

    /** Phases in which we are waiting on a network response. */
    fun awaitsNetwork(phase: AgentPhase): Boolean =
        phase == AgentPhase.Thinking || phase == AgentPhase.Speaking
}
