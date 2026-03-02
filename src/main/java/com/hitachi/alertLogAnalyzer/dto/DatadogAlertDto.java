package com.hitachi.alertLogAnalyzer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data // Lombok generates Getters, Setters, toString
public class DatadogAlertDto {

    @NotBlank(message = "Alert title is mandatory")
    @JsonProperty("alert_title") // Maps JSON "alert_title" to Java "alertTitle"
    private String alertTitle;

    @JsonProperty("alert_status")
    private String alertStatus;

    @JsonProperty("alert_query")
    private String alertQuery;

    @JsonProperty("alert_scope")
    private String alertScope;

    @JsonProperty("alert_transition")
    private String alertTransition;

    @JsonProperty("alert_type")
    private String alertType;

    @JsonProperty("date_epoch_ms")
    private Long dateEpochMs;

    @JsonProperty("event_title")
    private String eventTitle;

    private String priority;
}
