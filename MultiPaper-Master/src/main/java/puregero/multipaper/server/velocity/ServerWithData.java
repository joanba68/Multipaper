package puregero.multipaper.server.velocity;

import com.velocitypowered.api.proxy.server.RegisteredServer;

public class ServerWithData {

    protected Boolean degPerf;
    protected RegisteredServer server;
    protected int players;
    protected double mspt;
    private long ownedChunks;

    public ServerWithData(Boolean perf, RegisteredServer server, int players, double time, long chunks){
        this.degPerf = perf;
        this.server = server;
        this.players = players;
        this.mspt = time;
        this.ownedChunks = chunks;
    }

    public Boolean getPerf() {
        return degPerf;
    }

    public RegisteredServer getServer() {
        return server;
    }

    public int getPlayers() {
        return players;
    }

    public double getMspt() {
        return mspt;
    }

    public double getChunks() {
        return mspt;
    }
}