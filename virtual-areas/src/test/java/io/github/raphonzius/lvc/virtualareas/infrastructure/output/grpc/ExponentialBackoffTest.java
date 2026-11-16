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

    @Test
    void shortLivedStreamsKeepEscalatingEvenIfTheyDeliveredMessages() {
        var backoff = new ExponentialBackoff(Duration.ofSeconds(1), Duration.ofSeconds(30), () -> 1.0);
        var resetAfter = Duration.ofSeconds(30);

        assertThat(backoff.nextDelayAfterStream(Duration.ofSeconds(2), resetAfter)).isEqualTo(Duration.ofSeconds(1));
        assertThat(backoff.nextDelayAfterStream(Duration.ofSeconds(29), resetAfter)).isEqualTo(Duration.ofSeconds(2));
        assertThat(backoff.nextDelayAfterStream(Duration.ofMillis(10), resetAfter)).isEqualTo(Duration.ofSeconds(4));
    }

    @Test
    void streamThatLivedLongEnoughResetsTheBackoff() {
        var backoff = new ExponentialBackoff(Duration.ofSeconds(1), Duration.ofSeconds(30), () -> 1.0);
        var resetAfter = Duration.ofSeconds(30);
        backoff.nextDelay();
        backoff.nextDelay();
        backoff.nextDelay();

        assertThat(backoff.nextDelayAfterStream(Duration.ofSeconds(30), resetAfter)).isEqualTo(Duration.ofSeconds(1));
        assertThat(backoff.nextDelayAfterStream(Duration.ofSeconds(1), resetAfter)).isEqualTo(Duration.ofSeconds(2));
    }
}
