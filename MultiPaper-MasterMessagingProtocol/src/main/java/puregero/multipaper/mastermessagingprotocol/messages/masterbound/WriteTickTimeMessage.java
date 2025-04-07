package puregero.multipaper.mastermessagingprotocol.messages.masterbound;

import puregero.multipaper.mastermessagingprotocol.ExtendedByteBuf;

    public class WriteTickTimeMessage extends MasterBoundMessage {

    public final long tickTime;
    public final float tps;
    public final long ownedChunks;

    public WriteTickTimeMessage(long tickTime, float tps, long ownedChunks) {
        this.tickTime = tickTime;
        this.tps = tps;
        this.ownedChunks = ownedChunks;
    }

    public WriteTickTimeMessage(ExtendedByteBuf byteBuf) {
        tickTime = byteBuf.readLong();
        tps = byteBuf.readFloat();
        ownedChunks = byteBuf.readLong();
    }

    @Override
    public void write(ExtendedByteBuf byteBuf) {
        byteBuf.writeLong(tickTime);
        byteBuf.writeFloat(tps);
        byteBuf.writeLong(ownedChunks);
    }

    @Override
    public void handle(MasterBoundMessageHandler handler) {
        handler.handle(this);
    }
}
