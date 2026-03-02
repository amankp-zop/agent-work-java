package com.hitachi.alertLogAnalyzer.controller;

import com.hitachi.alertLogAnalyzer.dto.DatadogAlertDto;
import com.hitachi.alertLogAnalyzer.event.AlertRecievedEvent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhook")
@Slf4j
@RequiredArgsConstructor
public class WebhookController {
    private final ApplicationEventPublisher eventPublisher;

    @PostMapping("/datadog")
    public ResponseEntity<String> recieveAlert(@Valid @RequestBody DatadogAlertDto alertDto){
        log.info("Received Alert via HTTP. Publishing event...");

        eventPublisher.publishEvent(new AlertRecievedEvent(this,alertDto));

        return ResponseEntity.accepted().body("Alert recieved and queued for processing");
    }
}
