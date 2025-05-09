package puregero.multipaper.server;

public class CircularTimer {
    public static final int DEFAULT_SIZE = 60;

    private static int size = DEFAULT_SIZE;

    private double[] times;
    private double total = 0;
    private int index = 0;

    public CircularTimer() {
        times = new double[size];
    }

    public void append(double time) {
        total -= times[index];
        total += times[index] = time;
        index = (index + 1) % times.length;
    }

    public double averageInMillis() {
        return total / times.length;
    }

    public static void setSize(int size) {
        CircularTimer.size = size;
    }
}
