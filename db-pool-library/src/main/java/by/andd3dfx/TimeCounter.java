package by.andd3dfx;

/**
 * Collects timing statistics for events (DB creation, pool wait time).
 */
class TimeCounter {

    private int count;
    private long totalTime;
    private long minTime = Long.MAX_VALUE;
    private long maxTime;

    public synchronized void add(long timeMs) {
        count++;
        totalTime += timeMs;
        if (timeMs < minTime) {
            minTime = timeMs;
        }
        if (timeMs > maxTime) {
            maxTime = timeMs;
        }
    }

    public synchronized Snapshot snapshot() {
        if (count == 0) {
            return new Snapshot(0, 0, 0, 0, 0);
        }
        return new Snapshot(count, totalTime, minTime, maxTime, totalTime / count);
    }

    record Snapshot(int count, long totalTime, long minTime, long maxTime, long average) {
    }
}
