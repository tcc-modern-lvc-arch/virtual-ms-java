package io.github.raphonzius.lvc.virtualareas.infrastructure.adapter.web.dto;

import io.github.raphonzius.lvc.virtualareas.domain.event.AreaTransitionEvent;

public record TransitionResult(String areaId, String areaName, String entityId, String transition) {
    public static TransitionResult from(AreaTransitionEvent e) {
        String t = switch (e) {
            case AreaTransitionEvent.Checkin ignored -> "CHECKIN";
            case AreaTransitionEvent.Checkout ignored -> "CHECKOUT";
        };
        return new TransitionResult(e.areaId(), e.areaName(), e.entityId(), t);
    }
}
