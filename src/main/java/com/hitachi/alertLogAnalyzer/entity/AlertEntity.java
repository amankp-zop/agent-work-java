package com.hitachi.alertLogAnalyzer.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "alerts")
@Data
@NoArgsConstructor
public class AlertEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String alertTitle;

    @Column(length = 1000)
    private String alertStatus;

    @Column(length = 2000)
    private String alertQuery;

    private String alertScope;
    private String alertTransition;
    private String alertType;

    private LocalDateTime eventDate;

    private String priority;

    // "PENDING", "ACTIONABLE", "NON_ACTIONABLE"
    private String processingStatus;

    @Column(length = 4000)
    private String analysisSummary;

    private String jiraTicketKey;
}