package vn.io.codelearning.springapitester.state;

import vn.io.codelearning.springapitester.model.AuthConfig;
import vn.io.codelearning.springapitester.model.HeaderItem;
import vn.io.codelearning.springapitester.model.RequestBodyType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EndpointSavedState {
    public Map<String, String> paramValues = new HashMap<>();
    public List<HeaderItem> customHeaders = new ArrayList<>();
    public AuthConfig authConfig = new AuthConfig();
    public String requestBodyJson = "";
    public RequestBodyType bodyType = RequestBodyType.NONE;
    public boolean isSecuredOverride = false;
    public boolean hasSecuredOverride = false;

    // Response Cache
    public String lastResponseBody = "";
    public int lastResponseStatusCode = 0;
    public String lastResponseStatusMessage = "";
    public long lastResponseTimeTakenMs = 0;
    public String lastResponseHeaders = "";
    public String lastResponseFormat = "JSON";
    
    // Module 7: Fields for manual endpoints
    public String id;
    public String name;
    public boolean isManual = false;
    public String folderId;
    public vn.io.codelearning.springapitester.model.HttpMethodEnum httpMethod;
    public String path;
    public List<vn.io.codelearning.springapitester.model.ParameterModel> manualParameters = new ArrayList<>();
}
