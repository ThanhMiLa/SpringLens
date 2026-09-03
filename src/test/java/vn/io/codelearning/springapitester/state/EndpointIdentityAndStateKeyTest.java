package vn.io.codelearning.springapitester.state;

import org.junit.Assert;
import org.junit.Test;
import vn.io.codelearning.springapitester.model.*;

import java.util.List;

public class EndpointIdentityAndStateKeyTest {

    @Test
    public void testSameRouteDifferentModulesRetainIndependentState() {
        SpringLensState state = new SpringLensState();

        EndpointModel ep1 = new EndpointModel(HttpMethodEnum.GET, "/api/status", "StatusController", "com.example.orders", "getStatus");
        ep1.setModuleName("order-service");
        ep1.addParameter(new ParameterModel("env", ParamTypeEnum.QUERY_PARAM, "String", "", false, "prod", ""));

        EndpointModel ep2 = new EndpointModel(HttpMethodEnum.GET, "/api/status", "StatusController", "com.example.users", "getStatus");
        ep2.setModuleName("user-service");
        ep2.addParameter(new ParameterModel("env", ParamTypeEnum.QUERY_PARAM, "String", "", false, "staging", ""));

        String key1 = state.getEndpointKey(ep1);
        String key2 = state.getEndpointKey(ep2);

        Assert.assertNotEquals(key1, key2);

        state.saveEndpoint(ep1);
        state.saveEndpoint(ep2);

        EndpointModel freshEp1 = new EndpointModel(HttpMethodEnum.GET, "/api/status", "StatusController", "com.example.orders", "getStatus");
        freshEp1.setModuleName("order-service");
        freshEp1.addParameter(new ParameterModel("env", ParamTypeEnum.QUERY_PARAM, "String"));
        state.restoreEndpoint(freshEp1);

        EndpointModel freshEp2 = new EndpointModel(HttpMethodEnum.GET, "/api/status", "StatusController", "com.example.users", "getStatus");
        freshEp2.setModuleName("user-service");
        freshEp2.addParameter(new ParameterModel("env", ParamTypeEnum.QUERY_PARAM, "String"));
        state.restoreEndpoint(freshEp2);

        Assert.assertEquals("prod", freshEp1.getParameters().get(0).getCurrentValue());
        Assert.assertEquals("staging", freshEp2.getParameters().get(0).getCurrentValue());
    }

    @Test
    public void testSameRouteDifferentControllersRetainIndependentState() {
        SpringLensState state = new SpringLensState();

        EndpointModel userEp = new EndpointModel(HttpMethodEnum.GET, "/api/users", "UserController", "com.example.api", "getUsers");
        userEp.setRequestBodyJson("{\"role\":\"user\"}");

        EndpointModel adminEp = new EndpointModel(HttpMethodEnum.GET, "/api/users", "AdminController", "com.example.api", "getUsers");
        adminEp.setRequestBodyJson("{\"role\":\"admin\"}");

        Assert.assertNotEquals(state.getEndpointKey(userEp), state.getEndpointKey(adminEp));

        state.persistRequestBodies = true;
        state.saveEndpoint(userEp);
        state.saveEndpoint(adminEp);

        EndpointModel freshUser = new EndpointModel(HttpMethodEnum.GET, "/api/users", "UserController", "com.example.api", "getUsers");
        EndpointModel freshAdmin = new EndpointModel(HttpMethodEnum.GET, "/api/users", "AdminController", "com.example.api", "getUsers");

        state.restoreEndpoint(freshUser);
        state.restoreEndpoint(freshAdmin);

        Assert.assertEquals("{\"role\":\"user\"}", freshUser.getRequestBodyJson());
        Assert.assertEquals("{\"role\":\"admin\"}", freshAdmin.getRequestBodyJson());
    }

    @Test
    public void testMethodOverloadsAreDistinct() {
        SpringLensState state = new SpringLensState();

        EndpointModel ep1 = new EndpointModel(HttpMethodEnum.GET, "/search", "SearchController", "com.example", "search");
        ep1.setMethodSignature("search(String)");
        ep1.addParameter(new ParameterModel("q", ParamTypeEnum.QUERY_PARAM, "String", "", false, "spring", ""));

        EndpointModel ep2 = new EndpointModel(HttpMethodEnum.GET, "/search", "SearchController", "com.example", "search");
        ep2.setMethodSignature("search(String,Integer)");
        ep2.addParameter(new ParameterModel("q", ParamTypeEnum.QUERY_PARAM, "String", "", false, "intellij", ""));
        ep2.addParameter(new ParameterModel("page", ParamTypeEnum.QUERY_PARAM, "Integer", "", false, "2", ""));

        Assert.assertNotEquals(state.getEndpointKey(ep1), state.getEndpointKey(ep2));

        state.saveEndpoint(ep1);
        state.saveEndpoint(ep2);

        EndpointSavedState s1 = state.endpoints.get(state.getEndpointKey(ep1));
        EndpointSavedState s2 = state.endpoints.get(state.getEndpointKey(ep2));
        Assert.assertEquals("spring", s1.paramValues.get("QUERY_PARAM:q"));
        Assert.assertEquals("intellij", s2.paramValues.get("QUERY_PARAM:q"));
        Assert.assertEquals("2", s2.paramValues.get("QUERY_PARAM:page"));
    }

    @Test
    public void testMultiMethodAndPathMappingsAreDistinct() {
        SpringLensState state = new SpringLensState();

        EndpointModel getV1 = new EndpointModel(HttpMethodEnum.GET, "/v1/items", "ItemController", "com.example", "getItems");
        EndpointModel postV1 = new EndpointModel(HttpMethodEnum.POST, "/v1/items", "ItemController", "com.example", "createItem");
        EndpointModel getV2 = new EndpointModel(HttpMethodEnum.GET, "/v2/items", "ItemController", "com.example", "getItems");
        EndpointModel postV2 = new EndpointModel(HttpMethodEnum.POST, "/v2/items", "ItemController", "com.example", "createItem");

        String k1 = state.getEndpointKey(getV1);
        String k2 = state.getEndpointKey(postV1);
        String k3 = state.getEndpointKey(getV2);
        String k4 = state.getEndpointKey(postV2);

        Assert.assertNotEquals(k1, k2);
        Assert.assertNotEquals(k1, k3);
        Assert.assertNotEquals(k1, k4);
        Assert.assertNotEquals(k2, k3);
        Assert.assertNotEquals(k2, k4);
        Assert.assertNotEquals(k3, k4);
    }

    @Test
    public void testManualEndpointsUsePersistedUuidAndCleanup() {
        SpringLensState state = new SpringLensState();

        EndpointModel manual1 = new EndpointModel(HttpMethodEnum.POST, "/custom", "", "", "");
        manual1.setManual(true);
        manual1.setId("uuid-manual-1");

        EndpointModel manual2 = new EndpointModel(HttpMethodEnum.POST, "/custom", "", "", "");
        manual2.setManual(true);
        manual2.setId("uuid-manual-2");

        Assert.assertEquals("manual:uuid-manual-1", state.getEndpointKey(manual1));
        Assert.assertEquals("manual:uuid-manual-2", state.getEndpointKey(manual2));

        state.saveEndpoint(manual1);
        state.saveEndpoint(manual2);

        Assert.assertTrue(state.endpoints.containsKey("manual:uuid-manual-1"));
        Assert.assertTrue(state.endpoints.containsKey("manual:uuid-manual-2"));

        state.deleteManualEndpoint("uuid-manual-1");
        Assert.assertFalse(state.endpoints.containsKey("manual:uuid-manual-1"));
        Assert.assertTrue(state.endpoints.containsKey("manual:uuid-manual-2"));
    }

    @Test
    public void testVersionedMigrationUnambiguous() {
        SpringLensState state = new SpringLensState();
        state.schemaVersion = 1;

        // Legacy saved state key format: METHOD PATH
        EndpointSavedState legacyState = new EndpointSavedState();
        legacyState.paramValues.put("QUERY_PARAM:filter", "active");
        state.endpoints.put("GET /api/unique", legacyState);

        EndpointModel ep = new EndpointModel(HttpMethodEnum.GET, "/api/unique", "UniqueController", "com.example", "getUnique");
        state.migrateLegacyKeys(List.of(ep));

        Assert.assertEquals(2, state.schemaVersion);
        Assert.assertFalse(state.endpoints.containsKey("GET /api/unique"));

        String newKey = state.getEndpointKey(ep);
        Assert.assertTrue(state.endpoints.containsKey(newKey));
        Assert.assertEquals("active", state.endpoints.get(newKey).paramValues.get("QUERY_PARAM:filter"));
    }

    @Test
    public void testVersionedMigrationAmbiguousNeverAssigns() {
        SpringLensState state = new SpringLensState();
        state.schemaVersion = 1;

        EndpointSavedState legacyState = new EndpointSavedState();
        legacyState.paramValues.put("QUERY_PARAM:filter", "ambiguous-value");
        state.endpoints.put("GET /api/common", legacyState);

        // Two endpoints share the same method and path
        EndpointModel ep1 = new EndpointModel(HttpMethodEnum.GET, "/api/common", "ControllerA", "com.example", "getA");
        EndpointModel ep2 = new EndpointModel(HttpMethodEnum.GET, "/api/common", "ControllerB", "com.example", "getB");

        state.migrateLegacyKeys(List.of(ep1, ep2));

        Assert.assertEquals(2, state.schemaVersion);
        // Ambiguous legacy key must be removed
        Assert.assertFalse(state.endpoints.containsKey("GET /api/common"));
        // And never assigned to either endpoint
        Assert.assertFalse(state.endpoints.containsKey(state.getEndpointKey(ep1)));
        Assert.assertFalse(state.endpoints.containsKey(state.getEndpointKey(ep2)));
    }

    @Test
    public void testCleanOrphanedParameterState() {
        SpringLensState state = new SpringLensState();

        EndpointModel ep = new EndpointModel(HttpMethodEnum.GET, "/api/test", "TestController", "com.example", "test");
        ParameterModel p1 = new ParameterModel("param1", ParamTypeEnum.QUERY_PARAM, "String", "", false, "val1", "");
        ParameterModel p2 = new ParameterModel("param2", ParamTypeEnum.QUERY_PARAM, "String", "", false, "val2", "");
        ep.addParameter(p1);
        ep.addParameter(p2);

        state.saveEndpoint(ep);
        EndpointSavedState saved = state.endpoints.get(state.getEndpointKey(ep));
        Assert.assertTrue(saved.paramValues.containsKey("QUERY_PARAM:param1"));
        Assert.assertTrue(saved.paramValues.containsKey("QUERY_PARAM:param2"));

        // User removes param2 from the endpoint
        ep.setParameters(List.of(p1));
        state.saveEndpoint(ep);

        saved = state.endpoints.get(state.getEndpointKey(ep));
        Assert.assertTrue(saved.paramValues.containsKey("QUERY_PARAM:param1"));
        Assert.assertFalse(saved.paramValues.containsKey("QUERY_PARAM:param2"));
        Assert.assertFalse(saved.paramEnabled.containsKey("QUERY_PARAM:param2"));
    }
}
