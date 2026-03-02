package com.hitachi.alertLogAnalyzer.service;

import com.hitachi.alertLogAnalyzer.agent.service.AlertAgentService;
import com.hitachi.alertLogAnalyzer.dto.DatadogAlertDto;
import com.hitachi.alertLogAnalyzer.entity.AlertEntity;
import com.hitachi.alertLogAnalyzer.event.AlertRecievedEvent;
import com.hitachi.alertLogAnalyzer.repository.AlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class AlertEventListener {
    private final AlertService alertService;
    private final AlertAgentService alertAgentService;
    private final AlertRepository alertRepository;

    @Async("taskExecutor")
    @EventListener
    public void handleAlertEvent(AlertRecievedEvent event) {
        DatadogAlertDto alertDto = event.getAlertDto();

        log.info("Start processing alert: '{}' on Thread: {}",
                alertDto.getAlertTitle(),
                Thread.currentThread().getName());

        try {
            // Step 1: Save alert to database
            AlertEntity savedEntity = alertService.saveAlert(alertDto);
            log.info("Alert saved with ID: {}", savedEntity.getId());

            // Step 2: Run AI agent analysis
            log.info("🤖 Invoking AI agent for alert analysis...");
            AlertAgentService.AlertAnalysisResult result = alertAgentService.analyzeAlert(alertDto);

            // Step 3: Update entity with analysis results
            savedEntity.setProcessingStatus(result.verdict());
            savedEntity.setAnalysisSummary(truncate(result.analysisSummary(), 4000));

            if (result.jiraTicketKey() != null && !result.jiraTicketKey().isBlank()) {
                // Extract ticket key (first line of jiraResult)
                String ticketKey = result.jiraTicketKey().split("\n")[0].trim();
                savedEntity.setJiraTicketKey(ticketKey);
                log.info("🎫 JIRA ticket created: {}", ticketKey);
            }

            alertRepository.save(savedEntity);
            log.info("✅ Alert processing complete. Verdict: {}", result.verdict());

        } catch (Exception e) {
            log.error("❌ Error processing alert: '{}'", alertDto.getAlertTitle(), e);
        }

        log.info("Finished processing alert: '{}'", alertDto.getAlertTitle());
    }

    private String truncate(String text, int maxLength) {
        if (text == null)
            return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }
}
