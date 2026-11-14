package io.github.raphonzius.lvc.virtualareas.infrastructure.support;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class LogThrottleTest {

    private final AtomicLong nanos = new AtomicLong(1_000);
    private final LogThrottle throttle = new LogThrottle(Duration.ofMinutes(1), nanos::get);

    @Test
    void allowsOncePerIntervalPerKey() {
        assertThat(throttle.allow("unknown_type")).isTrue();
        assertThat(throttle.allow("unknown_type")).isFalse();
        assertThat(throttle.allow("unknown_lvc")).as("independent key").isTrue();

        nanos.addAndGet(Duration.ofSeconds(59).toNanos());
        assertThat(throttle.allow("unknown_type")).isFalse();

        nanos.addAndGet(Duration.ofSeconds(1).toNanos());
        assertThat(throttle.allow("unknown_type")).isTrue();
    }
}
