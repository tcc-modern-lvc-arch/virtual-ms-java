package io.github.raphonzius.lvc.virtualareas.domain.event;

public interface EventDispatchPort {
    void dispatch(AreaTransitionEvent event);
}
