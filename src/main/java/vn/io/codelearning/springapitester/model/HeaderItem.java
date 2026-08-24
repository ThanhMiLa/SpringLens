package vn.io.codelearning.springapitester.model;

import java.util.Objects;

/**
 * Đại diện cho một cặp Key-Value trong bảng Headers của Request.
 */
public class HeaderItem {
    private String key;
    private String value;
    private boolean enabled;

    public HeaderItem() {
        this("", "", true);
    }

    public HeaderItem(String key, String value) {
        this(key, value, true);
    }

    public HeaderItem(String key, String value, boolean enabled) {
        this.key = key;
        this.value = value;
        this.enabled = enabled;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HeaderItem that = (HeaderItem) o;
        return Objects.equals(key, that.key) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value);
    }

    @Override
    public String toString() {
        return (enabled ? "[x] " : "[ ] ") + key + ": " + value;
    }
}
