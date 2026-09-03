package vn.io.codelearning.springapitester.state;

import org.junit.Assert;
import org.junit.Test;
import vn.io.codelearning.springapitester.model.AuthConfig;
import vn.io.codelearning.springapitester.model.AuthTypeEnum;
import vn.io.codelearning.springapitester.model.HeaderItem;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CredentialStoreTest {

    @Test
    public void testSecretsAreSeparatedFromSerializableState() {
        MemoryBackend backend = new MemoryBackend();
        CredentialStore store = new CredentialStore("project", backend);
        AuthConfig auth = new AuthConfig();
        auth.setAuthType(AuthTypeEnum.BASIC_AUTH);
        auth.setUsername("alice");
        auth.setPassword("password123");
        auth.setApiKeyName("X-API-Key");
        auth.setApiKeyValue("api-secret");

        List<HeaderItem> headers = List.of(
                new HeaderItem("Accept", "application/json"),
                new HeaderItem("Authorization", "Bearer token"),
                new HeaderItem("X-Custom-Token", "header-secret")
        );
        Map<Integer, String> headerSecrets = new HashMap<>();
        List<HeaderItem> sanitizedHeaders = CredentialStore.sanitizeHeaders(headers, headerSecrets);
        AuthConfig sanitizedAuth = CredentialStore.sanitizeAuth(auth);
        store.save("credential-1", auth, headerSecrets);

        Assert.assertEquals("", sanitizedAuth.getUsername());
        Assert.assertEquals("", sanitizedAuth.getPassword());
        Assert.assertEquals("", sanitizedAuth.getApiKeyValue());
        Assert.assertEquals("application/json", sanitizedHeaders.get(0).getValue());
        Assert.assertEquals("", sanitizedHeaders.get(1).getValue());
        Assert.assertEquals("", sanitizedHeaders.get(2).getValue());

        CredentialStore.StoredSecrets loaded = store.load("credential-1");
        Assert.assertEquals("alice", loaded.authConfig.getUsername());
        Assert.assertEquals("password123", loaded.authConfig.getPassword());
        Assert.assertEquals("api-secret", loaded.authConfig.getApiKeyValue());
        Assert.assertEquals("Bearer token", loaded.headerValues.get(1));
        Assert.assertEquals("header-secret", loaded.headerValues.get(2));

        List<HeaderItem> restored = CredentialStore.restoreHeaders(sanitizedHeaders, loaded.headerValues);
        Assert.assertEquals("Bearer token", restored.get(1).getValue());
        Assert.assertEquals("header-secret", restored.get(2).getValue());
    }

    @Test
    public void testCredentialDeletion() {
        MemoryBackend backend = new MemoryBackend();
        CredentialStore store = new CredentialStore("project", backend);
        store.save("credential-1", new AuthConfig(), new HashMap<>());
        Assert.assertNotNull(store.load("credential-1"));
        store.delete("credential-1");
        Assert.assertNull(store.load("credential-1"));
    }

    @Test
    public void testLegacyPlaintextStateMigratesToSecureStore() {
        MemoryBackend backend = new MemoryBackend();
        CredentialStore store = new CredentialStore("project", backend);
        SpringLensState state = new SpringLensState();
        EndpointSavedState legacy = new EndpointSavedState();
        legacy.authConfig.setAuthType(AuthTypeEnum.BEARER_TOKEN);
        legacy.authConfig.setBearerToken("legacy-token");
        legacy.customHeaders.add(new HeaderItem("Authorization", "Bearer legacy-header"));
        state.endpoints.put("GET /legacy", legacy);

        state.attachCredentialStoreForTest(store);

        Assert.assertFalse(legacy.credentialId.isBlank());
        Assert.assertEquals("", legacy.authConfig.getBearerToken());
        Assert.assertEquals("", legacy.customHeaders.get(0).getValue());
        Assert.assertEquals("legacy-token", state.resolveAuthConfig(legacy).getBearerToken());
        Assert.assertEquals("Bearer legacy-header", state.resolveHeaders(legacy).get(0).getValue());
    }

    @Test
    public void testLoadStateDoesNotMigrateBeforeProjectAttach() {
        SpringLensState source = new SpringLensState();
        EndpointSavedState legacy = new EndpointSavedState();
        legacy.authConfig.setAuthType(AuthTypeEnum.BEARER_TOKEN);
        legacy.authConfig.setBearerToken("legacy-token");
        legacy.customHeaders.add(new HeaderItem("Authorization", "Bearer legacy-header"));
        source.endpoints.put("GET /legacy", legacy);

        SpringLensState target = new SpringLensState();
        target.loadState(source);

        // Before project attach, credentials MUST remain in serializable state fields so they aren't lost
        EndpointSavedState loaded = target.endpoints.get("GET /legacy");
        Assert.assertEquals("legacy-token", loaded.authConfig.getBearerToken());
        Assert.assertEquals("Bearer legacy-header", loaded.customHeaders.get(0).getValue());

        // Now attach store (simulating attachProject)
        MemoryBackend backend = new MemoryBackend();
        CredentialStore store = new CredentialStore("project", backend);
        target.attachCredentialStoreForTest(store);

        // After attach, migration must have run
        Assert.assertEquals("", loaded.authConfig.getBearerToken());
        Assert.assertEquals("", loaded.customHeaders.get(0).getValue());
        Assert.assertEquals("legacy-token", target.resolveAuthConfig(loaded).getBearerToken());
        Assert.assertEquals("Bearer legacy-header", target.resolveHeaders(loaded).get(0).getValue());
    }

    private static final class MemoryBackend implements CredentialStore.Backend {
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
}
