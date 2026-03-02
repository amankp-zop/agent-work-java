package com.hitachi.alertLogAnalyzer.agent.nodes;

import com.hitachi.alertLogAnalyzer.agent.state.AlertAnalyzerState;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;

import java.util.List;
import java.util.Map;

/**
 * Simple node that logs the result for non-actionable alerts.
 * No JIRA ticket is created — the alert is logged and marked as resolved.
 */
@Slf4j
public class ResultLoggerAgent implements NodeAction<AlertAnalyzerState> {

    @Override
    public Map<String, Object> apply(AlertAnalyzerState state) {
        log.info("📋 [ResultLoggerAgent] Alert classified as NON_ACTIONABLE.");
        log.info("📋 [ResultLoggerAgent] Analysis Summary:\n{}", state.analysisSummary());
        log.info("📋 [ResultLoggerAgent] No JIRA ticket will be created. Alert archived.");

        return Map.of(
                AlertAnalyzerState.MESSAGES,
                List.of("[ResultLogger] 📋 Alert is NON_ACTIONABLE. Logged and archived. No ticket created."));
    }
}
