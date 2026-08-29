package vn.io.codelearning.springapitester.client;

import okhttp3.Request;
import org.junit.Assert;
import org.junit.Test;
import vn.io.codelearning.springapitester.model.*;

import java.io.IOException;

public class HttpRequestBuilderTest {

    @Test
    public void testBuildGetRequestWithQueryParamsAndPathVars() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/users/{id:[0-9]+}/posts", "UserController", "com.example", "getPosts");
        ParameterModel pPath = new ParameterModel("id", ParamTypeEnum.PATH_VARIABLE, "Long");
        pPath.setCurrentValue("123");
        ParameterModel pQuery = new ParameterModel("status", ParamTypeEnum.QUERY_PARAM, "String");
        pQuery.setCurrentValue("ACTIVE");

        endpoint.addParameter(pPath);
        endpoint.addParameter(pQuery);

        Request request = HttpRequestBuilder.buildRequest(endpoint, "http://localhost:8080/users/{id:[0-9]+}/posts");
        Assert.assertEquals("GET", request.method());
        Assert.assertEquals("http://localhost:8080/users/123/posts?status=ACTIVE", request.url().toString());
        Assert.assertNull(request.body());
    }

    @Test
    public void testBuildPostWithJsonBody() throws IOException {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.POST, "/api/v1/users", "UserController", "com.example", "createUser");
        endpoint.setBodyType(RequestBodyType.JSON);
        endpoint.setRequestBodyJson("{\"username\":\"testuser\"}");

        Request request = HttpRequestBuilder.buildRequest(endpoint, "http://localhost:8080/api/v1/users");
        Assert.assertEquals("POST", request.method());
        Assert.assertNotNull(request.body());
        Assert.assertEquals("application/json; charset=utf-8", request.body().contentType().toString());
    }

    @Test
    public void testBuildPostWithEmptyBodySafe() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.POST, "/api/v1/ping", "PingController", "com.example", "ping");
        endpoint.setRequestBodyJson("");

        Request request = HttpRequestBuilder.buildRequest(endpoint, "http://localhost:8080/api/v1/ping");
        Assert.assertEquals("POST", request.method());
        Assert.assertNotNull(request.body()); // Should not be null to prevent OkHttp exception
    }

    @Test
    public void testBuildPutPatchDelete() {
        // PUT
        EndpointModel putEp = new EndpointModel(HttpMethodEnum.PUT, "/items/1", "ItemController", "com.example", "update");
        putEp.setRequestBodyJson("{}");
        Request putReq = HttpRequestBuilder.buildRequest(putEp, "http://localhost:8080/items/1");
        Assert.assertEquals("PUT", putReq.method());
        Assert.assertNotNull(putReq.body());

        // PATCH
        EndpointModel patchEp = new EndpointModel(HttpMethodEnum.PATCH, "/items/1", "ItemController", "com.example", "patch");
        patchEp.setRequestBodyJson("{}");
        Request patchReq = HttpRequestBuilder.buildRequest(patchEp, "http://localhost:8080/items/1");
        Assert.assertEquals("PATCH", patchReq.method());
        Assert.assertNotNull(patchReq.body());

        // DELETE
        EndpointModel delEp = new EndpointModel(HttpMethodEnum.DELETE, "/items/1", "ItemController", "com.example", "delete");
        Request delReq = HttpRequestBuilder.buildRequest(delEp, "http://localhost:8080/items/1");
        Assert.assertEquals("DELETE", delReq.method());
    }

    @Test
    public void testBuildBearerAuth() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/profile", "ProfileController", "com.example", "profile");
        AuthConfig auth = new AuthConfig();
        auth.setAuthType(AuthTypeEnum.BEARER_TOKEN);
        auth.setBearerToken("jwt-sample-token");
        endpoint.setAuthConfig(auth);

        Request request = HttpRequestBuilder.buildRequest(endpoint, "http://localhost:8080/profile");
        Assert.assertEquals("Bearer jwt-sample-token", request.header("Authorization"));
    }

    @Test
    public void testBuildBasicAuth() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/admin", "AdminController", "com.example", "admin");
        AuthConfig auth = new AuthConfig();
        auth.setAuthType(AuthTypeEnum.BASIC_AUTH);
        auth.setUsername("user");
        auth.setPassword("pass");
        endpoint.setAuthConfig(auth);

        Request request = HttpRequestBuilder.buildRequest(endpoint, "http://localhost:8080/admin");
        Assert.assertEquals("Basic dXNlcjpwYXNz", request.header("Authorization"));
    }

    @Test
    public void testBuildApiKeyInHeaderAndQuery() {
        // Header
        EndpointModel ep1 = new EndpointModel(HttpMethodEnum.GET, "/data", "DataController", "com.example", "data");
        AuthConfig a1 = new AuthConfig();
        a1.setAuthType(AuthTypeEnum.API_KEY);
        a1.setApiKeyName("X-Api-Key");
        a1.setApiKeyValue("secret123");
        a1.setApiKeyInHeader(true);
        ep1.setAuthConfig(a1);

        Request req1 = HttpRequestBuilder.buildRequest(ep1, "http://localhost:8080/data");
        Assert.assertEquals("secret123", req1.header("X-Api-Key"));

        // Query
        EndpointModel ep2 = new EndpointModel(HttpMethodEnum.GET, "/data", "DataController", "com.example", "data");
        AuthConfig a2 = new AuthConfig();
        a2.setAuthType(AuthTypeEnum.API_KEY);
        a2.setApiKeyName("key");
        a2.setApiKeyValue("querysecret");
        a2.setApiKeyInHeader(false);
        ep2.setAuthConfig(a2);

        Request req2 = HttpRequestBuilder.buildRequest(ep2, "http://localhost:8080/data");
        Assert.assertEquals("http://localhost:8080/data?key=querysecret", req2.url().toString());
    }

    @Test
    public void testBuildCustomHeaders() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/test", "TestController", "com.example", "test");
        endpoint.addCustomHeader(new HeaderItem("X-Custom-1", "val1", true));
        endpoint.addCustomHeader(new HeaderItem("X-Custom-2", "val2", false)); // disabled

        Request request = HttpRequestBuilder.buildRequest(endpoint, "http://localhost:8080/test");
        Assert.assertEquals("val1", request.header("X-Custom-1"));
        Assert.assertNull(request.header("X-Custom-2"));
    }
}
