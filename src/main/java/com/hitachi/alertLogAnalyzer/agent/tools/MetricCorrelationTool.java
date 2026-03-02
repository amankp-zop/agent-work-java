package com.hitachi.alertLogAnalyzer.agent.tools;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * Tool: Fetch correlated infrastructure metrics for the alert scope.
 * 
 * Currently returns SIMULATED metric data. In production, this would call
 * the Datadog Metrics API or your monitoring system's API.
 * 
 * Helps the analyzer:
 * - Identify resource saturation (CPU, memory, disk)
 * - Correlate network issues with alerts
 * - Determine if the alert is caused by a broader infrastructure issue
 */
@Component
@Slf4j
public class MetricCorrelationTool {

    /**
     * Fetch infrastructure metrics correlated with the alert.
     *
     * @param alertScope The scope of the alert, e.g., "host:prod-web-01"
     * @param alertQuery The original alert query, e.g.,
     *                   "avg:system.cpu.user{host:prod-web-01} > 90"
     * @return Formatted metric snapshot string
     */
    @Tool("Fetch correlated infrastructure metrics (CPU, memory, disk, network, latency) for the alert scope. Use this when you need to check resource utilization and infrastructure health.")
    public String getCorrelatedMetrics(
            @P("The scope of the alert, e.g. 'host:prod-web-01'") String alertScope,
            @P("The original alert query, e.g. 'avg:system.cpu.user{host:prod-web-01} > 90'") String alertQuery) {
        log.info("📊 [MetricCorrelationTool] Fetching metrics for scope='{}', query='{}'", alertScope, alertQuery);

        // Simulated metrics — in production, replace with Datadog API call
        Random random = new Random();

        double cpuUsage = 40 + random.nextDouble() * 55; // 40-95%
        double memoryUsage = 50 + random.nextDouble() * 40; // 50-90%
        double diskUsage = 30 + random.nextDouble() * 50; // 30-80%
        double networkErrorRate = random.nextDouble() * 5; // 0-5%
        int activeConnections = 100 + random.nextInt(900); // 100-1000
        double responseTimeP99 = 100 + random.nextDouble() * 4900; // 100-5000ms

        StringBuilder result = new StringBuilder();
        result.append("=== Correlated Infrastructure Metrics ===\n");
        result.append(String.format("Scope: %s\n", alertScope));
        result.append(String.format("Time range: last 15 minutes\n\n"));

        result.append(String.format("CPU Usage (avg):       %.1f%%\n", cpuUsage));
        result.append(String.format("Memory Usage (avg):    %.1f%%\n", memoryUsage));
        result.append(String.format("Disk Usage:            %.1f%%\n", diskUsage));
        result.append(String.format("Network Error Rate:    %.2f%%\n", networkErrorRate));
        result.append(String.format("Active Connections:    %d\n", activeConnections));
        result.append(String.format("Response Time (P99):   %.0fms\n\n", responseTimeP99));

        // Add threshold breach indicators
        if (cpuUsage > 85)
            result.append("⚠️ CPU usage is CRITICAL (>85%%)\n");
        if (memoryUsage > 80)
            result.append("⚠️ Memory usage is HIGH (>80%%)\n");
        if (diskUsage > 75)
            result.append("⚠️ Disk usage approaching limits (>75%%)\n");
        if (networkErrorRate > 2)
            result.append("⚠️ Network error rate elevated (>2%%)\n");
        if (responseTimeP99 > 3000)
            result.append("⚠️ Response time is degraded (P99 >3000ms)\n");

        result.append("\n(Note: These are simulated metrics. Connect to Datadog API for real data.)");

        return result.toString();
    }
}
