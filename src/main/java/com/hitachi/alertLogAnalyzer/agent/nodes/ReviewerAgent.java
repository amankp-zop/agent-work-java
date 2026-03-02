package com.hitachi.alertLogAnalyzer.agent.nodes;

import com.hitachi.alertLogAnalyzer.agent.state.AlertAnalyzerState;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reviewer node that quality-gates the analyzer's output.
 * <p>
 * The reviewer examines the analysis summary and determines:
 * <ul>
 * <li><b>APPROVED</b> — the analysis is thorough and the verdict is
 * well-supported</li>
 * <li><b>NEEDS_REWORK</b> — the analysis is missing critical considerations and
 * should be reworked by the analyzer with specific feedback</li>
 * </ul>
 * <p>
 * If NEEDS_REWORK, specific feedback is written to state so the analyzer
 * can address the gaps on its next pass. A max-attempts guard prevents
 * infinite loops.
 */
@RequiredArgsConstructor
@Slf4j
public class ReviewerAgent implements NodeAction<AlertAnalyzerState> {

    private final ChatModel chatModel;
    private static final int MAX_REVIEW_ATTEMPTS = 3;

    private static final String SYSTEM_PROMPT = """
            You are a senior SRE review lead. Your job is to review alert analysis reports
            produced by a junior analyst and decide whether the analysis is thorough enough
            to act on, or whether it needs rework.

            You will receive:
            1. The original alert payload
            2. The analyst's structured analysis report (with verdict, severity, root cause, etc.)

            Your review criteria:
            - Did the analyst consider SERVICE HEALTH? (Is the service up/down/degraded?)
            - Did the analyst consider HISTORICAL PATTERNS? (Is this a recurring/flapping alert?)
            - Did the analyst consider INFRASTRUCTURE METRICS? (CPU, memory, disk, network?)
            - Is the ROOT CAUSE HYPOTHESIS reasonable and supported by data?
            - Is the VERDICT (ACTIONABLE/NON_ACTIONABLE) well-justified?
            - Are the RECOMMENDED ACTIONS specific and actionable?

            Respond in this EXACT format:

            REVIEW_DECISION: [APPROVED or NEEDS_REWORK]
            REVIEW_REASONING: [One paragraph explaining your decision]
            FEEDBACK_FOR_ANALYST: [If NEEDS_REWORK: specific instructions on what to investigate further.
                                   If APPROVED: write "N/A"]

            Be fair but rigorous. Approve if the analysis covers at least the key dimensions.
            Only request rework if critical considerations are missing.
            """;

    @Override
    public Map<String, Object> apply(AlertAnalyzerState state) {
        int currentAttempt = state.reviewAttempts() + 1;
        log.info("📋 [ReviewerAgent] Reviewing analysis (attempt #{})...", currentAttempt);

        String alertPayload = state.alertPayload();
        String analysisSummary = state.analysisSummary();

        // Build the review prompt
        String userPrompt = String.format("""
                Review the following alert analysis:

                === ORIGINAL ALERT PAYLOAD ===
                %s

                === ANALYST'S REPORT ===
                %s

                This is review attempt #%d of %d maximum.
                If this is attempt %d, you MUST approve (we cannot loop further).

                Provide your structured review now.
                """,
                alertPayload,
                analysisSummary,
                currentAttempt,
                MAX_REVIEW_ATTEMPTS,
                MAX_REVIEW_ATTEMPTS);

        // Call the LLM for review
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        messages.add(new UserMessage(userPrompt));

        ChatResponse response = chatModel.chat(messages);
        AiMessage aiMessage = response.aiMessage();
        String reviewResult = aiMessage.text();

        log.info("📋 [ReviewerAgent] Review complete.");

        // Parse decision
        String decision = parseDecision(reviewResult);
        String feedback = parseFeedback(reviewResult);

        // Force approval on max attempts
        if (currentAttempt >= MAX_REVIEW_ATTEMPTS && "NEEDS_REWORK".equals(decision)) {
            log.warn("📋 [ReviewerAgent] Max attempts reached ({}). Forcing APPROVED.", MAX_REVIEW_ATTEMPTS);
            decision = "APPROVED";
            feedback = "";
        }

        log.info("📋 [ReviewerAgent] Decision: {} (attempt #{})", decision, currentAttempt);

        String verdict = state.verdict();
        String routingKey = "APPROVED".equals(decision)
                ? ("ACTIONABLE".equals(verdict) ? "APPROVED_ACTIONABLE" : "APPROVED_NON_ACTIONABLE")
                : "NEEDS_REWORK";

        return Map.of(
                AlertAnalyzerState.MESSAGES,
                List.of("[Reviewer] 📋 Decision: " + decision + " | " + reviewResult),
                AlertAnalyzerState.REVIEWER_FEEDBACK, feedback,
                AlertAnalyzerState.REVIEW_ATTEMPTS, currentAttempt,
                AlertAnalyzerState.VERDICT, verdict + "|" + routingKey);
    }

    private String parseDecision(String reviewResult) {
        for (String line : reviewResult.split("\n")) {
            String trimmed = line.trim().toUpperCase();
            if (trimmed.startsWith("REVIEW_DECISION:")) {
                String decision = trimmed.replace("REVIEW_DECISION:", "").trim();
                if (decision.contains("APPROVED")) {
                    return "APPROVED";
                }
                return "NEEDS_REWORK";
            }
        }
        log.warn("Could not parse review decision, defaulting to APPROVED");
        return "APPROVED";
    }

    private String parseFeedback(String reviewResult) {
        boolean capture = false;
        StringBuilder feedback = new StringBuilder();
        for (String line : reviewResult.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.toUpperCase().startsWith("FEEDBACK_FOR_ANALYST:")) {
                String inline = trimmed.substring("FEEDBACK_FOR_ANALYST:".length()).trim();
                if (!inline.isEmpty() && !"N/A".equalsIgnoreCase(inline)) {
                    feedback.append(inline).append("\n");
                }
                capture = true;
                continue;
            }
            if (capture && !trimmed.isEmpty()) {
                feedback.append(trimmed).append("\n");
            }
        }
        return feedback.toString().trim();
    }
}
