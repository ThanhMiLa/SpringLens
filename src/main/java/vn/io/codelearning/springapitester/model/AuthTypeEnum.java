package vn.io.codelearning.springapitester.model;

/**
 * Các loại hình xác thực (Authentication Types) hỗ trợ trong tab Authorization.
 */
public enum AuthTypeEnum {
    INHERIT("Inherit auth from project"),
    NO_AUTH("No Auth"),
    BEARER_TOKEN("Bearer Token"),
    BASIC_AUTH("Basic Auth"),
    API_KEY("API Key");

    private final String displayName;

    AuthTypeEnum(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
