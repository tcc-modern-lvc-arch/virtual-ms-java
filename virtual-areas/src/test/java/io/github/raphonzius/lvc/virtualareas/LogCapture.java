package io.github.raphonzius.lvc.virtualareas;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.util.List;

/** Captures the events a class logs (logback is the test runtime's SLF4J backend). */
public final class LogCapture {

    private final Logger logger;
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    private LogCapture(Class<?> type) {
        logger = (Logger) LoggerFactory.getLogger(type);
        logger.setLevel(Level.DEBUG);
        appender.start();
        logger.addAppender(appender);
    }

    public static LogCapture attach(Class<?> type) {
        return new LogCapture(type);
    }

    public void detach() {
        logger.detachAppender(appender);
        appender.stop();
    }

    public List<ILoggingEvent> events() {
        return List.copyOf(appender.list);
    }

    public List<ILoggingEvent> at(Level level) {
        return events().stream().filter(e -> e.getLevel() == level).toList();
    }

    public List<ILoggingEvent> errors() {
        return at(Level.ERROR);
    }

    public List<ILoggingEvent> warnings() {
        return at(Level.WARN);
    }
}
