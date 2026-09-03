package vn.io.codelearning.springapitester.client;

import vn.io.codelearning.springapitester.model.EndpointIdentity;
import vn.io.codelearning.springapitester.model.EndpointModel;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Ngữ cảnh thực thi request bất đồng bộ, gắn liền với EndpointIdentity,
 * sequence monotonic và UI generation token để ngăn chặn race condition và memory leak.
 */
public final class RequestExecutionContext {

    private final EndpointIdentity endpointIdentity;
    private final EndpointModel targetEndpoint;
    private final long sequence;
    private final long uiGeneration;
    private final AtomicReference<RequestExecutionState> state = new AtomicReference<>(RequestExecutionState.IN_FLIGHT);
    private volatile HttpClientService.RequestHandle requestHandle;

    public RequestExecutionContext(EndpointIdentity endpointIdentity, EndpointModel targetEndpoint,
                                  long sequence, long uiGeneration) {
        this.endpointIdentity = Objects.requireNonNull(endpointIdentity, "endpointIdentity must not be null");
        this.targetEndpoint = targetEndpoint;
        this.sequence = sequence;
        this.uiGeneration = uiGeneration;
    }

    public EndpointIdentity getEndpointIdentity() {
        return endpointIdentity;
    }

    public EndpointModel getTargetEndpoint() {
        return targetEndpoint;
    }

    public long getSequence() {
        return sequence;
    }

    public long getUiGeneration() {
        return uiGeneration;
    }

    public RequestExecutionState getState() {
        return state.get();
    }

    public void setRequestHandle(HttpClientService.RequestHandle requestHandle) {
        this.requestHandle = requestHandle;
        if (state.get().isTerminal() && requestHandle != null) {
            requestHandle.cancel();
        }
    }

    public HttpClientService.RequestHandle getRequestHandle() {
        return requestHandle;
    }

    /**
     * Chuyển đổi trạng thái sang terminal state (SUCCESS, FAILED, CANCELED, SUPERSEDED, DISPOSED)
     * một cách idempotent và an toàn luồng (CAS).
     */
    public boolean transitionToTerminal(RequestExecutionState terminalState) {
        if (!terminalState.isTerminal()) {
            throw new IllegalArgumentException("Target state must be terminal: " + terminalState);
        }
        while (true) {
            RequestExecutionState current = state.get();
            if (current.isTerminal()) {
                return false;
            }
            if (state.compareAndSet(current, terminalState)) {
                if (terminalState == RequestExecutionState.CANCELED
                        || terminalState == RequestExecutionState.SUPERSEDED
                        || terminalState == RequestExecutionState.DISPOSED) {
                    if (requestHandle != null) {
                        requestHandle.cancel();
                    }
                }
                return true;
            }
        }
    }

    public void cancel() {
        transitionToTerminal(RequestExecutionState.CANCELED);
    }

    public void supersede() {
        transitionToTerminal(RequestExecutionState.SUPERSEDED);
    }

    public void dispose() {
        transitionToTerminal(RequestExecutionState.DISPOSED);
    }

    public boolean isTerminal() {
        return state.get().isTerminal();
    }

    /**
     * Kiểm tra context này có hợp lệ để render lên UI hiện tại hay không.
     */
    public boolean canRenderToUi(EndpointIdentity visibleIdentity, long currentUiGeneration) {
        if (isTerminal()) return false;
        if (currentUiGeneration != this.uiGeneration) return false;
        return Objects.equals(this.endpointIdentity, visibleIdentity);
    }
}
