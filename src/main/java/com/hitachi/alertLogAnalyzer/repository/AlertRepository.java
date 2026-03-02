package com.hitachi.alertLogAnalyzer.repository;

import com.hitachi.alertLogAnalyzer.entity.AlertEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AlertRepository extends JpaRepository<AlertEntity, Long> {

    List<AlertEntity> findByAlertScopeAndProcessingStatus(String scope, String status);

    // Used by AlertHistoryTool — find recent alerts by scope
    List<AlertEntity> findByAlertScopeAndEventDateAfterOrderByEventDateDesc(
            String alertScope, LocalDateTime afterDate);

    // Used by AlertPatternTool — find alerts by type within a time range
    List<AlertEntity> findByAlertTypeAndEventDateAfterOrderByEventDateDesc(
            String alertType, LocalDateTime afterDate);

    // Used by AlertPatternTool — count alerts by scope in time window
    long countByAlertScopeAndEventDateAfter(String alertScope, LocalDateTime afterDate);

    // Used by AlertPatternTool — count alerts by type in time window
    long countByAlertTypeAndEventDateAfter(String alertType, LocalDateTime afterDate);
}
