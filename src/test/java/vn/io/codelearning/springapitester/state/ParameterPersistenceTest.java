package vn.io.codelearning.springapitester.state;

import org.junit.Assert;
import org.junit.Test;
import vn.io.codelearning.springapitester.model.*;

public class ParameterPersistenceTest {

    @Test
    public void testParametersSavedWithTypeNameKeyAndEnabledState() {
        SpringLensState state = new SpringLensState();

        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/api/users", "UserController", "com.example", "getUsers");
        ParameterModel queryParam = new ParameterModel("filter", ParamTypeEnum.QUERY_PARAM, "String");
        queryParam.setCurrentValue("active");
        queryParam.setEnabled(true);

        ParameterModel headerParam = new ParameterModel("X-Tenant", ParamTypeEnum.HEADER, "String");
        headerParam.setCurrentValue("tenant-a");
        headerParam.setEnabled(false);

        ParameterModel cookieParam = new ParameterModel("session", ParamTypeEnum.COOKIE, "String");
        cookieParam.setCurrentValue("sess-123");
        cookieParam.setEnabled(true);

        endpoint.addParameter(queryParam);
        endpoint.addParameter(headerParam);
        endpoint.addParameter(cookieParam);

        state.saveEndpoint(endpoint);

        EndpointSavedState saved = state.endpoints.get(state.getEndpointKey(endpoint));
        Assert.assertNotNull(saved);
        Assert.assertEquals("active", saved.paramValues.get("QUERY_PARAM:filter"));
        Assert.assertEquals("tenant-a", saved.paramValues.get("HEADER:X-Tenant"));
        Assert.assertEquals("", saved.paramValues.get("COOKIE:session")); // Protected in PasswordSafe per Plan 02

        Assert.assertEquals(Boolean.TRUE, saved.paramEnabled.get("QUERY_PARAM:filter"));
        Assert.assertEquals(Boolean.FALSE, saved.paramEnabled.get("HEADER:X-Tenant"));
        Assert.assertEquals(Boolean.TRUE, saved.paramEnabled.get("COOKIE:session"));

        // Now restore into a fresh EndpointModel
        EndpointModel fresh = new EndpointModel(HttpMethodEnum.GET, "/api/users", "UserController", "com.example", "getUsers");
        ParameterModel p1 = new ParameterModel("filter", ParamTypeEnum.QUERY_PARAM, "String");
        ParameterModel p2 = new ParameterModel("X-Tenant", ParamTypeEnum.HEADER, "String");
        ParameterModel p3 = new ParameterModel("session", ParamTypeEnum.COOKIE, "String");
        fresh.addParameter(p1);
        fresh.addParameter(p2);
        fresh.addParameter(p3);

        state.restoreEndpoint(fresh);

        Assert.assertEquals("active", p1.getCurrentValue());
        Assert.assertTrue(p1.isEnabled());

        Assert.assertEquals("tenant-a", p2.getCurrentValue());
        Assert.assertFalse(p2.isEnabled());

        Assert.assertEquals("sess-123", p3.getCurrentValue());
        Assert.assertTrue(p3.isEnabled());
    }

    @Test
    public void testLegacyParameterKeyBackwardCompatibility() {
        SpringLensState state = new SpringLensState();

        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/api/legacy", "LegacyController", "com.example", "legacy");
        EndpointSavedState legacySaved = new EndpointSavedState();
        // Legacy saved state only had parameter name without type prefix
        legacySaved.paramValues.put("userId", "user-456");
        state.endpoints.put(state.getEndpointKey(endpoint), legacySaved);

        ParameterModel param = new ParameterModel("userId", ParamTypeEnum.QUERY_PARAM, "String");
        endpoint.addParameter(param);

        state.restoreEndpoint(endpoint);
        Assert.assertEquals("user-456", param.getCurrentValue());
    }
}
