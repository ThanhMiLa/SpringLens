package vn.io.codelearning.springapitester.state;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    public static SpringLensState getInstance(Project project) {
        return project.getService(SpringLensState.class);
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
        saved.requestBodyJson = persistRequestBodies && endpoint.getRequestBodyJson() != null
                ? endpoint.getRequestBodyJson()
                : "";
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
            saved.lastResponseHeaders = endpoint.getLastResponseHeaders();
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

        saved.paramValues.clear();
        saved.paramEnabled.clear();
        for (vn.io.codelearning.springapitester.model.ParameterModel param : endpoint.getParameters()) {
            if (param.getParamType() != null && param.getName() != null) {
                String typeKey = param.getParamType().name() + ":" + param.getName();
                String rawVal = param.getCurrentValue() != null ? param.getCurrentValue() : "";
                saved.paramValues.put(typeKey, rawVal);
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
            // Deep copy parameters so manual endpoints restore independently.
            saved.manualParameters = new java.util.ArrayList<>();
            for (vn.io.codelearning.springapitester.model.ParameterModel param : endpoint.getParameters()) {
                vn.io.codelearning.springapitester.model.ParameterModel clone = new vn.io.codelearning.springapitester.model.ParameterModel();
                clone.setName(param.getName());
                clone.setParamType(param.getParamType());
                String rawVal = param.getCurrentValue() != null ? param.getCurrentValue() : "";
                clone.setCurrentValue(rawVal);
                clone.setDefaultValue(param.getDefaultValue());
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
        saved.authConfig = endpoint.getAuthConfig() != null
                ? endpoint.getAuthConfig().cloneConfig() : new vn.io.codelearning.springapitester.model.AuthConfig();
        saved.customHeaders = copyHeaders(endpoint.getCustomHeaders());
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
        if (removed != null) manualEndpoints.remove(removed);
        endpoints.remove("manual:" + manualId);
    }

    public void migrateLegacyKeys(List<vn.io.codelearning.springapitester.model.EndpointModel> discoveredEndpoints) {
        if (discoveredEndpoints == null || discoveredEndpoints.isEmpty()) {
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
        if (currentScannedEndpoints == null || currentScannedEndpoints.isEmpty()) return;
        Set<String> activeKeys = new HashSet<>();
        for (vn.io.codelearning.springapitester.model.EndpointModel ep : currentScannedEndpoints) {
            if (!ep.isManual()) {
                activeKeys.add(getEndpointKey(ep));
            }
        }
        List<String> keysToRemove = new ArrayList<>();
        for (String key : endpoints.keySet()) {
            if (key.startsWith("scanned:") && !activeKeys.contains(key)) {
                keysToRemove.add(key);
            }
        }
        for (String key : keysToRemove) {
            endpoints.remove(key);
        }
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
        endpoint.setAuthConfig(saved.authConfig != null
                ? saved.authConfig.cloneConfig() : new vn.io.codelearning.springapitester.model.AuthConfig());
        endpoint.setCustomHeaders(copyHeaders(saved.customHeaders));
        
        // Restore JSON Body (User can use Sync Schema button later to smart merge with new DTO changes)
        if (saved.requestBodyJson != null) {
            endpoint.setRequestBodyJson(saved.requestBodyJson);
        }
        
        // Restore Body Type and Security
        endpoint.setBodyType(saved.bodyType);
        endpoint.setAllowInsecureTls(saved.allowInsecureTls);
        if (saved.allowInsecureTls && saved.insecureTlsConsentHost != null && !saved.insecureTlsConsentHost.isEmpty()
                && saved.insecureTlsConsentVersion == vn.io.codelearning.springapitester.client.InsecureTlsConsent.CURRENT_POLICY_VERSION
                && vn.io.codelearning.springapitester.client.HttpClientService.isLocalDevelopmentHost(saved.insecureTlsConsentHost)) {
            endpoint.setInsecureTlsConsent(new vn.io.codelearning.springapitester.client.InsecureTlsConsent(
                    saved.insecureTlsConsentHost, saved.insecureTlsConsentVersion));
        } else {
            endpoint.setInsecureTlsConsent(null);
        }
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
        if (endpoint.isManual() && saved.manualParameters != null) {
            java.util.List<vn.io.codelearning.springapitester.model.ParameterModel> restored = new java.util.ArrayList<>();
            for (vn.io.codelearning.springapitester.model.ParameterModel p : saved.manualParameters) {
                vn.io.codelearning.springapitester.model.ParameterModel clone = p.clone();
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
        this.endpoints.clear();
        this.quarantinedEndpoints.clear();
        this.manualFolders.clear();
        this.manualEndpoints.clear();
    }

    public void updateSavedAuthConfig(EndpointSavedState saved, vn.io.codelearning.springapitester.model.AuthConfig authConfig) {
        if (saved == null) return;
        saved.authConfig = authConfig != null
                ? authConfig.cloneConfig() : new vn.io.codelearning.springapitester.model.AuthConfig();
    }

    private List<vn.io.codelearning.springapitester.model.HeaderItem> copyHeaders(
            List<vn.io.codelearning.springapitester.model.HeaderItem> headers) {
        List<vn.io.codelearning.springapitester.model.HeaderItem> copy = new ArrayList<>();
        if (headers == null) return copy;
        for (vn.io.codelearning.springapitester.model.HeaderItem header : headers) {
            if (header != null) {
                copy.add(new vn.io.codelearning.springapitester.model.HeaderItem(
                        header.getKey(), header.getValue(), header.isEnabled()));
            }
        }
        return copy;
    }
}
