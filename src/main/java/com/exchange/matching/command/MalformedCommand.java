package com.exchange.matching.command;

import com.exchange.matching.model.Side;

/**
 * Preserves malformed input for deterministic rejection, including existing v1 journal records.
 */
public record MalformedCommand(Type type, long orderId, Side side, long price, long quantity) implements Command {
    public MalformedCommand {
        boolean malformed = type == null
                || (type == Type.CANCEL && (side != null || price != 0 || quantity != 0))
                || (type == Type.MARKET && price != 0);
        if (!malformed) throw new IllegalArgumentException("Use a typed command for structurally valid input");
    }
}
