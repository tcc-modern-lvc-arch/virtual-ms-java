package io.github.raphonzius.lvc.virtualareas.infrastructure.support;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Per-key rate limiter for noisy log statements: {@link #allow} is true at most once per interval for each
 * key. Thread-safe; keys are expected to be a small fixed set (reasons, enum names), not user input.
 */
public final class LogThrottle {

    private final long intervalNanos;
    private final LongSupplier nanoClock;
    private final ConcurrentHashMap<String, Long> lastAllowed = new ConcurrentHashMap<>();

    public LogThrottle(Duration interval) {
        this(interval, System::nanoTime);
    }

    public LogThrottle(Duration interval, LongSupplier nanoClock) {
        this.intervalNanos = interval.toNanos();
        this.nanoClock = nanoClock;
    }

    public boolean allow(String key) {
        long now = nanoClock.getAsLong();
        boolean[] allowed = {false};
        lastAllowed.compute(key, (_, last) -> {
            if (last == null || now - last >= intervalNanos) {
                allowed[0] = true;
                return now;
            }
            return last;
        });
        return allowed[0];
    }
}
