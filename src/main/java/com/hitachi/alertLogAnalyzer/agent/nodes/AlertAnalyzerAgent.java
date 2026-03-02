package com.hitachi.alertLogAnalyzer.agent.nodes;

import com.hitachi.alertLogAnalyzer.agent.state.AlertAnalyzerState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.List;
import java.util.Map;

/**
 * Core analysis node. Uses LangChain4j AiServices so that the LLM
 * autonomously decides which tools to call (alert history, metrics,
 * service health, pattern analysis) based on the alert context.
 * <p>
 * If the reviewer has sent feedback (from a previous iteration),
 * it is included in the prompt so the analyzer can address the concerns.
 */
@RequiredArgsConstructor
@Slf4j
public class AlertAnalyzerAgent implements NodeAction<AlertAnalyzerState> {

    private final AlertAnalyzerAiService aiService;

    @Override
    public Map<String, Object> apply(AlertAnalyzerState state) {
        log.info("🔬 [AlertAnalyzerAgent] Starting alert analysis (attempt #{})...",
                state.reviewAttempts() + 1);

        String alertPayload = state.alertPayload();
        String reviewerFeedback = state.reviewerFeedback();

        // Build the user prompt — include reviewer feedback if this is a re-analysis
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Analyze the following Datadog alert. ")
                .append("You have access to tools — use whichever tools you need to gather ")
                .append("enough context for a confident verdict. You do NOT need to call all tools; ")
                .append("choose based on what information would be most useful for this alert.\n\n");
        userPrompt.append("=== ALERT PAYLOAD ===\n").append(alertPayload).append("\n\n");

        if (reviewerFeedback != null && !reviewerFeedback.isEmpty()) {
            log.info("🔬 [AlertAnalyzerAgent] Re-analyzing with reviewer feedback.");
            userPrompt.append("=== REVIEWER FEEDBACK (address these concerns) ===\n")
                    .append(reviewerFeedback).append("\n\n");
        }

        userPrompt.append("Provide your structured analysis now.");

        // Call the AiServices proxy — the LLM will autonomously decide which tools to
        // invoke
        log.info("🔬 [AlertAnalyzerAgent] Sending to LLM (with tool access)...");
        String analysisResult = aiService.analyzeAlert(userPrompt.toString());

        log.info("🔬 [AlertAnalyzerAgent] Analysis complete.");

        // Parse verdict from LLM response
        String verdict = parseVerdict(analysisResult);
        log.info("🔬 [AlertAnalyzerAgent] Verdict: {}", verdict);

        return Map.of(
                AlertAnalyzerState.MESSAGES, List.of("[Analyzer] 🔬 " + analysisResult),
                AlertAnalyzerState.VERDICT, verdict,
                AlertAnalyzerState.ANALYSIS_SUMMARY, analysisResult);
    }

    /**
     * Parse the VERDICT line from the LLM's structured response.
     */
    private String parseVerdict(String analysisResult) {
        for (String line : analysisResult.split("\n")) {
            String trimmed = line.trim().toUpperCase();
            if (trimmed.startsWith("VERDICT:")) {
                String verdict = trimmed.replace("VERDICT:", "").trim();
                if (verdict.contains("ACTIONABLE") && !verdict.contains("NON")) {
                    return "ACTIONABLE";
                }
                return "NON_ACTIONABLE";
            }
        }
        // Default to actionable if we can't parse (safer to create a ticket than miss
        // one)
        log.warn("Could not parse verdict from LLM response, defaulting to ACTIONABLE");
        return "ACTIONABLE";
    }
}
