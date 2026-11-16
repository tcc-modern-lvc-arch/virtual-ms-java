package io.github.raphonzius.lvc.virtualareas.domain.event;

/**
 * Transactional outbox for CHECKIN / CHECKOUT transitions.
 * <p>
 * {@link #enqueue} must take part in the caller's transaction: the outbox row commits (or rolls back)
 * together with the entity-state change, so a committed transition is never lost and a rolled-back one
 * never leaks. Delivery to event-hub happens later, asynchronously, by a relay.
 */
public interface TransitionOutbox {
    void enqueue(AreaTransitionEvent event);
}
