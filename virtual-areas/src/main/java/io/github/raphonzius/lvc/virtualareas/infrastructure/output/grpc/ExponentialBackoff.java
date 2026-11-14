package io.github.raphonzius.lvc.virtualareas.infrastructure.output.grpc;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/**
 * Exponential backoff with "equal jitter": the n-th delay is uniformly distributed in
 * {@code [base/2, base]}, where {@code base = min(max, initial * 2^n)}. Jitter spreads reconnect
 * storms when many subscribers lose the hub at the same moment. Thread-safe.
 */
final class ExponentialBackoff {

    private final long initialMillis;
    private final long maxMillis;
    private final DoubleSupplier random;
    private int attempt;

    ExponentialBackoff(Duration initial, Duration max) {
        this(initial, max, () -> ThreadLocalRandom.current().nextDouble());
    }

    ExponentialBackoff(Duration initial, Duration max, DoubleSupplier random) {
        this.initialMillis = Math.max(1, initial.toMillis());
        this.maxMillis = Math.max(initialMillis, max.toMillis());
        this.random = random;
    }

    synchronized Duration nextDelay() {
        // shift capped at 30 so the long never overflows; min() applies the ceiling
        long base = Math.min(maxMillis, initialMillis << Math.min(attempt, 30));
        if (base < maxMillis) attempt++;
        long half = base / 2;
        return Duration.ofMillis(half + Math.round((base - half) * random.getAsDouble()));
    }

    synchronized void reset() {
        attempt = 0;
    }
}
