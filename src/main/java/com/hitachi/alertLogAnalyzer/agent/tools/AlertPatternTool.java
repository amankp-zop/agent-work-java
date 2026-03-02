package com.hitachi.alertLogAnalyzer.agent.tools;

import com.hitachi.alertLogAnalyzer.entity.AlertEntity;
import com.hitachi.alertLogAnalyzer.repository.AlertRepository;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool: Detect alert patterns — frequency, recurring issues, noise detection.
 * 
 * Analyzes stored alerts to identify:
 * - Flapping alerts (triggered/resolved repeatedly in short windows)
 * - Alert storms (many alerts from same scope in short time)
 * - Time-based patterns (repeated at same time of day)
 * - Known noisy alert types that are usually non-actionable
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AlertPatternTool {

    private final AlertRepository alertRepository;

    /**
     * Analyze patterns for the given alert scope and type.
     *
     * @param alertScope The scope of the alert
     * @param alertType  The type of the alert
     * @param alertTitle The title of the alert
     * @return Pattern analysis report
     */
    @Tool("Analyze alert patterns including frequency, flapping detection, noise patterns, and alert storms. Use this to determine if an alert is recurring noise or a genuine new issue.")
    public String analyzePatterns(
            @P("The scope of the alert, e.g. 'host:prod-web-01'") String alertScope,
            @P("The type of the alert, e.g. 'metric alert'") String alertType,
            @P("The title of the alert") String alertTitle) {
        log.info("🔄 [AlertPatternTool] Analyzing patterns for scope='{}', type='{}', title='{}'",
                alertScope, alertType, alertTitle);

        try {
            LocalDateTime now = LocalDateTime.now();

            // Query alerts from last 24 hours for this scope
            List<AlertEntity> last24hAlerts = alertRepository
                    .findByAlertScopeAndEventDateAfterOrderByEventDateDesc(
                            alertScope, now.minusHours(24));

            // Query alerts from last 7 days for this type
            List<AlertEntity> last7dByType = alertRepository
                    .findByAlertTypeAndEventDateAfterOrderByEventDateDesc(
                            alertType, now.minusDays(7));

            // Frequency analysis
            long last1hCount = alertRepository.countByAlertScopeAndEventDateAfter(alertScope, now.minusHours(1));
            long last6hCount = alertRepository.countByAlertScopeAndEventDateAfter(alertScope, now.minusHours(6));
            long last24hCount = last24hAlerts.size();

            // Title similarity — check for repeated exact-same alerts
            long sameTitle24h = last24hAlerts.stream()
                    .filter(a -> alertTitle.equals(a.getAlertTitle()))
                    .count();

            // Transition analysis — count how many are Triggered vs Resolved
            Map<String, Long> transitionCounts = new HashMap<>();
            for (AlertEntity alert : last24hAlerts) {
                String transition = alert.getAlertTransition() != null ? alert.getAlertTransition() : "Unknown";
                transitionCounts.merge(transition, 1L, Long::sum);
            }

            // Processing status analysis
            long actionableCount = last24hAlerts.stream()
                    .filter(a -> "ACTIONABLE".equals(a.getProcessingStatus()))
                    .count();
            long nonActionableCount = last24hAlerts.stream()
                    .filter(a -> "NON_ACTIONABLE".equals(a.getProcessingStatus()))
                    .count();

            StringBuilder result = new StringBuilder();
            result.append("=== Alert Pattern Analysis ===\n");
            result.append(String.format("Scope: %s\n", alertScope));
            result.append(String.format("Type: %s\n\n", alertType));

            result.append("--- Frequency Analysis ---\n");
            result.append(String.format("Same scope alerts (last 1h): %d\n", last1hCount));
            result.append(String.format("Same scope alerts (last 6h): %d\n", last6hCount));
            result.append(String.format("Same scope alerts (last 24h): %d\n", last24hCount));
            result.append(String.format("Same type alerts (last 7d): %d\n", last7dByType.size()));
            result.append(String.format("Exact same title (last 24h): %d\n\n", sameTitle24h));

            result.append("--- Transition Analysis (last 24h) ---\n");
            transitionCounts
                    .forEach((transition, count) -> result.append(String.format("  %s: %d\n", transition, count)));

            result.append("\n--- Historical Verdicts (last 24h) ---\n");
            result.append(String.format("  Previously ACTIONABLE: %d\n", actionableCount));
            result.append(String.format("  Previously NON_ACTIONABLE: %d\n\n", nonActionableCount));

            // Pattern flags
            if (last1hCount > 3) {
                result.append("🔴 ALERT STORM detected: >3 alerts from same scope in last hour.\n");
            }
            if (sameTitle24h > 5) {
                result.append("🟡 FLAPPING ALERT: Same alert title triggered >5 times in 24h.\n");
            }
            if (nonActionableCount > actionableCount && last24hCount > 3) {
                result.append("🟡 NOISE PATTERN: Most alerts from this scope were previously non-actionable.\n");
            }
            if (last24hCount == 0) {
                result.append(
                        "🟢 FIRST OCCURRENCE: No prior alerts from this scope in 24h — treat with higher priority.\n");
            }

            Long triggeredCount = transitionCounts.getOrDefault("Triggered", 0L);
            Long resolvedCount = transitionCounts.getOrDefault("Resolved", 0L);
            if (triggeredCount > 0 && resolvedCount > 0 && Math.abs(triggeredCount - resolvedCount) <= 1) {
                result.append("🟡 OSCILLATING: Alert is being triggered and resolved repeatedly.\n");
            }

            return result.toString();
        } catch (Exception e) {
            log.error("Error analyzing alert patterns", e);
            return "Pattern analysis failed: " + e.getMessage();
        }
    }
}
