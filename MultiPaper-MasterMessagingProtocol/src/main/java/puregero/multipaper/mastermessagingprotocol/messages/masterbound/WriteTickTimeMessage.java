package puregero.multipaper.mastermessagingprotocol.messages.masterbound;

import puregero.multipaper.mastermessagingprotocol.ExtendedByteBuf;

    public class WriteTickTimeMessage extends MasterBoundMessage {

    public final double tickTime;
    public final float tps;
    public final long ownedChunks;

    public WriteTickTimeMessage(double tickTime, float tps, long ownedChunks) {
        this.tickTime = tickTime;
        this.tps = tps;
        this.ownedChunks = ownedChunks;
    }

    public WriteTickTimeMessage(ExtendedByteBuf byteBuf) {
        tickTime = byteBuf.readDouble();
        tps = byteBuf.readFloat();
        ownedChunks = byteBuf.readLong();
    }

    @Override
    public void write(ExtendedByteBuf byteBuf) {
        byteBuf.writeDouble(tickTime);
        byteBuf.writeFloat(tps);
        byteBuf.writeLong(ownedChunks);
    }

    @Override
    public void handle(MasterBoundMessageHandler handler) {
        handler.handle(this);
    }
}
