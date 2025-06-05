package puregero.multipaper.server.velocity;

import com.velocitypowered.api.proxy.server.RegisteredServer;

public class ServerWithData {

    protected Boolean perf;
    protected RegisteredServer server;
    protected int players;
    protected double mspt;

    public ServerWithData(Boolean perfDeg, RegisteredServer server, int players, double time){
        this.perf = perfDeg;
        this.server = server;
        this.players = players;
        this.mspt = time;
    }

    public Boolean getPerf() {
        return perf;
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
}