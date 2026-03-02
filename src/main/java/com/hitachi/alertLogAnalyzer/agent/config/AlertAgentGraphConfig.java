package com.hitachi.alertLogAnalyzer.agent.config;

import com.hitachi.alertLogAnalyzer.agent.nodes.AlertAnalyzerAgent;
import com.hitachi.alertLogAnalyzer.agent.nodes.AlertAnalyzerAiService;
import com.hitachi.alertLogAnalyzer.agent.nodes.JiraTicketCreatorAgent;
import com.hitachi.alertLogAnalyzer.agent.nodes.ResultLoggerAgent;
import com.hitachi.alertLogAnalyzer.agent.nodes.ReviewerAgent;
import com.hitachi.alertLogAnalyzer.agent.state.AlertAnalyzerState;
import com.hitachi.alertLogAnalyzer.agent.tools.AlertHistoryTool;
import com.hitachi.alertLogAnalyzer.agent.tools.AlertPatternTool;
import com.hitachi.alertLogAnalyzer.agent.tools.MetricCorrelationTool;
import com.hitachi.alertLogAnalyzer.agent.tools.ServiceHealthTool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * Spring Configuration that wires the Alert Analyzer agent graph.
 * <p>
 * Graph topology:
 * 
 * <pre>
 * START → analyzer → reviewer → conditional
 *  ├── "APPROVED_ACTIONABLE"     → jira_creator → END
 *  ├── "APPROVED_NON_ACTIONABLE" → result_logger → END
 *  └── "NEEDS_REWORK"            → analyzer  (loop back, max 3 iterations)
 * </pre>
 */
@Configuration
@Slf4j
public class AlertAgentGraphConfig {

        // ==================== LLM Bean ====================

        @Bean
        public ChatModel alertChatLanguageModel(
                        @Value("${groq.api.key}") String apiKey,
                        @Value("${groq.model}") String model,
                        @Value("${groq.base.url}") String baseUrl) {

                log.info("🤖 Initializing LLM: model={}, baseUrl={}", model, baseUrl);

                return OpenAiChatModel.builder()
                                .apiKey(apiKey)
                                .modelName(model)
                                .baseUrl(baseUrl)
                                .temperature(0.0)
                                .build();
        }

        // ==================== AiServices Bean (LLM + Tools) ====================

        @Bean
        public AlertAnalyzerAiService alertAnalyzerAiService(
                        ChatModel alertChatLanguageModel,
                        AlertHistoryTool alertHistoryTool,
                        MetricCorrelationTool metricCorrelationTool,
                        ServiceHealthTool serviceHealthTool,
                        AlertPatternTool alertPatternTool) {

                log.info("🔧 Building AlertAnalyzerAiService with tool bindings...");

                return AiServices.builder(AlertAnalyzerAiService.class)
                                .chatModel(alertChatLanguageModel)
                                .systemMessageProvider(
                                                chatMemoryId -> """
                                                                You are an expert Site Reliability Engineer (SRE) alert analyst. Your job is to analyze
                                                                Datadog monitoring alerts and determine whether they require human intervention (ACTIONABLE)
                                                                or can be safely ignored/auto-resolved (NON_ACTIONABLE).

                                                                You have access to the following tools — call whichever ones you need to gather
                                                                sufficient context. You do NOT need to call all tools for every alert. Be selective
                                                                based on what the alert payload tells you:

                                                                - Alert History Tool: check if this is a first-time or recurring/flapping alert
                                                                - Metric Correlation Tool: check CPU, memory, disk, network metrics
                                                                - Service Health Tool: check if the service is UP, DEGRADED, or DOWN
                                                                - Alert Pattern Tool: detect noise patterns, alert storms, oscillations

                                                                After gathering the context you need, provide your analysis in this EXACT format:

                                                                VERDICT: [ACTIONABLE or NON_ACTIONABLE]
                                                                SEVERITY: [CRITICAL, HIGH, MEDIUM, LOW]
                                                                ROOT_CAUSE_HYPOTHESIS: [Your best guess at the root cause]
                                                                CONFIDENCE: [HIGH, MEDIUM, LOW]
                                                                RECOMMENDED_ACTIONS:
                                                                - [Action 1]
                                                                - [Action 2]
                                                                - [Action 3]
                                                                ANALYSIS_SUMMARY: [A concise paragraph summarizing your analysis]

                                                                Decision guidelines:
                                                                - ACTIONABLE if: service is DOWN/DEGRADED, first-time alert, critical metrics breached,
                                                                  alert requires investigation, P1/P2 priority, no auto-resolution pattern.
                                                                - NON_ACTIONABLE if: flapping/oscillating alert, known noise pattern, metrics within
                                                                  normal range, service is healthy, alert is auto-resolving, low priority with no impact.

                                                                Be precise. Your verdict directly determines whether a JIRA ticket is created.
                                                                """)
                                .tools(alertHistoryTool, metricCorrelationTool, serviceHealthTool, alertPatternTool)
                                .build();
        }

        // ==================== Compiled Graph Bean ====================

        @Bean
        public CompiledGraph<AlertAnalyzerState> alertCompiledGraph(
                        ChatModel alertChatLanguageModel,
                        AlertAnalyzerAiService alertAnalyzerAiService) throws Exception {

                log.info("🔧 Building Alert Analyzer agent graph...");

                // Instantiate agent nodes
                var analyzerAgent = new AlertAnalyzerAgent(alertAnalyzerAiService);
                var reviewerAgent = new ReviewerAgent(alertChatLanguageModel);
                var jiraCreatorAgent = new JiraTicketCreatorAgent(alertChatLanguageModel);
                var resultLoggerAgent = new ResultLoggerAgent();

                // Build the state graph
                var graph = new StateGraph<>(AlertAnalyzerState.SCHEMA, AlertAnalyzerState::new)

                                // === NODES ===
                                .addNode("analyzer", node_async(analyzerAgent))
                                .addNode("reviewer", node_async(reviewerAgent))
                                .addNode("jira_creator", node_async(jiraCreatorAgent))
                                .addNode("result_logger", node_async(resultLoggerAgent))

                                // === EDGES ===

                                // Entry point: every alert starts at the analyzer
                                .addEdge(START, "analyzer")

                                // Analyzer always goes to reviewer for quality gating
                                .addEdge("analyzer", "reviewer")

                                // Reviewer → conditional routing based on review decision
                                .addConditionalEdges("reviewer",
                                                edge_async(state -> {
                                                        String verdict = state.verdict();
                                                        log.info("🔀 Routing based on reviewer verdict: {}", verdict);

                                                        if (verdict.contains("APPROVED_ACTIONABLE")) {
                                                                return "APPROVED_ACTIONABLE";
                                                        } else if (verdict.contains("APPROVED_NON_ACTIONABLE")) {
                                                                return "APPROVED_NON_ACTIONABLE";
                                                        } else {
                                                                return "NEEDS_REWORK";
                                                        }
                                                }),
                                                Map.of(
                                                                "APPROVED_ACTIONABLE", "jira_creator",
                                                                "APPROVED_NON_ACTIONABLE", "result_logger",
                                                                "NEEDS_REWORK", "analyzer"))

                                // Both terminal nodes go to END
                                .addEdge("jira_creator", END)
                                .addEdge("result_logger", END);

                log.info("✅ Alert Analyzer agent graph compiled successfully.");
                return graph.compile();
        }
}
