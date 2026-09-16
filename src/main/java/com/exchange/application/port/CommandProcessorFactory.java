package com.exchange.application.port;

/**
 * Builds a processor using a preconfigured transport strategy.
 */
@FunctionalInterface
public interface CommandProcessorFactory {
    CommandProcessor create(int capacity, CommandProcessor.Handler handler);
}
