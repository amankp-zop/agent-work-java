package com.hitachi.alertLogAnalyzer.agent.tools;

import com.hitachi.alertLogAnalyzer.entity.AlertEntity;
import com.hitachi.alertLogAnalyzer.repository.AlertRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Tool: Query historical alerts from the database to detect patterns.
 * Helps the analyzer identify:
 * - Repeated/flapping alerts (noise)
 * - First-time alerts (potentially higher severity)
 * - Historical context for similar alerts
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlertHistoryTool {

    private final AlertRepository alertRepository;

    /**
     * Fetch recent alerts for a given scope (e.g., "host:prod-web-01").
     * Returns a formatted summary of past alerts.
     */
    @Tool("Fetch historical alerts for a given scope and type to detect recurring patterns, flapping, and first-time alerts. Use this when you need to understand if an alert is new or has been seen before.")
    public String getAlertHistory(
            @P("The scope of the alert, e.g. 'host:prod-web-01' or 'service:payment-api'") String alertScope,
            @P("The type of the alert, e.g. 'metric alert' or 'service check'") String alertType) {
        log.info("🔍 [AlertHistoryTool] Querying history for scope='{}', type='{}'", alertScope, alertType);

        try {
            // Find recent alerts by scope (last 7 days)
            List<AlertEntity> recentByScope = alertRepository
                    .findByAlertScopeAndEventDateAfterOrderByEventDateDesc(
                            alertScope,
                            LocalDateTime.now().minusDays(7));

            // Find recent alerts by type (last 24h)
            long countByType24h = alertRepository
                    .countByAlertTypeAndEventDateAfter(alertType, LocalDateTime.now().minusHours(24));

            // Find recent alerts by scope (last 24h) for frequency
            long countByScope24h = alertRepository
                    .countByAlertScopeAndEventDateAfter(alertScope, LocalDateTime.now().minusHours(24));

            StringBuilder result = new StringBuilder();
            result.append("=== Alert History Report ===\n");
            result.append(String.format("Scope: %s\n", alertScope));
            result.append(String.format("Alerts from same scope (last 24h): %d\n", countByScope24h));
            result.append(String.format("Alerts of same type (last 24h): %d\n", countByType24h));
            result.append(String.format("Alerts from same scope (last 7 days): %d\n", recentByScope.size()));

            if (!recentByScope.isEmpty()) {
                result.append("\nRecent alerts from this scope:\n");
                int count = 0;
                for (AlertEntity alert : recentByScope) {
                    if (count >= 5)
                        break; // Show max 5 recent alerts
                    result.append(String.format("  - [%s] %s | Status: %s | Priority: %s\n",
                            alert.getEventDate(),
                            alert.getAlertTitle(),
                            alert.getProcessingStatus(),
                            alert.getPriority()));
                    count++;
                }
            } else {
                result.append("\n⚠️ No previous alerts found for this scope — this is a FIRST-TIME alert.\n");
            }

            return result.toString();
        } catch (Exception e) {
            log.error("Error querying alert history", e);
            return "Alert history query failed: " + e.getMessage();
        }
    }
}
