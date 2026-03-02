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
 * JIRA Ticket Creator node. Takes the analysis summary and alert payload,
 * generates a well-structured JIRA ticket, and logs it.
 * 
 * Currently uses a SIMULATED JIRA API. In production, replace the
 * createJiraTicket() method with a real JIRA REST API call.
 */
@RequiredArgsConstructor
@Slf4j
public class JiraTicketCreatorAgent implements NodeAction<AlertAnalyzerState> {

    private final ChatModel chatModel;

    private static final String SYSTEM_PROMPT = """
            You are a JIRA ticket creation specialist. Given a Datadog alert analysis report,
            create a well-structured JIRA ticket. Output in this EXACT format:

            TICKET_SUMMARY: [Concise one-line summary for the JIRA ticket title]
            TICKET_PRIORITY: [Critical, High, Medium, Low]
            TICKET_TYPE: [Bug, Incident, Task]
            TICKET_LABELS: [comma-separated labels, e.g., alert,infrastructure,cpu]
            TICKET_DESCRIPTION:
            [Multi-line description including:
            - Alert Details (title, scope, type, query)
            - Analysis Summary
            - Root Cause Hypothesis
            - Recommended Actions
            - Impact Assessment
            ]

            Rules:
            - Summary should be actionable and specific (not generic)
            - Include all relevant technical details in description
            - Priority should match the analysis severity
            - Labels should be relevant for filtering
            """;

    @Override
    public Map<String, Object> apply(AlertAnalyzerState state) {
        log.info("📝 [JiraTicketCreatorAgent] Creating JIRA ticket for actionable alert...");

        String alertPayload = state.alertPayload();
        String analysisSummary = state.analysisSummary();

        // Build prompt with alert + analysis context
        String userPrompt = String.format("""
                Create a JIRA ticket for the following actionable alert:

                === ALERT PAYLOAD ===
                %s

                === ANALYSIS REPORT ===
                %s

                Generate the ticket now.
                """, alertPayload, analysisSummary);

        // Call LLM to generate structured ticket content
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage(SYSTEM_PROMPT));
        messages.add(new UserMessage(userPrompt));

        ChatResponse response = chatModel.chat(messages);
        AiMessage aiMessage = response.aiMessage();
        String ticketContent = aiMessage.text();

        // Simulate JIRA ticket creation
        String ticketKey = createJiraTicket(ticketContent);

        log.info("📝 [JiraTicketCreatorAgent] JIRA ticket created: {}", ticketKey);

        return Map.of(
                AlertAnalyzerState.MESSAGES, List.of("[JiraCreator] 📝 Created ticket: " + ticketKey),
                AlertAnalyzerState.JIRA_RESULT, ticketKey + "\n" + ticketContent);
    }

    /**
     * Simulated JIRA ticket creation.
     * In production, replace with actual JIRA REST API call:
     * POST https://your-domain.atlassian.net/rest/api/3/issue
     */
    private String createJiraTicket(String ticketContent) {
        // Parse ticket fields from LLM output
        String summary = extractTicketField(ticketContent, "TICKET_SUMMARY");
        String priority = extractTicketField(ticketContent, "TICKET_PRIORITY");
        String type = extractTicketField(ticketContent, "TICKET_TYPE");
        String labels = extractTicketField(ticketContent, "TICKET_LABELS");

        // Generate a simulated ticket key
        String ticketKey = "ALERT-" + System.currentTimeMillis() % 10000;

        log.info("══════════════════════════════════════════════════");
        log.info("🎫 SIMULATED JIRA TICKET CREATION");
        log.info("══════════════════════════════════════════════════");
        log.info("  Ticket Key:    {}", ticketKey);
        log.info("  Summary:       {}", summary);
        log.info("  Priority:      {}", priority);
        log.info("  Type:          {}", type);
        log.info("  Labels:        {}", labels);
        log.info("══════════════════════════════════════════════════");
        log.info("  Full content:\n{}", ticketContent);
        log.info("══════════════════════════════════════════════════");

        return ticketKey;
    }

    private String extractTicketField(String content, String fieldName) {
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(fieldName + ":")) {
                return trimmed.substring(fieldName.length() + 1).trim();
            }
        }
        return "N/A";
    }
}
