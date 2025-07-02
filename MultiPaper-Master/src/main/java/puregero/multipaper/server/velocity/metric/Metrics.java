package puregero.multipaper.server.velocity.metric;

public class Metrics {

    private String name;
    private double mspt;
    private double quality;
    private int players;
    private long chunks;
    
    public Metrics(String name, double mspt, double quality, int players, long chunks){
        this.name    = name;
        this.mspt    = mspt;
        this.quality = quality;
        this.players = players;
        this.chunks  = chunks;
    }

    public String getName() {
        return this.name;
    }

    public double getMspt() {
        return this.mspt;
    }

    public double getQuality() {
        return this.quality;
    }

    public int getPlayers() {
        return this.players;
    }

    public long getChunks() {
        return this.chunks;
    }
}


