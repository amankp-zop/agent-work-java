package com.hitachi.alertLogAnalyzer.agent.state;

import org.bsc.langgraph4j.state.AgentState;
import org.bsc.langgraph4j.state.Channel;
import org.bsc.langgraph4j.state.Channels;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared state for the Alert Analyzer agent graph.
 * All nodes read from and write to this object.
 */
public class AlertAnalyzerState extends AgentState {

    public static final String MESSAGES = "messages";
    public static final String VERDICT = "verdict";
    public static final String ANALYSIS_SUMMARY = "analysis_summary";
    public static final String ALERT_PAYLOAD = "alert_payload";
    public static final String JIRA_RESULT = "jira_result";

    public static final Map<String, Channel<?>> SCHEMA = Map.of(
            MESSAGES, Channels.appender(ArrayList::new),
            VERDICT, Channels.base(() -> ""),
            ANALYSIS_SUMMARY, Channels.base(() -> ""),
            ALERT_PAYLOAD, Channels.base(() -> ""),
            JIRA_RESULT, Channels.base(() -> ""));

    public AlertAnalyzerState(Map<String, Object> initData) {
        super(initData);
    }

    public List<String> messages() {
        return this.<List<String>>value(MESSAGES).orElse(List.of());
    }

    public String verdict() {
        return this.<String>value(VERDICT).orElse("");
    }

    public String analysisSummary() {
        return this.<String>value(ANALYSIS_SUMMARY).orElse("");
    }

    public String alertPayload() {
        return this.<String>value(ALERT_PAYLOAD).orElse("");
    }

    public String jiraResult() {
        return this.<String>value(JIRA_RESULT).orElse("");
    }
}
