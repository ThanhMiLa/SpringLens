package vn.io.codelearning.springapitester.state;

import com.google.gson.Gson;
import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.CredentialAttributesKt;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.project.Project;
import vn.io.codelearning.springapitester.model.AuthConfig;
import vn.io.codelearning.springapitester.model.HeaderItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Stores endpoint secrets outside project XML using IntelliJ PasswordSafe. */
public final class CredentialStore {
    private static final Gson GSON = new Gson();
    private final String scope;
    private final Backend backend;

    public CredentialStore(Project project) {
        this(project.getLocationHash(), new PasswordSafeBackend());
    }

    CredentialStore(String scope, Backend backend) {
        this.scope = scope;
        this.backend = backend;
    }

    public void save(String credentialId, AuthConfig authConfig, Map<Integer, String> secretHeaders) {
        StoredSecrets secrets = new StoredSecrets();
        secrets.authConfig = authConfig != null ? authConfig.cloneConfig() : new AuthConfig();
        if (secretHeaders != null) secrets.headerValues.putAll(secretHeaders);
        backend.set(serviceName(credentialId), GSON.toJson(secrets));
    }

    public StoredSecrets load(String credentialId) {
        if (credentialId == null || credentialId.isBlank()) return null;
        String json = backend.get(serviceName(credentialId));
        if (json == null || json.isBlank()) return null;
        try {
            StoredSecrets result = GSON.fromJson(json, StoredSecrets.class);
            return result != null ? result : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public void delete(String credentialId) {
        if (credentialId != null && !credentialId.isBlank()) {
            backend.set(serviceName(credentialId), null);
        }
    }

    private String serviceName(String credentialId) {
        return CredentialAttributesKt.generateServiceName("SpringLens", scope + ":" + credentialId);
    }

    public static AuthConfig sanitizeAuth(AuthConfig source) {
        AuthConfig sanitized = new AuthConfig();
        if (source == null) return sanitized;
        sanitized.setAuthType(source.getAuthType());
        sanitized.setApiKeyName(source.getApiKeyName());
        sanitized.setApiKeyInHeader(source.isApiKeyInHeader());
        return sanitized;
    }

    public static List<HeaderItem> sanitizeHeaders(List<HeaderItem> source, Map<Integer, String> secrets) {
        List<HeaderItem> sanitized = new ArrayList<>();
        if (source == null) return sanitized;
        for (int i = 0; i < source.size(); i++) {
            HeaderItem item = source.get(i);
            String value = item.getValue() != null ? item.getValue() : "";
            if (isSensitiveHeader(item.getKey()) && !value.isEmpty()) {
                secrets.put(i, value);
                value = "";
            }
            sanitized.add(new HeaderItem(item.getKey(), value, item.isEnabled()));
        }
        return sanitized;
    }

    public static List<HeaderItem> restoreHeaders(List<HeaderItem> sanitized, Map<Integer, String> secrets) {
        List<HeaderItem> restored = new ArrayList<>();
        if (sanitized == null) return restored;
        for (int i = 0; i < sanitized.size(); i++) {
            HeaderItem item = sanitized.get(i);
            String value = secrets != null && secrets.containsKey(i) ? secrets.get(i) : item.getValue();
            restored.add(new HeaderItem(item.getKey(), value, item.isEnabled()));
        }
        return restored;
    }

    public static boolean containsSecrets(AuthConfig authConfig, List<HeaderItem> headers) {
        if (authConfig != null && (!authConfig.getBearerToken().isEmpty()
                || !authConfig.getUsername().isEmpty()
                || !authConfig.getPassword().isEmpty()
                || !authConfig.getApiKeyValue().isEmpty())) return true;
        if (headers != null) {
            for (HeaderItem header : headers) {
                if (isSensitiveHeader(header.getKey()) && header.getValue() != null && !header.getValue().isEmpty()) return true;
            }
        }
        return false;
    }

    public static boolean isSensitiveHeader(String key) {
        if (key == null) return false;
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("authorization")
                || normalized.equals("proxy-authorization")
                || normalized.equals("cookie")
                || normalized.equals("set-cookie")
                || normalized.contains("api-key")
                || normalized.contains("apikey")
                || normalized.contains("token")
                || normalized.contains("secret");
    }

    public static final class StoredSecrets {
        public AuthConfig authConfig = new AuthConfig();
        public Map<Integer, String> headerValues = new HashMap<>();
    }

    interface Backend {
        String get(String serviceName);
        void set(String serviceName, String value);
    }

    private static final class PasswordSafeBackend implements Backend {
        @Override
        public String get(String serviceName) {
            Credentials credentials = PasswordSafe.getInstance().get(new CredentialAttributes(serviceName, "springlens"));
            return credentials != null ? credentials.getPasswordAsString() : null;
        }

        @Override
        public void set(String serviceName, String value) {
            CredentialAttributes attributes = new CredentialAttributes(serviceName, "springlens");
            PasswordSafe.getInstance().set(attributes, value != null ? new Credentials("springlens", value) : null);
        }
    }
}
