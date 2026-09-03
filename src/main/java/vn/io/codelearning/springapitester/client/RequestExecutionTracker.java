package vn.io.codelearning.springapitester.client;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Tracks the newest request for each endpoint so stale callbacks cannot replace newer UI state. */
public final class RequestExecutionTracker {
    private final AtomicLong sequence = new AtomicLong();
    private final Map<String, Long> latestByEndpoint = new ConcurrentHashMap<>();

    public long begin(String endpointId) {
        long value = sequence.incrementAndGet();
        latestByEndpoint.put(endpointId, value);
        return value;
    }

    public boolean isLatest(String endpointId, long requestSequence) {
        return latestByEndpoint.getOrDefault(endpointId, -1L) == requestSequence;
    }

    public void clear(String endpointId, long requestSequence) {
        latestByEndpoint.remove(endpointId, requestSequence);
    }
}
