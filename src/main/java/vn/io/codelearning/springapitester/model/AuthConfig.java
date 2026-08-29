package vn.io.codelearning.springapitester.model;

import java.util.Objects;

/**
 * Cấu hình xác thực cho từng Endpoint hoặc dùng chung cho toàn bộ Project.
 */
public class AuthConfig {
    private AuthTypeEnum authType;
    private String bearerToken;
    private String username;
    private String password;
    private String apiKeyName;
    private String apiKeyValue;
    private boolean apiKeyInHeader; // true: Header, false: Query Params

    public AuthConfig() {
        this.authType = AuthTypeEnum.INHERIT;
        this.bearerToken = "";
        this.username = "";
        this.password = "";
        this.apiKeyName = "X-API-Key";
        this.apiKeyValue = "";
        this.apiKeyInHeader = true;
    }

    public AuthTypeEnum getAuthType() {
        return authType;
    }

    public void setAuthType(AuthTypeEnum authType) {
        this.authType = (authType != null) ? authType : AuthTypeEnum.INHERIT;
    }

    public String getBearerToken() {
        return bearerToken;
    }

    public void setBearerToken(String bearerToken) {
        this.bearerToken = (bearerToken != null) ? bearerToken : "";
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = (username != null) ? username : "";
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = (password != null) ? password : "";
    }

    public String getApiKeyName() {
        return apiKeyName;
    }

    public void setApiKeyName(String apiKeyName) {
        this.apiKeyName = (apiKeyName != null) ? apiKeyName : "";
    }

    public String getApiKeyValue() {
        return apiKeyValue;
    }

    public void setApiKeyValue(String apiKeyValue) {
        this.apiKeyValue = (apiKeyValue != null) ? apiKeyValue : "";
    }

    public boolean isApiKeyInHeader() {
        return apiKeyInHeader;
    }

    public void setApiKeyInHeader(boolean apiKeyInHeader) {
        this.apiKeyInHeader = apiKeyInHeader;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuthConfig that = (AuthConfig) o;
        return apiKeyInHeader == that.apiKeyInHeader &&
                authType == that.authType &&
                Objects.equals(bearerToken, that.bearerToken) &&
                Objects.equals(username, that.username) &&
                Objects.equals(password, that.password) &&
                Objects.equals(apiKeyName, that.apiKeyName) &&
                Objects.equals(apiKeyValue, that.apiKeyValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(authType, bearerToken, username, password, apiKeyName, apiKeyValue, apiKeyInHeader);
    }
    
    public AuthConfig cloneConfig() {
        AuthConfig clone = new AuthConfig();
        clone.setAuthType(this.authType);
        clone.setBearerToken(this.bearerToken);
        clone.setUsername(this.username);
        clone.setPassword(this.password);
        clone.setApiKeyName(this.apiKeyName);
        clone.setApiKeyValue(this.apiKeyValue);
        clone.setApiKeyInHeader(this.apiKeyInHeader);
        return clone;
    }
}
