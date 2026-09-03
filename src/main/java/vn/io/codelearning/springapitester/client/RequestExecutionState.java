package vn.io.codelearning.springapitester.client;

/**
 * Các trạng thái vòng đời của một request bất đồng bộ.
 */
public enum RequestExecutionState {
    IDLE,
    IN_FLIGHT,
    SUCCESS,
    FAILED,
    CANCELED,
    SUPERSEDED,
    DISPOSED;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELED || this == SUPERSEDED || this == DISPOSED;
    }
}
