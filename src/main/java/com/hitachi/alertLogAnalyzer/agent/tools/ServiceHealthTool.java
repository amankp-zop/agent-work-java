package com.hitachi.alertLogAnalyzer.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * Tool: Check the health status of the service associated with the alert.
 * 
 * Currently returns SIMULATED health data. In production, this would call
 * your service health/monitoring endpoint (e.g., /health, /actuator/health,
 * Consul, or a service mesh health API).
 * 
 * Helps the analyzer:
 * - Determine if the service is actually degraded or down
 * - Correlate uptime data with the alert timing
 * - Identify cascading failure patterns across services
 */
@Component
@Slf4j
public class ServiceHealthTool {

    /**
     * Check health status for the service related to the alert scope.
     *
     * @param alertScope The scope of the alert, e.g., "host:prod-web-01"
     * @return Formatted health status report
     */
    @Tool("Check the health status, uptime, error rate, and instance count of the service associated with the alert. Use this to determine if a service is UP, DEGRADED, or DOWN.")
    public String checkServiceHealth(
            @P("The scope of the alert, e.g. 'host:prod-web-01' or 'service:payment-api'") String alertScope) {
        log.info("🏥 [ServiceHealthTool] Checking health for scope='{}'", alertScope);

        // Extract service/host name from scope
        String serviceName = alertScope.replace("host:", "")
                .replace("service:", "")
                .replace("env:", "");

        // Simulated health check — in production, call real health endpoint
        Random random = new Random();
        String[] statuses = { "UP", "UP", "UP", "DEGRADED", "DOWN" }; // weighted toward UP
        String healthStatus = statuses[random.nextInt(statuses.length)];

        double uptimeHours = 24 + random.nextDouble() * 720; // 1-30 days
        double errorRatePercent = random.nextDouble() * 8; // 0-8%
        int avgLatencyMs = 50 + random.nextInt(450); // 50-500ms
        int activeInstances = 1 + random.nextInt(5); // 1-5 instances
        int totalInstances = activeInstances + random.nextInt(2); // +0-1 down instances

        LocalDateTime lastDeployment = LocalDateTime.now().minusHours(random.nextInt(72));
        LocalDateTime lastIncident = LocalDateTime.now().minusDays(random.nextInt(30));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        StringBuilder result = new StringBuilder();
        result.append("=== Service Health Report ===\n");
        result.append(String.format("Service/Host: %s\n", serviceName));
        result.append(String.format("Health Status: %s\n", healthStatus));
        result.append(String.format("Uptime: %.1f hours\n", uptimeHours));
        result.append(String.format("Error Rate (5min avg): %.2f%%\n", errorRatePercent));
        result.append(String.format("Avg Latency: %dms\n", avgLatencyMs));
        result.append(String.format("Instances: %d/%d healthy\n", activeInstances, totalInstances));
        result.append(String.format("Last Deployment: %s\n", lastDeployment.format(formatter)));
        result.append(String.format("Last Known Incident: %s\n\n", lastIncident.format(formatter)));

        // Health assessment
        if ("DOWN".equals(healthStatus)) {
            result.append("🔴 SERVICE IS DOWN — immediate action required!\n");
        } else if ("DEGRADED".equals(healthStatus)) {
            result.append("🟡 SERVICE IS DEGRADED — performance impacted.\n");
        } else {
            result.append("🟢 Service is healthy — alert may be transient or metric-specific.\n");
        }

        if (errorRatePercent > 5) {
            result.append("⚠️ Error rate is elevated (>5%). Check application logs.\n");
        }

        if (activeInstances < totalInstances) {
            result.append(String.format("⚠️ %d instance(s) are NOT healthy.\n", totalInstances - activeInstances));
        }

        result.append("\n(Note: Simulated data. Connect to your health API for real status.)");

        return result.toString();
    }
}
