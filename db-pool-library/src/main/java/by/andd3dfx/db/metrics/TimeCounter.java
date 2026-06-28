package by.andd3dfx.db.metrics;

/**
 * <pre>
 * Class contains counters grouped together to collect and measure statistics of happened events:
 * - events count
 * - total time of all events in milliseconds
 * - min event time in milliseconds
 * - max event time in milliseconds
 * - average duration of event
 * </pre>
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
            // We need this branch because of non-0 default value of minTime
            return new Snapshot(0, 0, 0, 0, 0);
        }
        return new Snapshot(count, totalTime, minTime, maxTime, totalTime / count);
    }

    record Snapshot(int count, long totalTime, long minTime, long maxTime, long average) {
    }
}
