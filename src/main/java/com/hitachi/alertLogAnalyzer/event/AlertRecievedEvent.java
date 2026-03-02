package com.hitachi.alertLogAnalyzer.event;

import com.hitachi.alertLogAnalyzer.dto.DatadogAlertDto;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class AlertRecievedEvent extends ApplicationEvent {
    private final DatadogAlertDto alertDto;

    public AlertRecievedEvent(Object source, DatadogAlertDto alertDto){
        super(source);
        this.alertDto=alertDto;
    }
}
