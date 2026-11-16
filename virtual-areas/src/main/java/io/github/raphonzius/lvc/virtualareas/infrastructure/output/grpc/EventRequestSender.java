package io.github.raphonzius.lvc.virtualareas.infrastructure.output.grpc;

import io.github.raphonzius.lvc.proto.event.EventRequest;

/** Sends an already-built {@link EventRequest} to event-hub; throws if it was not accepted. */
@FunctionalInterface
public interface EventRequestSender {
    void send(EventRequest request);
}
