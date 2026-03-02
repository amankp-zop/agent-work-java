package com.hitachi.alertLogAnalyzer.agent.service;

import com.hitachi.alertLogAnalyzer.agent.state.AlertAnalyzerState;
import com.hitachi.alertLogAnalyzer.dto.DatadogAlertDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Bridge between Spring and the LangGraph4j compiled graph.
 * Receives a DatadogAlertDto, runs it through the agent graph,
 * and returns the analysis result.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertAgentService {

    private final CompiledGraph<AlertAnalyzerState> alertCompiledGraph;

    /**
     * Analyze a Datadog alert by running it through the agent graph.
     *
     * @param alertDto The Datadog alert payload
     * @return Analysis result containing verdict, summary, and optional JIRA ticket
     *         key
     */
    public AlertAnalysisResult analyzeAlert(DatadogAlertDto alertDto) {
        log.info("🚀 [AlertAgentService] Starting agent analysis for alert: '{}'", alertDto.getAlertTitle());

        // Serialize the DTO fields to a structured string for the LLM
        String alertPayload = serializeAlertPayload(alertDto);

        // Prepare initial state
        Map<String, Object> inputs = Map.of(
                AlertAnalyzerState.ALERT_PAYLOAD, alertPayload,
                AlertAnalyzerState.MESSAGES, List.of("New alert received: " + alertDto.getAlertTitle()));

        // Stream through the graph
        var results = alertCompiledGraph.stream(inputs);

        // Collect results
        StringBuilder fullTrace = new StringBuilder();
        String verdict = "";
        String analysisSummary = "";
        String jiraResult = "";

        for (var nodeOutput : results) {
            var state = nodeOutput.state();

            // Log each node execution
            var messages = state.messages();
            if (!messages.isEmpty()) {
                var lastMessage = messages.get(messages.size() - 1);
                fullTrace.append("📍 Node '").append(nodeOutput.node()).append("':\n");
                fullTrace.append("   ").append(lastMessage).append("\n\n");
            }

            // Capture final state values
            verdict = state.verdict();
            analysisSummary = state.analysisSummary();
            jiraResult = state.jiraResult();
        }

        log.info("✅ [AlertAgentService] Analysis complete. Verdict: {}", verdict);

        return new AlertAnalysisResult(verdict, analysisSummary, jiraResult, fullTrace.toString());
    }

    /**
     * Serialize the alert DTO to a structured string.
     */
    private String serializeAlertPayload(DatadogAlertDto dto) {
        return String.format("""
                {
                  "alertTitle": "%s",
                  "alertStatus": "%s",
                  "alertQuery": "%s",
                  "alertScope": "%s",
                  "alertTransition": "%s",
                  "alertType": "%s",
                  "dateEpochMs": %s,
                  "eventTitle": "%s",
                  "priority": "%s"
                }""",
                safe(dto.getAlertTitle()),
                safe(dto.getAlertStatus()),
                safe(dto.getAlertQuery()),
                safe(dto.getAlertScope()),
                safe(dto.getAlertTransition()),
                safe(dto.getAlertType()),
                dto.getDateEpochMs() != null ? dto.getDateEpochMs().toString() : "null",
                safe(dto.getEventTitle()),
                safe(dto.getPriority()));
    }

    private String safe(String value) {
        return value != null ? value.replace("\"", "\\\"") : "";
    }

    /**
     * Result record for the alert analysis.
     */
    public record AlertAnalysisResult(
            String verdict,
            String analysisSummary,
            String jiraTicketKey,
            String trace) {
    }
}
