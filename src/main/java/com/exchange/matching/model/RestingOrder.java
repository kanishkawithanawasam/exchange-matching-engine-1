package com.exchange.matching.model;

/**
 * Immutable observation of an order; mutable order state remains inside the book.
 */
public record RestingOrder(long id, Side side, long price, long remaining) {
}
