package vn.io.codelearning.springapitester.state;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import vn.io.codelearning.springapitester.model.AuthConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@State(
    name = "SpringLensState",
    storages = @Storage("spring-lens-state.xml")
)
public class SpringLensState implements PersistentStateComponent<SpringLensState> {

    public int schemaVersion = 3;
    public Map<String, EndpointSavedState> endpoints = new HashMap<>();
    public Map<String, EndpointSavedState> quarantinedEndpoints = new HashMap<>();
    
    // Module 7: Manual structures
    public java.util.List<vn.io.codelearning.springapitester.model.FolderModel> manualFolders = new java.util.ArrayList<>();
    public java.util.List<EndpointSavedState> manualEndpoints = new java.util.ArrayList<>();
    public boolean gatewayModeEnabled = false;
    public boolean persistRequestBodies = true;
    public boolean persistResponseHistory = true;
    private transient Project project;
    private transient CredentialStore credentialStoreOverride;

    public static SpringLensState getInstance(Project project) {
        SpringLensState state = project.getService(SpringLensState.class);
        if (state != null) state.attachProject(project);
        return state;
    }

    @Nullable
    @Override
    public SpringLensState getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull SpringLensState state) {
        this.schemaVersion = state.schemaVersion;
        this.endpoints = state.endpoints != null ? state.endpoints : new HashMap<>();
        if (state.quarantinedEndpoints != null) {
            this.quarantinedEndpoints = state.quarantinedEndpoints;
        }
        if (state.manualFolders != null) {
            this.manualFolders = state.manualFolders;
        }
        if (state.manualEndpoints != null) {
            this.manualEndpoints = state.manualEndpoints;
            for (EndpointSavedState manual : this.manualEndpoints) {
                if (manual.path != null) {
                    String sanitized = vn.io.codelearning.springapitester.util.UrlResolutionUtil.sanitizeCorruptedUrl(manual.path);
                    if (!sanitized.equals(manual.path)) {
                        manual.path = sanitized;
                        manual.isAbsoluteUrl = true;
                    } else if (vn.io.codelearning.springapitester.util.UrlResolutionUtil.isAbsoluteUrl(manual.path)) {
                        manual.isAbsoluteUrl = true;
                    }
                }
            }
        }
        this.gatewayModeEnabled = state.gatewayModeEnabled;
        this.persistRequestBodies = state.persistRequestBodies;
        this.persistResponseHistory = state.persistResponseHistory;
        this.needsCredentialMigration = true;
    }

    public String getEndpointKey(vn.io.codelearning.springapitester.model.EndpointModel endpoint) {
        return vn.io.codelearning.springapitester.model.EndpointIdentity.createKey(endpoint);
    }

    public void saveEndpoint(vn.io.codelearning.springapitester.model.EndpointModel endpoint) {
        if (endpoint == null) return;
        EndpointSavedState saved = new EndpointSavedState();
        EndpointSavedState oldSaved = endpoint.isManual()
                ? manualEndpoints.stream().filter(e -> e.id != null && e.id.equals(endpoint.getId())).findFirst().orElse(null)
                : endpoints.get(getEndpointKey(endpoint));
        saved.credentialId = oldSaved != null && oldSaved.credentialId != null && !oldSaved.credentialId.isBlank()
                ? oldSaved.credentialId : UUID.randomUUID().toString();
        saved.requestBodyJson = persistRequestBodies ? endpoint.getRequestBodyJson() : "";
        saved.bodyType = endpoint.getBodyType();
        saved.allowInsecureTls = endpoint.isAllowInsecureTls();
        if (endpoint.getInsecureTlsConsent() != null) {
            saved.insecureTlsConsentHost = endpoint.getInsecureTlsConsent().getNormalizedHost();
            saved.insecureTlsConsentVersion = endpoint.getInsecureTlsConsent().getPolicyVersion();
        } else {
            saved.insecureTlsConsentHost = "";
            saved.insecureTlsConsentVersion = 0;
        }
        saved.isSecuredOverride = endpoint.isSecured();
        
        // Response Cache
        if (persistResponseHistory) {
            String rawBody = endpoint.getLastResponseBody();
            rawBody = SensitiveValueClassifier.redactSensitiveJson(rawBody);
            String persistedBody;
            if (rawBody != null && rawBody.length() > MAX_PERSISTED_BODY_BYTES) {
                byte[] rawBytes = rawBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                int safeBoundary = vn.io.codelearning.springapitester.client.ResponseReader.findSafeUtf8Boundary(
                        rawBytes, Math.min(rawBytes.length, MAX_PERSISTED_BODY_BYTES));
                persistedBody = new String(rawBytes, 0, safeBoundary, java.nio.charset.StandardCharsets.UTF_8)
                        + "\n\n... [truncated: showing " + safeBoundary + " of " + rawBytes.length + " bytes] --- [Persisted snapshot truncated at 256 KB] ---";
            } else {
                persistedBody = rawBody != null ? rawBody : "";
            }
            saved.lastResponseBody = persistedBody;
            saved.lastResponseStatusCode = endpoint.getLastResponseStatusCode();
            saved.lastResponseStatusMessage = endpoint.getLastResponseStatusMessage();
            saved.lastResponseTimeTakenMs = endpoint.getLastResponseTimeTakenMs();
            saved.lastResponseHeaders = redactResponseHeaders(endpoint.getLastResponseHeaders());
            saved.lastResponseFormat = endpoint.getLastResponseFormat();

            if (oldSaved != null && oldSaved.responseHistory != null) {
                saved.responseHistory.addAll(oldSaved.responseHistory);
            }
            if (endpoint.getLastResponseStatusCode() > 0) {
                saved.responseHistory.add(new EndpointSavedState.ResponseHistoryEntry(
                        saved.lastResponseStatusCode,
                        saved.lastResponseStatusMessage,
                        saved.lastResponseTimeTakenMs,
                        saved.lastResponseBody,
                        saved.lastResponseHeaders,
                        saved.lastResponseFormat
                ));
                while (saved.responseHistory.size() > EndpointSavedState.MAX_RESPONSE_HISTORY_ENTRIES) {
                    saved.responseHistory.remove(0);
                }
            }
        }

        // Inherit the previous override state so we don't accidentally lock all endpoints
        if (oldSaved != null) {
            saved.hasSecuredOverride = oldSaved.hasSecuredOverride;
        } else {
            saved.hasSecuredOverride = false;
        }

        Map<String, String> parameterSecrets = new HashMap<>();
        saved.paramValues.clear();
        saved.paramEnabled.clear();
        for (vn.io.codelearning.springapitester.model.ParameterModel param : endpoint.getParameters()) {
            if (param.getParamType() != null && param.getName() != null) {
                String typeKey = param.getParamType().name() + ":" + param.getName();
                String rawVal = param.getCurrentValue() != null ? param.getCurrentValue() : "";
                if (SensitiveValueClassifier.isSensitive(param.getName(), param.getParamType()) && !rawVal.isEmpty()) {
                    parameterSecrets.put(typeKey, rawVal);
                    saved.paramValues.put(typeKey, ""); // Sanitize in XML state
                } else {
                    saved.paramValues.put(typeKey, rawVal);
                }
                saved.paramEnabled.put(typeKey, param.isEnabled());
            }
        }
        
        String key = getEndpointKey(endpoint);
        if (endpoint.isManual()) {
            saved.id = endpoint.getId();
            saved.name = endpoint.getName();
            saved.isManual = true;
            saved.isAbsoluteUrl = endpoint.isAbsoluteUrl();
            saved.folderId = endpoint.getFolderId();
            saved.httpMethod = endpoint.getHttpMethod();
            saved.path = endpoint.getPath();
            // Deep copy parameters, sanitizing sensitive values in XML
            saved.manualParameters = new java.util.ArrayList<>();
            for (vn.io.codelearning.springapitester.model.ParameterModel param : endpoint.getParameters()) {
                vn.io.codelearning.springapitester.model.ParameterModel clone = new vn.io.codelearning.springapitester.model.ParameterModel();
                clone.setName(param.getName());
                clone.setParamType(param.getParamType());
                String rawVal = param.getCurrentValue() != null ? param.getCurrentValue() : "";
                String typeKey = (param.getParamType() != null ? param.getParamType().name() : "QUERY_PARAM") + ":" + param.getName();
                if (SensitiveValueClassifier.isSensitive(param.getName(), param.getParamType())) {
                    if (rawVal.isEmpty() && param.getDefaultValue() != null && !param.getDefaultValue().isEmpty()) {
                        rawVal = param.getDefaultValue();
                    }
                    if (!rawVal.isEmpty()) {
                        parameterSecrets.put(typeKey, rawVal);
                    }
                    clone.setCurrentValue(""); // Sanitize in XML state
                    clone.setDefaultValue(""); // Sanitize in XML state
                } else {
                    clone.setCurrentValue(rawVal);
                    clone.setDefaultValue(param.getDefaultValue());
                }
                clone.setRequired(param.isRequired());
                clone.setEnabled(param.isEnabled());
                saved.manualParameters.add(clone);
            }
            // Update or add
            manualEndpoints.removeIf(e -> e.id != null && e.id.equals(saved.id));
            manualEndpoints.add(saved);
            endpoints.put(key, saved);
        } else {
            endpoints.put(key, saved);
        }
        storeCredentials(saved, endpoint.getAuthConfig(), endpoint.getCustomHeaders(), parameterSecrets);
        enforceResponseStorageQuota();
    }

    public static final int MAX_PERSISTED_BODY_BYTES = 256 * 1024; // 256 KB
    public static final int MAX_TOTAL_RESPONSE_STORAGE_BYTES = 5 * 1024 * 1024; // 5 MB

    private void enforceResponseStorageQuota() {
        long totalBytes = 0;
        for (EndpointSavedState s : endpoints.values()) {
            if (s.lastResponseBody != null) {
                totalBytes += s.lastResponseBody.length();
            }
        }
        if (totalBytes > MAX_TOTAL_RESPONSE_STORAGE_BYTES) {
            for (EndpointSavedState s : endpoints.values()) {
                if (s.lastResponseBody != null && !s.lastResponseBody.isEmpty()) {
                    totalBytes -= s.lastResponseBody.length();
                    s.lastResponseBody = "";
                    if (totalBytes <= MAX_TOTAL_RESPONSE_STORAGE_BYTES) {
                        break;
                    }
                }
            }
        }
    }

    public void deleteManualEndpoint(String manualId) {
        if (manualId == null || manualId.isBlank()) return;
        EndpointSavedState removed = manualEndpoints.stream()
                .filter(e -> manualId.equals(e.id))
                .findFirst().orElse(null);
        if (removed != null) {
            manualEndpoints.remove(removed);
            if (removed.credentialId != null) {
                CredentialStore store = credentialStore();
                if (store != null) {
                    store.delete(removed.credentialId);
                }
            }
        }
        endpoints.remove("manual:" + manualId);
    }

    public void migrateLegacyKeys(List<vn.io.codelearning.springapitester.model.EndpointModel> discoveredEndpoints) {
        if (discoveredEndpoints == null || discoveredEndpoints.isEmpty()) {
            this.schemaVersion = 3;
            return;
        }

        Map<String, List<vn.io.codelearning.springapitester.model.EndpointModel>> grouped = new HashMap<>();
        for (vn.io.codelearning.springapitester.model.EndpointModel ep : discoveredEndpoints) {
            if (ep.isManual()) continue;
            String legacyKey = ep.getHttpMethod().name() + " " + ep.getPath();
            grouped.computeIfAbsent(legacyKey, k -> new ArrayList<>()).add(ep);
        }

        List<String> currentKeys = new ArrayList<>(endpoints.keySet());
        for (String key : currentKeys) {
            if (key.startsWith("scanned:") || key.startsWith("manual:")) {
                continue;
            }
            EndpointSavedState saved = endpoints.remove(key);
            if (saved == null) continue;

            if (key.contains("#")) {
                // v2 key without "scanned:" prefix
                String newKey = "scanned:" + key;
                if (!endpoints.containsKey(newKey)) {
                    endpoints.put(newKey, saved);
                } else {
                    quarantinedEndpoints.put("quarantine:collision:" + key + ":" + UUID.randomUUID(), saved);
                }
                continue;
            }

            // v1 key
            List<vn.io.codelearning.springapitester.model.EndpointModel> matches = grouped.get(key);
            if (matches != null && matches.size() == 1) {
                vn.io.codelearning.springapitester.model.EndpointModel target = matches.get(0);
                String newKey = getEndpointKey(target);
                if (!endpoints.containsKey(newKey)) {
                    endpoints.put(newKey, saved);
                } else {
                    quarantinedEndpoints.put("quarantine:collision:" + key + ":" + UUID.randomUUID(), saved);
                }
            } else if (matches != null && matches.size() > 1) {
                // Ambiguous collision: multiple endpoints match this legacy key! Quarantine rather than silently dropping.
                quarantinedEndpoints.put("quarantine:ambiguous:" + key + ":" + UUID.randomUUID(), saved);
            } else {
                quarantinedEndpoints.put("quarantine:orphan:" + key + ":" + UUID.randomUUID(), saved);
            }
        }
        this.schemaVersion = 3;
    }

    public void pruneOrphanScannedEndpoints(List<vn.io.codelearning.springapitester.model.EndpointModel> currentScannedEndpoints) {
        if (currentScannedEndpoints == null) return;
        Set<String> activeKeys = new HashSet<>();
        for (vn.io.codelearning.springapitester.model.EndpointModel ep : currentScannedEndpoints) {
            if (!ep.isManual()) {
                activeKeys.add(getEndpointKey(ep));
            }
        }
        endpoints.keySet().removeIf(key -> key.startsWith("scanned:") && !activeKeys.contains(key));
    }

    public boolean restoreQuarantinedEndpoint(String quarantineKey, vn.io.codelearning.springapitester.model.EndpointModel target) {
        if (quarantineKey == null || target == null) return false;
        EndpointSavedState saved = quarantinedEndpoints.remove(quarantineKey);
        if (saved == null) return false;
        String newKey = getEndpointKey(target);
        endpoints.put(newKey, saved);
        restoreEndpoint(target);
        return true;
    }

    public void restoreEndpoint(vn.io.codelearning.springapitester.model.EndpointModel endpoint) {
        String modernKey = getEndpointKey(endpoint);
        EndpointSavedState saved = endpoints.get(modernKey);
        if (saved == null && endpoint.isManual()) {
            saved = manualEndpoints.stream()
                    .filter(e -> e.id != null && e.id.equals(endpoint.getId()))
                    .findFirst().orElse(null);
        }
        if (saved == null && !endpoint.isManual()) {
            // Check legacy key without "scanned:" prefix
            String legacyV2Key = modernKey.startsWith("scanned:") ? modernKey.substring(8) : modernKey;
            saved = endpoints.remove(legacyV2Key);
            if (saved != null) {
                endpoints.put(modernKey, saved);
            }
        }
        if (saved == null && schemaVersion < 3) {
            String legacyKey = endpoint.getHttpMethod().name() + " " + endpoint.getPath();
            saved = endpoints.get(legacyKey);
        }
        if (saved == null) return;

        if (endpoint.isManual()) {
            endpoint.setAbsoluteUrl(saved.isAbsoluteUrl);
            if (saved.path != null && !saved.path.isEmpty()) {
                endpoint.setPath(saved.path);
            }
        }

        // Restore Auth & Headers
        endpoint.setAuthConfig(resolveAuthConfig(saved));
        endpoint.setCustomHeaders(resolveHeaders(saved));
        
        // Restore JSON Body (User can use Sync Schema button later to smart merge with new DTO changes)
        if (saved.requestBodyJson != null) {
            endpoint.setRequestBodyJson(saved.requestBodyJson);
        }
        
        // Restore Body Type and Security
        endpoint.setBodyType(saved.bodyType);
        endpoint.setAllowInsecureTls(saved.allowInsecureTls);
        if (saved.allowInsecureTls && saved.insecureTlsConsentHost != null && !saved.insecureTlsConsentHost.isEmpty()) {
            endpoint.setInsecureTlsConsent(new vn.io.codelearning.springapitester.client.InsecureTlsConsent(
                    saved.insecureTlsConsentHost, saved.insecureTlsConsentVersion));
        } else if (!saved.allowInsecureTls) {
            endpoint.revokeInsecureTlsConsent();
        }
        if (saved.hasSecuredOverride) {
            endpoint.setSecured(saved.isSecuredOverride);
        }

        CredentialStore.StoredSecrets secrets = loadSecrets(saved);

        // Smart Merge Parameters
        for (vn.io.codelearning.springapitester.model.ParameterModel param : endpoint.getParameters()) {
            if (param.getParamType() != null && param.getName() != null) {
                String typeKey = param.getParamType().name() + ":" + param.getName();
                if (secrets != null && secrets.parameterValues != null && secrets.parameterValues.containsKey(typeKey)) {
                    param.setCurrentValue(secrets.parameterValues.get(typeKey));
                } else if (saved.paramValues.containsKey(typeKey)) {
                    param.setCurrentValue(saved.paramValues.get(typeKey));
                } else if (saved.paramValues.containsKey(param.getName())) {
                    param.setCurrentValue(saved.paramValues.get(param.getName()));
                }
                if (saved.paramEnabled != null && saved.paramEnabled.containsKey(typeKey)) {
                    param.setEnabled(saved.paramEnabled.get(typeKey));
                }
            }
        }
        if (endpoint.isManual() && saved.manualParameters != null) {
            java.util.List<vn.io.codelearning.springapitester.model.ParameterModel> restored = new java.util.ArrayList<>();
            for (vn.io.codelearning.springapitester.model.ParameterModel p : saved.manualParameters) {
                vn.io.codelearning.springapitester.model.ParameterModel clone = p.clone();
                String typeKey = (p.getParamType() != null ? p.getParamType().name() : "QUERY_PARAM") + ":" + p.getName();
                if (secrets != null && secrets.parameterValues != null && secrets.parameterValues.containsKey(typeKey)) {
                    clone.setCurrentValue(secrets.parameterValues.get(typeKey));
                }
                restored.add(clone);
            }
            endpoint.setParameters(restored);
        }

        // Restore Response Cache
        endpoint.setLastResponseBody(saved.lastResponseBody);
        endpoint.setLastResponseStatusCode(saved.lastResponseStatusCode);
        endpoint.setLastResponseStatusMessage(saved.lastResponseStatusMessage);
        endpoint.setLastResponseTimeTakenMs(saved.lastResponseTimeTakenMs);
        endpoint.setLastResponseHeaders(saved.lastResponseHeaders);
        endpoint.setLastResponseFormat(saved.lastResponseFormat);
    }
    public void clearAllData() {
        CredentialStore store = credentialStore();
        if (store != null) {
            endpoints.values().forEach(saved -> store.delete(saved.credentialId));
            manualEndpoints.forEach(saved -> store.delete(saved.credentialId));
            quarantinedEndpoints.values().forEach(saved -> store.delete(saved.credentialId));
        }
        this.endpoints.clear();
        this.quarantinedEndpoints.clear();
        this.manualFolders.clear();
        this.manualEndpoints.clear();
    }

    public AuthConfig resolveAuthConfig(EndpointSavedState saved) {
        CredentialStore.StoredSecrets secrets = loadSecrets(saved);
        return secrets != null && secrets.authConfig != null
                ? secrets.authConfig.cloneConfig()
                : CredentialStore.sanitizeAuth(saved.authConfig);
    }

    public List<vn.io.codelearning.springapitester.model.HeaderItem> resolveHeaders(EndpointSavedState saved) {
        CredentialStore.StoredSecrets secrets = loadSecrets(saved);
        return CredentialStore.restoreHeaders(saved.customHeaders, secrets != null ? secrets.headerValues : null);
    }

    public void updateSavedAuthConfig(EndpointSavedState saved, vn.io.codelearning.springapitester.model.AuthConfig authConfig) {
        if (saved == null) return;
        if (saved.credentialId == null || saved.credentialId.isBlank()) saved.credentialId = UUID.randomUUID().toString();
        CredentialStore.StoredSecrets existing = loadSecrets(saved);
        Map<Integer, String> headers = existing != null ? existing.headerValues : new HashMap<>();
        Map<String, String> params = existing != null ? existing.parameterValues : new HashMap<>();
        CredentialStore store = credentialStore();
        if (store != null) store.save(saved.credentialId, authConfig, headers, params);
        saved.authConfig = CredentialStore.sanitizeAuth(authConfig);
    }

    private boolean needsCredentialMigration = false;

    private void attachProject(Project project) {
        if (this.project == null) {
            this.project = project;
            drainFallbackToPasswordSafe();
            if (needsCredentialMigration) {
                migrateLegacyCredentials();
                needsCredentialMigration = false;
            }
        }
    }

    private void drainFallbackToPasswordSafe() {
        if (fallbackMemoryStore == null) return;
        CredentialStore store = credentialStore();
        if (store == null || store == fallbackMemoryStore) return;
        for (EndpointSavedState saved : allSavedEndpoints()) {
            if (saved.credentialId != null && !saved.credentialId.isBlank()) {
                CredentialStore.StoredSecrets fromMemory = fallbackMemoryStore.load(saved.credentialId);
                if (fromMemory != null) {
                    store.save(saved.credentialId, fromMemory.authConfig, fromMemory.headerValues, fromMemory.parameterValues);
                }
            }
        }
    }

    private final CredentialStore fallbackMemoryStore = new CredentialStore("memory", new CredentialStore.MemoryBackend());

    private CredentialStore credentialStore() {
        if (credentialStoreOverride != null) return credentialStoreOverride;
        if (project != null && !project.isDisposed()) return new CredentialStore(project);
        return fallbackMemoryStore;
    }

    void attachCredentialStoreForTest(CredentialStore store) {
        this.credentialStoreOverride = store;
        drainFallbackToPasswordSafe();
        migrateLegacyCredentials();
        this.needsCredentialMigration = false;
    }

    private void storeCredentials(EndpointSavedState saved, AuthConfig authConfig,
                                  List<vn.io.codelearning.springapitester.model.HeaderItem> headers,
                                  Map<String, String> parameterSecrets) {
        Map<Integer, String> headerSecrets = new HashMap<>();
        saved.authConfig = CredentialStore.sanitizeAuth(authConfig);
        saved.customHeaders = CredentialStore.sanitizeHeaders(headers, headerSecrets);
        CredentialStore store = credentialStore();
        if (store != null) store.save(saved.credentialId, authConfig, headerSecrets, parameterSecrets);
    }

    private CredentialStore.StoredSecrets loadSecrets(EndpointSavedState saved) {
        CredentialStore store = credentialStore();
        return store != null && saved != null ? store.load(saved.credentialId) : null;
    }

    private void migrateLegacyCredentials() {
        CredentialStore store = credentialStore();
        if (store == null) return;
        for (EndpointSavedState saved : allSavedEndpoints()) {
            boolean hasAuthSecrets = CredentialStore.containsSecrets(saved.authConfig, saved.customHeaders);
            boolean hasParamSecrets = false;
            Map<String, String> paramSecrets = new HashMap<>();
            for (Map.Entry<String, String> entry : saved.paramValues.entrySet()) {
                String key = entry.getKey();
                int idx = key.indexOf(':');
                String name = idx >= 0 ? key.substring(idx + 1) : key;
                String typeStr = idx >= 0 ? key.substring(0, idx) : "QUERY_PARAM";
                vn.io.codelearning.springapitester.model.ParamTypeEnum type = null;
                try { type = vn.io.codelearning.springapitester.model.ParamTypeEnum.valueOf(typeStr); } catch (Exception ignored) {}
                if (SensitiveValueClassifier.isSensitive(name, type) && entry.getValue() != null && !entry.getValue().isEmpty()) {
                    hasParamSecrets = true;
                    paramSecrets.put(key, entry.getValue());
                }
            }
            if (hasAuthSecrets || hasParamSecrets) {
                if (saved.credentialId == null || saved.credentialId.isBlank()) saved.credentialId = UUID.randomUUID().toString();
                Map<Integer, String> headerSecrets = new HashMap<>();
                for (int i = 0; i < saved.customHeaders.size(); i++) {
                    vn.io.codelearning.springapitester.model.HeaderItem item = saved.customHeaders.get(i);
                    if (SensitiveValueClassifier.isSensitiveHeader(item.getKey()) && item.getValue() != null && !item.getValue().isEmpty()) {
                        headerSecrets.put(i, item.getValue());
                    }
                }
                // 1. Write to PasswordSafe
                store.save(saved.credentialId, saved.authConfig, headerSecrets, paramSecrets);
                // 2. Transactionally verify retrieval
                CredentialStore.StoredSecrets retrieved = store.load(saved.credentialId);
                if (retrieved != null) {
                    // 3. Sanitize in state XML only after verification
                    saved.authConfig = CredentialStore.sanitizeAuth(saved.authConfig);
                    saved.customHeaders = CredentialStore.sanitizeHeaders(saved.customHeaders, new HashMap<>());
                    for (String secretKey : paramSecrets.keySet()) {
                        saved.paramValues.put(secretKey, "");
                    }
                    if (saved.manualParameters != null) {
                        for (vn.io.codelearning.springapitester.model.ParameterModel p : saved.manualParameters) {
                            if (SensitiveValueClassifier.isSensitive(p.getName(), p.getParamType())) {
                                p.setCurrentValue("");
                            }
                        }
                    }
                }
            }
        }
    }

    private List<EndpointSavedState> allSavedEndpoints() {
        List<EndpointSavedState> result = new java.util.ArrayList<>(endpoints.values());
        result.addAll(manualEndpoints);
        return result;
    }

    static String redactResponseHeaders(String headers) {
        if (headers == null || headers.isBlank()) return headers != null ? headers : "";
        StringBuilder result = new StringBuilder();
        for (String line : headers.split("\\R", -1)) {
            int separator = line.indexOf(':');
            if (separator > 0 && CredentialStore.isSensitiveHeader(line.substring(0, separator))) {
                result.append(line, 0, separator + 1).append(" [REDACTED]");
            } else {
                result.append(line);
            }
            result.append('\n');
        }
        if (!headers.endsWith("\n") && result.length() > 0) result.setLength(result.length() - 1);
        return result.toString();
    }
}
