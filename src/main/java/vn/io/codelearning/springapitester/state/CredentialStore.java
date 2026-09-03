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

    public CredentialStore(String scope, Backend backend) {
        this.scope = scope;
        this.backend = backend;
    }

    public void save(String credentialId, AuthConfig authConfig, Map<Integer, String> secretHeaders) {
        save(credentialId, authConfig, secretHeaders, java.util.Collections.emptyMap());
    }

    public void save(String credentialId, AuthConfig authConfig, Map<Integer, String> secretHeaders, Map<String, String> secretParams) {
        StoredSecrets secrets = new StoredSecrets();
        secrets.authConfig = authConfig != null ? authConfig.cloneConfig() : new AuthConfig();
        if (secretHeaders != null) secrets.headerValues.putAll(secretHeaders);
        if (secretParams != null) secrets.parameterValues.putAll(secretParams);
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
        return SensitiveValueClassifier.isSensitiveHeader(key);
    }

    public static final class StoredSecrets {
        public AuthConfig authConfig = new AuthConfig();
        public Map<Integer, String> headerValues = new HashMap<>();
        public Map<String, String> parameterValues = new HashMap<>();
    }

    public interface Backend {
        String get(String serviceName);
        void set(String serviceName, String value);
    }

    public static final class MemoryBackend implements Backend {
        private final Map<String, String> values = new HashMap<>();

        @Override
        public String get(String serviceName) {
            return values.get(serviceName);
        }

        @Override
        public void set(String serviceName, String value) {
            if (value == null) values.remove(serviceName);
            else values.put(serviceName, value);
        }
    }

    private static final class PasswordSafeBackend implements Backend {
        private final Map<String, String> cache = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public String get(String serviceName) {
            String cached = cache.get(serviceName);
            if (cached != null) return cached;
            try {
                Credentials credentials = PasswordSafe.getInstance().get(new CredentialAttributes(serviceName, "springlens"));
                String val = credentials != null ? credentials.getPasswordAsString() : null;
                if (val != null) cache.put(serviceName, val);
                return val;
            } catch (Throwable t) {
                return null;
            }
        }

        @Override
        public void set(String serviceName, String value) {
            if (value == null) {
                cache.remove(serviceName);
            } else {
                cache.put(serviceName, value);
            }
            CredentialAttributes attributes = new CredentialAttributes(serviceName, "springlens");
            Credentials credentials = value != null ? new Credentials("springlens", value) : null;
            com.intellij.openapi.application.Application app = com.intellij.openapi.application.ApplicationManager.getApplication();
            if (app != null && app.isDispatchThread()) {
                app.executeOnPooledThread(() -> {
                    try {
                        PasswordSafe.getInstance().set(attributes, credentials);
                    } catch (Throwable ignored) {}
                });
            } else {
                try {
                    PasswordSafe.getInstance().set(attributes, credentials);
                } catch (Throwable ignored) {}
            }
        }
    }
}
