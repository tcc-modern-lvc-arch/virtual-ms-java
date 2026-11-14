package io.github.raphonzius.lvc.virtualareas.infrastructure.output.grpc;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ExponentialBackoffTest {

    @Test
    void doublesUpToCapWhenJitterIsMaximal() {
        var backoff = new ExponentialBackoff(Duration.ofSeconds(1), Duration.ofSeconds(30), () -> 1.0);

        assertThat(backoff.nextDelay()).isEqualTo(Duration.ofSeconds(1));
        assertThat(backoff.nextDelay()).isEqualTo(Duration.ofSeconds(2));
        assertThat(backoff.nextDelay()).isEqualTo(Duration.ofSeconds(4));
        assertThat(backoff.nextDelay()).isEqualTo(Duration.ofSeconds(8));
        assertThat(backoff.nextDelay()).isEqualTo(Duration.ofSeconds(16));
        assertThat(backoff.nextDelay()).isEqualTo(Duration.ofSeconds(30));
        assertThat(backoff.nextDelay()).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void jitterKeepsDelayBetweenHalfAndFullBase() {
        var low = new ExponentialBackoff(Duration.ofSeconds(4), Duration.ofSeconds(30), () -> 0.0);
        assertThat(low.nextDelay()).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    void resetRestartsFromInitialDelay() {
        var backoff = new ExponentialBackoff(Duration.ofSeconds(1), Duration.ofSeconds(30), () -> 1.0);
        backoff.nextDelay();
        backoff.nextDelay();
        backoff.nextDelay();

        backoff.reset();

        assertThat(backoff.nextDelay()).isEqualTo(Duration.ofSeconds(1));
    }
}
