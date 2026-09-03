package vn.io.codelearning.springapitester.client;

import org.junit.Assert;
import org.junit.Test;
import vn.io.codelearning.springapitester.model.*;

public class CurlBuilderTest {

    @Test
    public void testSimpleGetCurl() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/api/v1/users", "UserController", "com.example", "getUsers");
        String curl = CurlBuilder.buildCurl(endpoint, "http://localhost:8080/api/v1/users");
        Assert.assertEquals("curl -X GET \"http://localhost:8080/api/v1/users\"", curl);
    }

    @Test
    public void testPostWithJsonBodyAndHeaders() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.POST, "/api/v1/users", "UserController", "com.example", "createUser");
        endpoint.setBodyType(RequestBodyType.JSON);
        endpoint.setRequestBodyJson("{\"name\":\"Alice\",\"age\":30}");
        endpoint.addCustomHeader(new HeaderItem("X-Request-Id", "req-12345", true));
        endpoint.addCustomHeader(new HeaderItem("X-Disabled-Header", "ignored", false));

        String curl = CurlBuilder.buildCurl(endpoint, "http://localhost:8080/api/v1/users");
        Assert.assertTrue(curl.contains("-X POST"));
        Assert.assertTrue(curl.contains("-H \"Content-Type: application/json\""));
        Assert.assertTrue(curl.contains("-H \"X-Request-Id: req-12345\""));
        Assert.assertFalse(curl.contains("X-Disabled-Header"));
        Assert.assertTrue(curl.contains("-d '{\"name\":\"Alice\",\"age\":30}'"));
    }

    @Test
    public void testCurlWithQueryParams() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/api/v1/search", "SearchController", "com.example", "search");
        ParameterModel p1 = new ParameterModel("q", ParamTypeEnum.QUERY_PARAM, "String");
        p1.setCurrentValue("spring boot");
        ParameterModel p2 = new ParameterModel("page", ParamTypeEnum.QUERY_PARAM, "Integer");
        p2.setCurrentValue("1");
        ParameterModel pEmpty = new ParameterModel("filter", ParamTypeEnum.QUERY_PARAM, "String");
        pEmpty.setCurrentValue("");

        endpoint.addParameter(p1);
        endpoint.addParameter(p2);
        endpoint.addParameter(pEmpty);

        String curl = CurlBuilder.buildCurl(endpoint, "http://localhost:8080/api/v1/search");
        Assert.assertTrue(curl.contains("\"http://localhost:8080/api/v1/search?q=spring boot&page=1\""));
        Assert.assertFalse(curl.contains("filter="));
    }

    @Test
    public void testCurlWithPathVariableRegex() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/users/{id:[0-9]+}/details", "UserController", "com.example", "getDetails");
        ParameterModel p = new ParameterModel("id", ParamTypeEnum.PATH_VARIABLE, "Long");
        p.setCurrentValue("555");
        endpoint.addParameter(p);

        String curl = CurlBuilder.buildCurl(endpoint, "http://localhost:8080/users/{id:[0-9]+}/details");
        Assert.assertEquals("curl -X GET \"http://localhost:8080/users/555/details\"", curl);
    }

    @Test
    public void testCurlWithBearerAuth() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/api/v1/profile", "ProfileController", "com.example", "getProfile");
        AuthConfig auth = new AuthConfig();
        auth.setAuthType(AuthTypeEnum.BEARER_TOKEN);
        auth.setBearerToken("secret-token-jwt-123");
        endpoint.setAuthConfig(auth);

        String curl = CurlBuilder.buildCurl(endpoint, "http://localhost:8080/api/v1/profile");
        Assert.assertTrue(curl.contains("-H \"Authorization: Bearer secret-token-jwt-123\""));
    }

    @Test
    public void testCurlWithBasicAuth() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/admin/status", "AdminController", "com.example", "getStatus");
        AuthConfig auth = new AuthConfig();
        auth.setAuthType(AuthTypeEnum.BASIC_AUTH);
        auth.setUsername("admin");
        auth.setPassword("password123");
        endpoint.setAuthConfig(auth);

        String curl = CurlBuilder.buildCurl(endpoint, "http://localhost:8080/admin/status");
        Assert.assertTrue(curl.contains("-H \"Authorization: Basic YWRtaW46cGFzc3dvcmQxMjM=\""));
    }

    @Test
    public void testCurlWithApiKeyInHeaderAndQuery() {
        // 1. API Key in Header
        EndpointModel endpoint1 = new EndpointModel(HttpMethodEnum.GET, "/api/v1/data", "DataController", "com.example", "getData");
        AuthConfig auth1 = new AuthConfig();
        auth1.setAuthType(AuthTypeEnum.API_KEY);
        auth1.setApiKeyName("X-API-KEY");
        auth1.setApiKeyValue("key-header-val");
        auth1.setApiKeyInHeader(true);
        endpoint1.setAuthConfig(auth1);

        String curl1 = CurlBuilder.buildCurl(endpoint1, "http://localhost:8080/api/v1/data");
        Assert.assertTrue(curl1.contains("-H \"X-API-KEY: key-header-val\""));

        // 2. API Key in Query Param
        EndpointModel endpoint2 = new EndpointModel(HttpMethodEnum.GET, "/api/v1/data", "DataController", "com.example", "getData");
        AuthConfig auth2 = new AuthConfig();
        auth2.setAuthType(AuthTypeEnum.API_KEY);
        auth2.setApiKeyName("api_key");
        auth2.setApiKeyValue("key-query-val");
        auth2.setApiKeyInHeader(false);
        endpoint2.setAuthConfig(auth2);

        String curl2 = CurlBuilder.buildCurl(endpoint2, "http://localhost:8080/api/v1/data");
        Assert.assertTrue(curl2.contains("\"http://localhost:8080/api/v1/data?api_key=key-query-val\""));
    }

    @Test
    public void testCurlWithFormDataAndFiles() throws java.io.IOException {
        java.io.File tempFile = java.io.File.createTempFile("avatar", ".png");
        tempFile.deleteOnExit();

        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.POST, "/upload", "UploadController", "com.example", "upload");
        endpoint.setBodyType(RequestBodyType.FORM_DATA);

        ParameterModel textField = new ParameterModel("username", ParamTypeEnum.FORM_DATA, "String");
        textField.setCurrentValue("JohnDoe");

        ParameterModel fileField = new ParameterModel("avatar", ParamTypeEnum.MULTIPART_FILE, "MultipartFile");
        fileField.setCurrentValue(tempFile.getAbsolutePath());

        endpoint.addParameter(textField);
        endpoint.addParameter(fileField);

        String curl = CurlBuilder.buildCurl(endpoint, "http://localhost:8080/upload");
        Assert.assertTrue(curl.contains("-F \"username=JohnDoe\""));
        Assert.assertTrue(curl.contains("-F \"avatar=@" + tempFile.getAbsolutePath() + ";type=image/png\""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCurlMissingFileFailsEarly() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.POST, "/upload", "UploadController", "com.example", "upload");
        endpoint.setBodyType(RequestBodyType.FORM_DATA);

        ParameterModel fileField = new ParameterModel("avatar", ParamTypeEnum.MULTIPART_FILE, "MultipartFile");
        fileField.setCurrentValue("/non/existent/file.png");
        endpoint.addParameter(fileField);

        CurlBuilder.buildCurl(endpoint, "http://localhost:8080/upload");
    }

    @Test
    public void testCurlMixedMultipartFileTextAndJson() throws java.io.IOException {
        java.io.File tempPdf = java.io.File.createTempFile("document", ".pdf");
        tempPdf.deleteOnExit();

        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.POST, "/documents", "DocController", "com.example", "create");
        endpoint.setBodyType(RequestBodyType.FORM_DATA);

        ParameterModel textParam = new ParameterModel("title", ParamTypeEnum.FORM_DATA, "String");
        textParam.setCurrentValue("Contract");

        ParameterModel jsonParam = new ParameterModel("metadata", ParamTypeEnum.FORM_DATA, "String");
        jsonParam.setCurrentValue("{\"author\":\"Alice\",\"version\":1}");

        ParameterModel fileParam = new ParameterModel("file", ParamTypeEnum.MULTIPART_FILE, "MultipartFile");
        fileParam.setCurrentValue(tempPdf.getAbsolutePath());

        endpoint.addParameter(textParam);
        endpoint.addParameter(jsonParam);
        endpoint.addParameter(fileParam);

        String curl = CurlBuilder.buildCurl(endpoint, "http://localhost:8080/documents");
        Assert.assertTrue(curl.contains("-F \"title=Contract\""));
        Assert.assertTrue(curl.contains("-F \"metadata={\\\"author\\\":\\\"Alice\\\",\\\"version\\\":1};type=application/json\""));
        Assert.assertTrue(curl.contains("-F \"file=@" + tempPdf.getAbsolutePath() + ";type=application/pdf\""));
    }

    @Test
    public void testCurlWithHeaderAndCookieParameters() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/profile", "ProfileController", "com.example", "getProfile");
        ParameterModel header = new ParameterModel("X-Request-Id", ParamTypeEnum.HEADER, "String");
        header.setCurrentValue("req-123");
        ParameterModel headerDef = new ParameterModel("X-Platform", ParamTypeEnum.HEADER, "String", "web", false, "", "");

        ParameterModel cookie = new ParameterModel("sessionId", ParamTypeEnum.COOKIE, "String");
        cookie.setCurrentValue("sess-abc");
        ParameterModel cookieDef = new ParameterModel("theme", ParamTypeEnum.COOKIE, "String", "dark", false, "", "");

        endpoint.addParameter(header);
        endpoint.addParameter(headerDef);
        endpoint.addParameter(cookie);
        endpoint.addParameter(cookieDef);

        String curl = CurlBuilder.buildCurl(endpoint, "http://localhost:8080/profile");
        Assert.assertTrue(curl.contains("-H \"X-Request-Id: req-123\""));
        Assert.assertTrue(curl.contains("-H \"X-Platform: web\""));
        Assert.assertTrue(curl.contains("-H \"Cookie: sessionId=sess-abc; theme=dark\""));

        // Verify equivalence with OkHttp request
        okhttp3.Request okHttpRequest = HttpRequestBuilder.buildRequest(endpoint, "http://localhost:8080/profile");
        Assert.assertEquals("req-123", okHttpRequest.header("X-Request-Id"));
        Assert.assertEquals("web", okHttpRequest.header("X-Platform"));
        Assert.assertEquals("sessionId=sess-abc; theme=dark", okHttpRequest.header("Cookie"));
    }

    @Test
    public void testCurlCookiePrecedenceOverCustomHeader() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/api", "ApiController", "com.example", "api");
        endpoint.addCustomHeader(new HeaderItem("Cookie", "session=old-session; extra=foo", true));
        ParameterModel cookieParam = new ParameterModel("session", ParamTypeEnum.COOKIE, "String");
        cookieParam.setCurrentValue("new-session");
        endpoint.addParameter(cookieParam);

        String curl = CurlBuilder.buildCurl(endpoint, "http://localhost:8080/api");
        Assert.assertTrue(curl.contains("-H \"Cookie: session=new-session; extra=foo\""));
        Assert.assertFalse(curl.contains("session=old-session"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCurlRequiredHeaderValidation() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/api", "ApiController", "com.example", "api");
        ParameterModel header = new ParameterModel("X-Required", ParamTypeEnum.HEADER, "String", "", true, "", "");
        endpoint.addParameter(header);
        CurlBuilder.buildCurl(endpoint, "http://localhost:8080/api");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCurlHeaderInjectionRejected() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/api", "ApiController", "com.example", "api");
        ParameterModel header = new ParameterModel("X-Header\r\nInjected: true", ParamTypeEnum.HEADER, "String");
        header.setCurrentValue("val");
        endpoint.addParameter(header);
        CurlBuilder.buildCurl(endpoint, "http://localhost:8080/api");
    }
}
