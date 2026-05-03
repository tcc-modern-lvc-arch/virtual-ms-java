package io.github.raphonzius.lvc.virtualareas.domain.event;

import java.util.List;

public interface InfluxProjectionPort {
    void writeTransition(AreaTransitionEvent event);

    List<TransitionRecord> queryRecent(int limit);
}
