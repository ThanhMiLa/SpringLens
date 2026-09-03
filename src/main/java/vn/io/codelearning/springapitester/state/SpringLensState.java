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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@State(
    name = "SpringLensState",
    storages = @Storage("spring-lens-state.xml")
)
public class SpringLensState implements PersistentStateComponent<SpringLensState> {

    public int schemaVersion = 1;
    public Map<String, EndpointSavedState> endpoints = new HashMap<>();
    
    // Module 7: Manual structures
    public java.util.List<vn.io.codelearning.springapitester.model.FolderModel> manualFolders = new java.util.ArrayList<>();
    public java.util.List<EndpointSavedState> manualEndpoints = new java.util.ArrayList<>();
    public boolean gatewayModeEnabled = false;
    public boolean persistRequestBodies = false;
    public boolean persistResponseHistory = false;
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
        migrateLegacyCredentials();
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
        storeCredentials(saved, endpoint.getAuthConfig(), endpoint.getCustomHeaders());
        saved.requestBodyJson = persistRequestBodies ? endpoint.getRequestBodyJson() : "";
        saved.bodyType = endpoint.getBodyType();
        saved.allowInsecureTls = endpoint.isAllowInsecureTls();
        saved.isSecuredOverride = endpoint.isSecured();
        
        // Response Cache
        if (persistResponseHistory) {
            saved.lastResponseBody = endpoint.getLastResponseBody();
            saved.lastResponseStatusCode = endpoint.getLastResponseStatusCode();
            saved.lastResponseStatusMessage = endpoint.getLastResponseStatusMessage();
            saved.lastResponseTimeTakenMs = endpoint.getLastResponseTimeTakenMs();
            saved.lastResponseHeaders = redactResponseHeaders(endpoint.getLastResponseHeaders());
            saved.lastResponseFormat = endpoint.getLastResponseFormat();
        }

        // Inherit the previous override state so we don't accidentally lock all endpoints
        if (oldSaved != null) {
            saved.hasSecuredOverride = oldSaved.hasSecuredOverride;
        } else {
            saved.hasSecuredOverride = false;
        }

        saved.paramValues.clear();
        saved.paramEnabled.clear();
        for (vn.io.codelearning.springapitester.model.ParameterModel param : endpoint.getParameters()) {
            if (param.getParamType() != null && param.getName() != null) {
                String typeKey = param.getParamType().name() + ":" + param.getName();
                if (param.getCurrentValue() != null) {
                    saved.paramValues.put(typeKey, param.getCurrentValue());
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
            // Deep copy parameters
            saved.manualParameters = new java.util.ArrayList<>();
            for (vn.io.codelearning.springapitester.model.ParameterModel param : endpoint.getParameters()) {
                vn.io.codelearning.springapitester.model.ParameterModel clone = new vn.io.codelearning.springapitester.model.ParameterModel();
                clone.setName(param.getName());
                clone.setParamType(param.getParamType());
                clone.setCurrentValue(param.getCurrentValue());
                clone.setRequired(param.isRequired());
                clone.setDefaultValue(param.getDefaultValue());
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
            this.schemaVersion = 2;
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
            if (key.startsWith("manual:") || key.contains("#")) {
                continue;
            }
            List<vn.io.codelearning.springapitester.model.EndpointModel> matches = grouped.get(key);
            if (matches != null && matches.size() == 1) {
                vn.io.codelearning.springapitester.model.EndpointModel target = matches.get(0);
                String newKey = getEndpointKey(target);
                EndpointSavedState saved = endpoints.remove(key);
                if (!endpoints.containsKey(newKey)) {
                    endpoints.put(newKey, saved);
                }
            } else {
                endpoints.remove(key);
            }
        }
        this.schemaVersion = 2;
    }

    public void restoreEndpoint(vn.io.codelearning.springapitester.model.EndpointModel endpoint) {
        EndpointSavedState saved = endpoints.get(getEndpointKey(endpoint));
        if (saved == null && endpoint.isManual()) {
            saved = manualEndpoints.stream()
                    .filter(e -> e.id != null && e.id.equals(endpoint.getId()))
                    .findFirst().orElse(null);
        }
        if (saved == null && schemaVersion < 2) {
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
        if (saved.hasSecuredOverride) {
            endpoint.setSecured(saved.isSecuredOverride);
        }

        // Smart Merge Parameters
        for (vn.io.codelearning.springapitester.model.ParameterModel param : endpoint.getParameters()) {
            if (param.getParamType() != null && param.getName() != null) {
                String typeKey = param.getParamType().name() + ":" + param.getName();
                if (saved.paramValues.containsKey(typeKey)) {
                    param.setCurrentValue(saved.paramValues.get(typeKey));
                } else if (saved.paramValues.containsKey(param.getName())) {
                    param.setCurrentValue(saved.paramValues.get(param.getName()));
                }
                if (saved.paramEnabled != null && saved.paramEnabled.containsKey(typeKey)) {
                    param.setEnabled(saved.paramEnabled.get(typeKey));
                }
            }
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
        }
        this.endpoints.clear();
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
        CredentialStore store = credentialStore();
        if (store != null) store.save(saved.credentialId, authConfig, headers);
        saved.authConfig = CredentialStore.sanitizeAuth(authConfig);
    }

    private void attachProject(Project project) {
        if (this.project == null) {
            this.project = project;
            migrateLegacyCredentials();
        }
    }

    private CredentialStore credentialStore() {
        if (credentialStoreOverride != null) return credentialStoreOverride;
        return project != null && !project.isDisposed() ? new CredentialStore(project) : null;
    }

    void attachCredentialStoreForTest(CredentialStore store) {
        this.credentialStoreOverride = store;
        migrateLegacyCredentials();
    }

    private void storeCredentials(EndpointSavedState saved, AuthConfig authConfig,
                                  List<vn.io.codelearning.springapitester.model.HeaderItem> headers) {
        Map<Integer, String> headerSecrets = new HashMap<>();
        saved.authConfig = CredentialStore.sanitizeAuth(authConfig);
        saved.customHeaders = CredentialStore.sanitizeHeaders(headers, headerSecrets);
        CredentialStore store = credentialStore();
        if (store != null) store.save(saved.credentialId, authConfig, headerSecrets);
    }

    private CredentialStore.StoredSecrets loadSecrets(EndpointSavedState saved) {
        CredentialStore store = credentialStore();
        return store != null && saved != null ? store.load(saved.credentialId) : null;
    }

    private void migrateLegacyCredentials() {
        CredentialStore store = credentialStore();
        if (store == null) return;
        for (EndpointSavedState saved : allSavedEndpoints()) {
            if (CredentialStore.containsSecrets(saved.authConfig, saved.customHeaders)) {
                if (saved.credentialId == null || saved.credentialId.isBlank()) saved.credentialId = UUID.randomUUID().toString();
                storeCredentials(saved, saved.authConfig, saved.customHeaders);
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
