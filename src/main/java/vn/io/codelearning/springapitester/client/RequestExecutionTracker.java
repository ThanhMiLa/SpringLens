package vn.io.codelearning.springapitester.client;

import vn.io.codelearning.springapitester.model.EndpointIdentity;
import vn.io.codelearning.springapitester.model.EndpointModel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Quản lý vòng đời thực thi các request bất đồng bộ, sử dụng RequestExecutionContext
 * để đảm bảo tính tuần tự, ngăn ngừa out-of-order responses và triệt tiêu race condition.
 */
public final class RequestExecutionTracker {

    private final AtomicLong sequenceGenerator = new AtomicLong();
    private final Map<String, Long> latestSequenceByEndpoint = new ConcurrentHashMap<>();
    private final Map<String, RequestExecutionContext> activeContexts = new ConcurrentHashMap<>();

    /**
     * Bắt đầu một execution context mới cho endpointModel với uiGeneration token.
     * Nếu endpoint đang có request in-flight trước đó, request cũ sẽ bị SUPERSEDED ngay lập tức.
     */
    public RequestExecutionContext begin(EndpointModel endpoint, long uiGeneration) {
        EndpointIdentity identity = EndpointIdentity.fromEndpoint(endpoint);
        String key = identity.getKey();
        long seq = sequenceGenerator.incrementAndGet();
        latestSequenceByEndpoint.put(key, seq);

        RequestExecutionContext oldContext = activeContexts.get(key);
        if (oldContext != null && !oldContext.isTerminal()) {
            oldContext.supersede();
        }

        RequestExecutionContext newContext = new RequestExecutionContext(identity, endpoint, seq, uiGeneration);
        activeContexts.put(key, newContext);
        return newContext;
    }

    /**
     * Backward-compatible helper method.
     */
    public long begin(String endpointId) {
        long value = sequenceGenerator.incrementAndGet();
        latestSequenceByEndpoint.put(endpointId, value);
        return value;
    }

    public boolean isLatest(String endpointId, long requestSequence) {
        return latestSequenceByEndpoint.getOrDefault(endpointId, -1L) == requestSequence;
    }

    public void clear(String endpointId, long requestSequence) {
        latestSequenceByEndpoint.remove(endpointId, requestSequence);
        RequestExecutionContext context = activeContexts.get(endpointId);
        if (context != null && context.getSequence() == requestSequence) {
            activeContexts.remove(endpointId);
        }
    }

    public void cancel(EndpointModel endpoint) {
        if (endpoint == null) return;
        String key = EndpointIdentity.fromEndpoint(endpoint).getKey();
        RequestExecutionContext context = activeContexts.remove(key);
        if (context != null) {
            context.cancel();
        }
    }

    public void cancelAll() {
        for (RequestExecutionContext context : activeContexts.values()) {
            context.cancel();
        }
        activeContexts.clear();
        latestSequenceByEndpoint.clear();
    }

    public void dispose() {
        for (RequestExecutionContext context : activeContexts.values()) {
            context.dispose();
        }
        activeContexts.clear();
        latestSequenceByEndpoint.clear();
    }

    public RequestExecutionContext getActiveContext(EndpointModel endpoint) {
        if (endpoint == null) return null;
        String key = EndpointIdentity.fromEndpoint(endpoint).getKey();
        return activeContexts.get(key);
    }
}
