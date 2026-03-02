package com.hitachi.alertLogAnalyzer.service;

import com.hitachi.alertLogAnalyzer.dto.DatadogAlertDto;
import com.hitachi.alertLogAnalyzer.entity.AlertEntity;
import com.hitachi.alertLogAnalyzer.repository.AlertRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {
    private final AlertRepository alertRepository;

    @Transactional
    public AlertEntity saveAlert(DatadogAlertDto dto){
        log.info("Saving alert to database: {}",dto.getAlertTitle());

        AlertEntity entity = new AlertEntity();
        entity.setAlertTitle(dto.getAlertTitle());
        entity.setAlertStatus(dto.getAlertStatus());
        entity.setAlertQuery(dto.getAlertQuery());
        entity.setAlertScope(dto.getAlertScope());
        entity.setAlertTransition(dto.getAlertTransition());
        entity.setAlertType(dto.getAlertType());
        entity.setPriority(dto.getPriority());

        if (dto.getDateEpochMs() != null) {
            entity.setEventDate(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(dto.getDateEpochMs()),
                    ZoneId.systemDefault()));
        }

        entity.setProcessingStatus("PENDING");

        return alertRepository.save(entity);
    }
}
