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

    @Test
    public void testBuildHeaderAndCookieParameters() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/profile", "ProfileController", "com.example", "getProfile");
        ParameterModel header1 = new ParameterModel("X-Client-Id", ParamTypeEnum.HEADER, "String");
        header1.setCurrentValue("client-123");
        ParameterModel header2 = new ParameterModel("X-Platform", ParamTypeEnum.HEADER, "String", "web", false, "", "");
        ParameterModel headerDisabled = new ParameterModel("X-Disabled", ParamTypeEnum.HEADER, "String");
        headerDisabled.setCurrentValue("ignore");
        headerDisabled.setEnabled(false);

        ParameterModel cookie1 = new ParameterModel("sessionId", ParamTypeEnum.COOKIE, "String");
        cookie1.setCurrentValue("sess-abc");
        ParameterModel cookie2 = new ParameterModel("theme", ParamTypeEnum.COOKIE, "String", "dark", false, "", "");
        ParameterModel cookieDisabled = new ParameterModel("trackId", ParamTypeEnum.COOKIE, "String");
        cookieDisabled.setCurrentValue("track-xyz");
        cookieDisabled.setEnabled(false);

        endpoint.addParameter(header1);
        endpoint.addParameter(header2);
        endpoint.addParameter(headerDisabled);
        endpoint.addParameter(cookie1);
        endpoint.addParameter(cookie2);
        endpoint.addParameter(cookieDisabled);

        Request request = HttpRequestBuilder.buildRequest(endpoint, "http://localhost:8080/profile");
        Assert.assertEquals("client-123", request.header("X-Client-Id"));
        Assert.assertEquals("web", request.header("X-Platform"));
        Assert.assertNull(request.header("X-Disabled"));

        String cookieHeader = request.header("Cookie");
        Assert.assertNotNull(cookieHeader);
        Assert.assertTrue(cookieHeader.contains("sessionId=sess-abc"));
        Assert.assertTrue(cookieHeader.contains("theme=dark"));
        Assert.assertFalse(cookieHeader.contains("trackId"));
    }

    @Test
    public void testCookieParametersTakePrecedenceOverCustomCookieHeader() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/api", "ApiController", "com.example", "api");
        endpoint.addCustomHeader(new HeaderItem("Cookie", "session=old-session; extra=foo", true));
        ParameterModel cookieParam = new ParameterModel("session", ParamTypeEnum.COOKIE, "String");
        cookieParam.setCurrentValue("new-session");
        endpoint.addParameter(cookieParam);

        Request request = HttpRequestBuilder.buildRequest(endpoint, "http://localhost:8080/api");
        String cookieHeader = request.header("Cookie");
        Assert.assertNotNull(cookieHeader);
        Assert.assertTrue(cookieHeader.contains("session=new-session"));
        Assert.assertTrue(cookieHeader.contains("extra=foo"));
        Assert.assertFalse(cookieHeader.contains("session=old-session"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRequiredHeaderValidation() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/api", "ApiController", "com.example", "api");
        ParameterModel header = new ParameterModel("X-Required", ParamTypeEnum.HEADER, "String", "", true, "", "");
        endpoint.addParameter(header);
        HttpRequestBuilder.buildRequest(endpoint, "http://localhost:8080/api");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRequiredCookieValidation() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/api", "ApiController", "com.example", "api");
        ParameterModel cookie = new ParameterModel("authCookie", ParamTypeEnum.COOKIE, "String", "", true, "", "");
        endpoint.addParameter(cookie);
        HttpRequestBuilder.buildRequest(endpoint, "http://localhost:8080/api");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHeaderInjectionRejected() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/api", "ApiController", "com.example", "api");
        ParameterModel header = new ParameterModel("X-Bad\r\nInjected: true", ParamTypeEnum.HEADER, "String");
        header.setCurrentValue("value");
        endpoint.addParameter(header);
        HttpRequestBuilder.buildRequest(endpoint, "http://localhost:8080/api");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCookieInjectionRejected() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/api", "ApiController", "com.example", "api");
        ParameterModel cookie = new ParameterModel("cookie", ParamTypeEnum.COOKIE, "String");
        cookie.setCurrentValue("val\r\nInjected: true");
        endpoint.addParameter(cookie);
        HttpRequestBuilder.buildRequest(endpoint, "http://localhost:8080/api");
    }

    @Test
    public void testMultipartRealFileUploadAndMimeType() throws IOException {
        java.io.File tempFile = java.io.File.createTempFile("sample", ".json");
        tempFile.deleteOnExit();
        java.nio.file.Files.writeString(tempFile.toPath(), "{\"hello\":\"world\"}");

        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.POST, "/upload", "UploadController", "com.example", "upload");
        endpoint.setBodyType(RequestBodyType.FORM_DATA);

        ParameterModel fileParam = new ParameterModel("file", ParamTypeEnum.MULTIPART_FILE, "MultipartFile");
        fileParam.setCurrentValue(tempFile.getAbsolutePath());
        endpoint.addParameter(fileParam);

        Request request = HttpRequestBuilder.buildRequest(endpoint, "http://localhost:8080/upload");
        Assert.assertEquals("POST", request.method());
        Assert.assertNotNull(request.body());
        Assert.assertTrue(request.body().contentType().toString().startsWith("multipart/form-data"));

        okio.Buffer buffer = new okio.Buffer();
        request.body().writeTo(buffer);
        String bodyContent = buffer.readUtf8();
        Assert.assertTrue(bodyContent.contains("filename=\"" + tempFile.getName() + "\""));
        Assert.assertTrue(bodyContent.contains("Content-Type: application/json"));
        Assert.assertTrue(bodyContent.contains("{\"hello\":\"world\"}"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMultipartMissingFileThrowsEarly() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.POST, "/upload", "UploadController", "com.example", "upload");
        endpoint.setBodyType(RequestBodyType.FORM_DATA);

        ParameterModel fileParam = new ParameterModel("file", ParamTypeEnum.MULTIPART_FILE, "MultipartFile");
        fileParam.setCurrentValue("/missing/file/path.txt");
        endpoint.addParameter(fileParam);

        HttpRequestBuilder.buildRequest(endpoint, "http://localhost:8080/upload");
    }

    @Test
    public void testMultipartMultipleFilesAndJsonPart() throws IOException {
        java.io.File file1 = java.io.File.createTempFile("file1", ".txt");
        file1.deleteOnExit();
        java.nio.file.Files.writeString(file1.toPath(), "content 1");

        java.io.File file2 = java.io.File.createTempFile("file2", ".txt");
        file2.deleteOnExit();
        java.nio.file.Files.writeString(file2.toPath(), "content 2");

        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.POST, "/batch-upload", "UploadController", "com.example", "batchUpload");
        endpoint.setBodyType(RequestBodyType.FORM_DATA);

        ParameterModel filesParam = new ParameterModel("files", ParamTypeEnum.MULTIPART_FILE, "MultipartFile[]");
        filesParam.setCurrentValue(file1.getAbsolutePath() + ", " + file2.getAbsolutePath());

        ParameterModel jsonPart = new ParameterModel("config", ParamTypeEnum.FORM_DATA, "ConfigDto");
        jsonPart.setCurrentValue("{\"async\":true}");

        endpoint.addParameter(filesParam);
        endpoint.addParameter(jsonPart);

        Request request = HttpRequestBuilder.buildRequest(endpoint, "http://localhost:8080/batch-upload");
        okio.Buffer buffer = new okio.Buffer();
        request.body().writeTo(buffer);
        String bodyContent = buffer.readUtf8();

        Assert.assertTrue(bodyContent.contains(file1.getName()));
        Assert.assertTrue(bodyContent.contains("content 1"));
        Assert.assertTrue(bodyContent.contains(file2.getName()));
        Assert.assertTrue(bodyContent.contains("content 2"));
        Assert.assertTrue(bodyContent.contains("name=\"config\""));
        Assert.assertTrue(bodyContent.contains("Content-Type: application/json"));
    }
}
