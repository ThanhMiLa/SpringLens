package vn.io.codelearning.springapitester.state;

import org.junit.Assert;
import org.junit.Test;
import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.HttpMethodEnum;

public class ResponseCachingTest {

    @Test
    public void testEndpointModelResponseFieldsDefaultAndSetters() {
        EndpointModel endpoint = new EndpointModel();
        Assert.assertEquals("", endpoint.getLastResponseBody());
        Assert.assertEquals(0, endpoint.getLastResponseStatusCode());
        Assert.assertEquals("", endpoint.getLastResponseStatusMessage());
        Assert.assertEquals(0, endpoint.getLastResponseTimeTakenMs());
        Assert.assertEquals("", endpoint.getLastResponseHeaders());
        Assert.assertEquals("JSON", endpoint.getLastResponseFormat());

        endpoint.setLastResponseBody("{\"status\":\"success\",\"data\":[1,2,3]}");
        endpoint.setLastResponseStatusCode(200);
        endpoint.setLastResponseStatusMessage("OK");
        endpoint.setLastResponseTimeTakenMs(42);
        endpoint.setLastResponseHeaders("Content-Type: application/json\nSet-Cookie: sid=abc");
        endpoint.setLastResponseFormat("JSON");
        endpoint.setAllowInsecureTls(true);

        Assert.assertEquals("{\"status\":\"success\",\"data\":[1,2,3]}", endpoint.getLastResponseBody());
        Assert.assertEquals(200, endpoint.getLastResponseStatusCode());
        Assert.assertEquals("OK", endpoint.getLastResponseStatusMessage());
        Assert.assertEquals(42, endpoint.getLastResponseTimeTakenMs());
        Assert.assertEquals("Content-Type: application/json\nSet-Cookie: sid=abc", endpoint.getLastResponseHeaders());
        Assert.assertEquals("JSON", endpoint.getLastResponseFormat());
        Assert.assertTrue(endpoint.isAllowInsecureTls());
    }

    @Test
    public void testSpringLensStateSaveAndRestoreScannedEndpointResponse() {
        SpringLensState state = new SpringLensState();

        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.POST, "/api/v1/users", "UserController", "com.example", "createUser");
        endpoint.setRequestBodyJson("{\"name\":\"Alice\"}");
        endpoint.setLastResponseBody("{\"id\":101,\"name\":\"Alice\"}");
        endpoint.setLastResponseStatusCode(201);
        endpoint.setLastResponseStatusMessage("Created");
        endpoint.setLastResponseTimeTakenMs(120);
        endpoint.setLastResponseHeaders("Content-Type: application/json");
        endpoint.setLastResponseFormat("JSON");
        endpoint.setAllowInsecureTls(true);

        // Save to state
        state.saveEndpoint(endpoint);

        // Verify stored in state
        String key = state.getEndpointKey(endpoint);
        Assert.assertTrue(state.endpoints.containsKey(key));
        EndpointSavedState saved = state.endpoints.get(key);
        Assert.assertEquals("{\"id\":101,\"name\":\"Alice\"}", saved.lastResponseBody);
        Assert.assertEquals(201, saved.lastResponseStatusCode);
        Assert.assertEquals("Created", saved.lastResponseStatusMessage);
        Assert.assertEquals(120, saved.lastResponseTimeTakenMs);
        Assert.assertEquals("Content-Type: application/json", saved.lastResponseHeaders);
        Assert.assertEquals("JSON", saved.lastResponseFormat);
        Assert.assertTrue(saved.allowInsecureTls);

        // Restore into a fresh endpoint model instance
        EndpointModel restoredEndpoint = new EndpointModel(HttpMethodEnum.POST, "/api/v1/users", "UserController", "com.example", "createUser");
        state.restoreEndpoint(restoredEndpoint);

        Assert.assertEquals("{\"name\":\"Alice\"}", restoredEndpoint.getRequestBodyJson());
        Assert.assertEquals("{\"id\":101,\"name\":\"Alice\"}", restoredEndpoint.getLastResponseBody());
        Assert.assertEquals(201, restoredEndpoint.getLastResponseStatusCode());
        Assert.assertEquals("Created", restoredEndpoint.getLastResponseStatusMessage());
        Assert.assertEquals(120, restoredEndpoint.getLastResponseTimeTakenMs());
        Assert.assertEquals("Content-Type: application/json", restoredEndpoint.getLastResponseHeaders());
        Assert.assertEquals("JSON", restoredEndpoint.getLastResponseFormat());
        Assert.assertTrue(restoredEndpoint.isAllowInsecureTls());
    }

    @Test
    public void testSpringLensStateSaveAndRestoreManualEndpointResponse() {
        SpringLensState state = new SpringLensState();

        EndpointModel manualEp = new EndpointModel();
        manualEp.setId("manual-uuid-1");
        manualEp.setName("Custom Webhook");
        manualEp.setManual(true);
        manualEp.setFolderId("folder-1");
        manualEp.setHttpMethod(HttpMethodEnum.GET);
        manualEp.setPath("/health");
        manualEp.setLastResponseBody("{\"status\":\"UP\"}");
        manualEp.setLastResponseStatusCode(200);
        manualEp.setLastResponseStatusMessage("OK");
        manualEp.setLastResponseTimeTakenMs(15);
        manualEp.setLastResponseHeaders("Content-Type: application/json");
        manualEp.setLastResponseFormat("JSON");

        state.saveEndpoint(manualEp);

        Assert.assertEquals(1, state.manualEndpoints.size());
        EndpointSavedState savedManual = state.manualEndpoints.get(0);
        Assert.assertEquals("manual-uuid-1", savedManual.id);
        Assert.assertEquals("{\"status\":\"UP\"}", savedManual.lastResponseBody);
        Assert.assertEquals(200, savedManual.lastResponseStatusCode);
        Assert.assertEquals("OK", savedManual.lastResponseStatusMessage);
        Assert.assertEquals(15, savedManual.lastResponseTimeTakenMs);
        Assert.assertEquals("Content-Type: application/json", savedManual.lastResponseHeaders);
        Assert.assertEquals("JSON", savedManual.lastResponseFormat);
    }
}
