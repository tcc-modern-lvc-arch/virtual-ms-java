package io.github.raphonzius.lvc.virtualareas.infrastructure.output.persistence.mariadb;

/** Published when a row is added to the outbox; the relay uses it (after commit) to wake up immediately. */
public record OutboxEnqueued() {
}
