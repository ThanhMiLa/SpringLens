package vn.io.codelearning.springapitester.state;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

@State(
    name = "SpringLensState",
    storages = @Storage("spring-lens-state.xml")
)
public class SpringLensState implements PersistentStateComponent<SpringLensState> {

    public Map<String, EndpointSavedState> endpoints = new HashMap<>();
    
    // Module 7: Manual structures
    public java.util.List<vn.io.codelearning.springapitester.model.FolderModel> manualFolders = new java.util.ArrayList<>();
    public java.util.List<EndpointSavedState> manualEndpoints = new java.util.ArrayList<>();
    public boolean gatewayModeEnabled = false;

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
        this.endpoints = state.endpoints;
        if (state.manualFolders != null) {
            this.manualFolders = state.manualFolders;
        }
        if (state.manualEndpoints != null) {
            this.manualEndpoints = state.manualEndpoints;
        }
        this.gatewayModeEnabled = state.gatewayModeEnabled;
    }

    public String getEndpointKey(vn.io.codelearning.springapitester.model.EndpointModel endpoint) {
        return endpoint.getHttpMethod().name() + " " + endpoint.getPath();
    }

    public void saveEndpoint(vn.io.codelearning.springapitester.model.EndpointModel endpoint) {
        if (endpoint == null) return;
        EndpointSavedState saved = new EndpointSavedState();
        saved.authConfig = endpoint.getAuthConfig();
        saved.customHeaders = new java.util.ArrayList<>(endpoint.getCustomHeaders());
        saved.requestBodyJson = endpoint.getRequestBodyJson();
        saved.bodyType = endpoint.getBodyType();
        saved.allowInsecureTls = endpoint.isAllowInsecureTls();
        saved.isSecuredOverride = endpoint.isSecured();
        
        // Response Cache
        saved.lastResponseBody = endpoint.getLastResponseBody();
        saved.lastResponseStatusCode = endpoint.getLastResponseStatusCode();
        saved.lastResponseStatusMessage = endpoint.getLastResponseStatusMessage();
        saved.lastResponseTimeTakenMs = endpoint.getLastResponseTimeTakenMs();
        saved.lastResponseHeaders = endpoint.getLastResponseHeaders();
        saved.lastResponseFormat = endpoint.getLastResponseFormat();

        // Inherit the previous override state so we don't accidentally lock all endpoints
        EndpointSavedState oldSaved = endpoints.get(getEndpointKey(endpoint));
        if (oldSaved != null) {
            saved.hasSecuredOverride = oldSaved.hasSecuredOverride;
        } else {
            saved.hasSecuredOverride = false;
        }

        for (vn.io.codelearning.springapitester.model.ParameterModel param : endpoint.getParameters()) {
            if (param.getCurrentValue() != null) {
                saved.paramValues.put(param.getName(), param.getCurrentValue());
            }
        }
        
        if (endpoint.isManual()) {
            saved.id = endpoint.getId();
            saved.name = endpoint.getName();
            saved.isManual = true;
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
                saved.manualParameters.add(clone);
            }
            // Update or add
            manualEndpoints.removeIf(e -> e.id != null && e.id.equals(saved.id));
            manualEndpoints.add(saved);
        } else {
            endpoints.put(getEndpointKey(endpoint), saved);
        }
    }

    public void restoreEndpoint(vn.io.codelearning.springapitester.model.EndpointModel endpoint) {
        EndpointSavedState saved = endpoints.get(getEndpointKey(endpoint));
        if (saved == null) return;

        // Restore Auth & Headers
        endpoint.setAuthConfig(saved.authConfig);
        endpoint.setCustomHeaders(saved.customHeaders);
        
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
            if (saved.paramValues.containsKey(param.getName())) {
                param.setCurrentValue(saved.paramValues.get(param.getName()));
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
        this.endpoints.clear();
        this.manualFolders.clear();
        this.manualEndpoints.clear();
    }
}
