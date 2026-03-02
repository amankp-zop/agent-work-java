package com.hitachi.alertLogAnalyzer.agent.nodes;

/**
 * LangChain4j AiService interface for the Alert Analyzer.
 * <p>
 * AiServices will create a proxy that:
 * 1. Sends the user message (alert payload) to the LLM with the system prompt
 * 2. Allows the LLM to autonomously decide which @Tool methods to invoke
 * 3. Loops internally until the LLM produces a final text response (no more
 * tool calls)
 * <p>
 * The tools are bound at build time via AiServices.builder(...).tools(...).
 */
public interface AlertAnalyzerAiService {

    /**
     * Analyze a Datadog alert payload. The LLM will decide which tools to call
     * (alert history, metrics, service health, pattern analysis) based on what
     * context it needs to make an ACTIONABLE / NON_ACTIONABLE verdict.
     *
     * @param alertContext The alert payload and any reviewer feedback, formatted as
     *                     a user message
     * @return The LLM's structured analysis response
     */
    String analyzeAlert(String alertContext);
}
