package com.exchange.infrastructure.journal;

import com.exchange.matching.command.CancelOrder;
import com.exchange.matching.command.Command;
import com.exchange.matching.command.MalformedCommand;
import com.exchange.matching.command.PlaceLimitOrder;
import com.exchange.matching.command.PlaceMarketOrder;
import com.exchange.matching.model.Side;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32C;

/**
 * Encodes journal v1 independently of Java command implementation classes. Single-owner reusable codec.
 */
final class JournalRecordCodec {
    static final int RECORD_BYTES = 44;
    private final CRC32C checksum = new CRC32C();

    void encode(Command command, long sequence, ByteBuffer target) {
        target.clear();
        if (command == null) command = new MalformedCommand(null, 0, null, 0, 0);
        int type = command.type() == null ? 0 : switch (command.type()) {
            case LIMIT -> 1;
            case MARKET -> 2;
            case CANCEL -> 3;
        };
        int side = command.side() == null ? 0 : command.side() == Side.BUY ? 1 : 2;
        target.putLong(sequence).putInt(type).putLong(command.orderId()).putInt(side)
                .putLong(command.price()).putLong(command.quantity());
        checksum.reset();
        checksum.update(target.array(), 0, RECORD_BYTES - Integer.BYTES);
        target.putInt((int) checksum.getValue()).flip();
    }

    Command decode(ByteBuffer source, long expectedSequence) throws IOException {
        checksum.reset();
        checksum.update(source.array(), 0, RECORD_BYTES - Integer.BYTES);
        if ((int) checksum.getValue() != source.getInt(RECORD_BYTES - Integer.BYTES))
            throw new IOException("journal checksum mismatch");
        if (source.getLong() != expectedSequence) throw new IOException("journal sequence gap");
        int typeCode = source.getInt();
        long id = source.getLong();
        Side side = switch (source.getInt()) {
            case 0 -> null;
            case 1 -> Side.BUY;
            case 2 -> Side.SELL;
            default -> throw new IOException("invalid side");
        };
        long price = source.getLong(), quantity = source.getLong();
        return switch (typeCode) {
            case 0 -> new MalformedCommand(null, id, side, price, quantity);
            case 1 -> new PlaceLimitOrder(id, side, price, quantity);
            case 2 -> price == 0 ? new PlaceMarketOrder(id, side, quantity)
                    : new MalformedCommand(Command.Type.MARKET, id, side, price, quantity);
            case 3 -> side == null && price == 0 && quantity == 0 ? new CancelOrder(id)
                    : new MalformedCommand(Command.Type.CANCEL, id, side, price, quantity);
            default -> throw new IOException("invalid command type");
        };
    }
}
