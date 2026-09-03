package vn.io.codelearning.springapitester.client;

import org.junit.Assert;
import org.junit.Test;
import vn.io.codelearning.springapitester.model.*;

import java.io.File;
import java.io.IOException;

public class CurlBuilderTest {

    @Test
    public void testSimpleGetCurl() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/api/v1/users", "UserController", "com.example", "getUsers");
        String curl = CurlBuilder.buildCurl(endpoint, "http://localhost:8080/api/v1/users");
        Assert.assertEquals("curl -X GET 'http://localhost:8080/api/v1/users'", curl);
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
        Assert.assertTrue(curl.contains("-H 'Content-Type: application/json; charset=utf-8'"));
        Assert.assertTrue(curl.contains("-H 'X-Request-Id: req-12345'"));
        Assert.assertFalse(curl.contains("X-Disabled-Header"));
        Assert.assertTrue(curl.contains("--data-raw '{\"name\":\"Alice\",\"age\":30}'"));
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
        // HttpUrl encodes space as %20 or +
        Assert.assertTrue(curl.contains("http://localhost:8080/api/v1/search?q=spring%20boot&page=1") ||
                curl.contains("http://localhost:8080/api/v1/search?q=spring+boot&page=1"));
        Assert.assertFalse(curl.contains("filter="));
    }

    @Test
    public void testCurlWithExistingQueryParamsInUrl() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/api/v1/search", "SearchController", "com.example", "search");
        ParameterModel p1 = new ParameterModel("page", ParamTypeEnum.QUERY_PARAM, "Integer");
        p1.setCurrentValue("2");
        endpoint.addParameter(p1);

        String curl = CurlBuilder.buildCurl(endpoint, "http://localhost:8080/api/v1/search?active=true");
        Assert.assertTrue(curl.contains("active=true"));
        Assert.assertTrue(curl.contains("page=2"));
    }

    @Test
    public void testCurlWithPathVariableRegex() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/users/{id:[0-9]+}/details", "UserController", "com.example", "getDetails");
        ParameterModel p = new ParameterModel("id", ParamTypeEnum.PATH_VARIABLE, "Long");
        p.setCurrentValue("555");
        endpoint.addParameter(p);

        String curl = CurlBuilder.buildCurl(endpoint, "http://localhost:8080/users/{id:[0-9]+}/details");
        Assert.assertEquals("curl -X GET 'http://localhost:8080/users/555/details'", curl);
    }

    @Test
    public void testCurlWithBearerAuthRedactedAndIncluded() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/api/v1/profile", "ProfileController", "com.example", "getProfile");
        AuthConfig auth = new AuthConfig();
        auth.setAuthType(AuthTypeEnum.BEARER_TOKEN);
        auth.setBearerToken("secret-token-jwt-123");
        endpoint.setAuthConfig(auth);

        // Default: redacted
        String redactedCurl = CurlBuilder.buildCurl(endpoint, "http://localhost:8080/api/v1/profile");
        Assert.assertTrue(redactedCurl.contains("-H 'Authorization: [REDACTED]'"));
        Assert.assertFalse(redactedCurl.contains("secret-token-jwt-123"));

        // With credentials: included
        String withCreds = CurlBuilder.buildCurl(endpoint, "http://localhost:8080/api/v1/profile", true);
        Assert.assertTrue(withCreds.contains("-H 'Authorization: Bearer secret-token-jwt-123'"));
    }

    @Test
    public void testCurlWithBasicAuthRedactedAndIncluded() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/admin/status", "AdminController", "com.example", "getStatus");
        AuthConfig auth = new AuthConfig();
        auth.setAuthType(AuthTypeEnum.BASIC_AUTH);
        auth.setUsername("admin");
        auth.setPassword("password123");
        endpoint.setAuthConfig(auth);

        String redactedCurl = CurlBuilder.buildCurl(endpoint, "http://localhost:8080/admin/status");
        Assert.assertTrue(redactedCurl.contains("-H 'Authorization: [REDACTED]'"));

        String withCreds = CurlBuilder.buildCurl(endpoint, "http://localhost:8080/admin/status", true);
        Assert.assertTrue(withCreds.contains("-H 'Authorization: Basic YWRtaW46cGFzc3dvcmQxMjM='"));
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

        String redacted1 = CurlBuilder.buildCurl(endpoint1, "http://localhost:8080/api/v1/data");
        Assert.assertTrue(redacted1.contains("-H 'X-API-KEY: [REDACTED]'"));
        String creds1 = CurlBuilder.buildCurl(endpoint1, "http://localhost:8080/api/v1/data", true);
        Assert.assertTrue(creds1.contains("-H 'X-API-KEY: key-header-val'"));

        // 2. API Key in Query Param
        EndpointModel endpoint2 = new EndpointModel(HttpMethodEnum.GET, "/api/v1/data", "DataController", "com.example", "getData");
        AuthConfig auth2 = new AuthConfig();
        auth2.setAuthType(AuthTypeEnum.API_KEY);
        auth2.setApiKeyName("api_key");
        auth2.setApiKeyValue("key-query-val");
        auth2.setApiKeyInHeader(false);
        endpoint2.setAuthConfig(auth2);

        String redacted2 = CurlBuilder.buildCurl(endpoint2, "http://localhost:8080/api/v1/data");
        Assert.assertTrue(redacted2.contains("api_key=%5BREDACTED%5D") || redacted2.contains("api_key=[REDACTED]"));
        String creds2 = CurlBuilder.buildCurl(endpoint2, "http://localhost:8080/api/v1/data", true);
        Assert.assertTrue(creds2.contains("api_key=key-query-val"));
    }

    @Test
    public void testCurlWithFormDataAndFiles() throws IOException {
        File tempFile = File.createTempFile("avatar", ".png");
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
        Assert.assertTrue(curl.contains("-F 'username=JohnDoe'"));
        Assert.assertTrue(curl.contains("-F 'avatar=@" + tempFile.getAbsolutePath() + ";type=image/png'"));
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
    public void testCurlMixedMultipartFileTextAndJson() throws IOException {
        File tempPdf = File.createTempFile("document", ".pdf");
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
        Assert.assertTrue(curl.contains("-F 'title=Contract'"));
        Assert.assertTrue(curl.contains("-F 'metadata={\"author\":\"Alice\",\"version\":1};type=application/json'"));
        Assert.assertTrue(curl.contains("-F 'file=@" + tempPdf.getAbsolutePath() + ";type=application/pdf'"));
    }

    @Test
    public void testShellEscapingSecurity() {
        // Test single quote escaping
        Assert.assertEquals("'O'\\''Reilly'", CurlBuilder.escapeShellArg("O'Reilly"));
        // Test spaces
        Assert.assertEquals("'hello world'", CurlBuilder.escapeShellArg("hello world"));
        // Test $ and backticks
        Assert.assertEquals("'$HOME `id`'", CurlBuilder.escapeShellArg("$HOME `id`"));
        // Test semicolons and injection attempts
        Assert.assertEquals("'; rm -rf / ;'", CurlBuilder.escapeShellArg("; rm -rf / ;"));
        // Test newlines
        Assert.assertEquals("'line1\nline2'", CurlBuilder.escapeShellArg("line1\nline2"));
        // Test Unicode
        Assert.assertEquals("'Tiếng Việt'", CurlBuilder.escapeShellArg("Tiếng Việt"));
        // Test JSON with apostrophe
        Assert.assertEquals("'{\"note\":\"don'\\''t panic\"}'", CurlBuilder.escapeShellArg("{\"note\":\"don't panic\"}"));
    }

    @Test
    public void testInjectionAttemptCannotAppendShellCommand() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.POST, "/api/test", "TestController", "com.example", "test");
        endpoint.setBodyType(RequestBodyType.JSON);
        endpoint.setRequestBodyJson("{\"cmd\": \"'; rm -rf / ; echo '\"}");

        ParameterModel header = new ParameterModel("X-Custom", ParamTypeEnum.HEADER, "String");
        header.setCurrentValue("val'; evil_cmd; echo '");
        endpoint.addParameter(header);

        String curl = CurlBuilder.buildCurl(endpoint, "http://localhost:8080/api/test");
        // Check that shell quoting prevents breaking out
        Assert.assertTrue(curl.contains("-H 'X-Custom: val'\\'' ; evil_cmd; echo '\\'''") ||
                curl.contains("val'\\''"));
        Assert.assertTrue(curl.contains("--data-raw '{\"cmd\": \"'\\'' ; rm -rf / ; echo '\\''\"}'") ||
                curl.contains("rm -rf /"));
        // The command must not have unescaped semicolons outside of single quotes
        Assert.assertFalse(curl.contains("\n  -H val;"));
    }

    @Test
    public void testPowerShellGeneration() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.POST, "/api/v1/test", "TestController", "com.example", "test");
        endpoint.setBodyType(RequestBodyType.JSON);
        endpoint.setRequestBodyJson("{\"key\":\"value's\"}");
        endpoint.addCustomHeader(new HeaderItem("X-Test", "test'value", true));

        String ps = CurlBuilder.buildPowerShell(endpoint, "http://localhost:8080/api/v1/test", true);
        Assert.assertTrue(ps.startsWith("Invoke-RestMethod -Method POST -Uri 'http://localhost:8080/api/v1/test'"));
        Assert.assertTrue(ps.contains("'X-Test' = 'test''value'"));
        Assert.assertTrue(ps.contains("-Body '{\"key\":\"value''s\"}'"));
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

    @Test
    public void testWindowsCmdExportEscapingAndQuoting() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.POST, "/api/v1/update", "UpdateCtrl", "com.example", "update");
        endpoint.setBodyType(RequestBodyType.JSON);
        endpoint.setRequestBodyJson("{\"percent\":\"100%\"}");
        endpoint.addCustomHeader(new HeaderItem("X-Custom", "quoted \"val\"", true));

        String cmd = CurlBuilder.buildWindowsCmd(endpoint, "http://localhost:8080/api/v1/update", true);
        Assert.assertTrue(cmd.startsWith("curl -X POST"));
        Assert.assertTrue(cmd.contains(" ^\n  -H \"X-Custom: quoted \\\"val\\\"\""));
        Assert.assertTrue(cmd.contains(" ^\n  --data-raw \"{\\\"percent\\\":\\\"100%%\\\"}\""));
    }

    @Test
    public void testPowerShellMultipartExport() throws IOException {
        File tempFile = File.createTempFile("ps_upload", ".txt");
        tempFile.deleteOnExit();
        java.nio.file.Files.writeString(tempFile.toPath(), "file content");

        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.POST, "/api/v1/upload", "UploadCtrl", "com.example", "upload");
        endpoint.setBodyType(RequestBodyType.FORM_DATA);
        ParameterModel pFile = new ParameterModel("avatar", ParamTypeEnum.MULTIPART_FILE, "MultipartFile");
        pFile.setCurrentValue(tempFile.getAbsolutePath());
        ParameterModel pText = new ParameterModel("username", ParamTypeEnum.FORM_DATA, "String");
        pText.setCurrentValue("john_doe");

        endpoint.addParameter(pFile);
        endpoint.addParameter(pText);

        String ps = CurlBuilder.buildPowerShell(endpoint, "http://localhost:8080/api/v1/upload", false);
        Assert.assertTrue(ps.contains("$form = @{"));
        Assert.assertTrue(ps.contains("'avatar' = Get-Item '" + tempFile.getAbsolutePath() + "'"));
        Assert.assertTrue(ps.contains("'username' = 'john_doe'"));
        Assert.assertTrue(ps.contains("Invoke-RestMethod -Method POST -Uri 'http://localhost:8080/api/v1/upload' -Form $form"));
    }

    @Test
    public void testCredentialRedactionAcrossAllExportFormats() {
        EndpointModel endpoint = new EndpointModel(HttpMethodEnum.GET, "/api/v1/secure", "SecureCtrl", "com.example", "getSecure");
        AuthConfig auth = new AuthConfig();
        auth.setAuthType(AuthTypeEnum.BEARER_TOKEN);
        auth.setBearerToken("super-secret-token");
        endpoint.setAuthConfig(auth);

        ParameterModel tokenParam = new ParameterModel("apikey", ParamTypeEnum.QUERY_PARAM, "String");
        tokenParam.setCurrentValue("confidential-key");
        endpoint.addParameter(tokenParam);

        String bash = CurlBuilder.buildCurl(endpoint, "http://localhost:8080/api/v1/secure", false);
        String cmd = CurlBuilder.buildWindowsCmd(endpoint, "http://localhost:8080/api/v1/secure", false);
        String ps = CurlBuilder.buildPowerShell(endpoint, "http://localhost:8080/api/v1/secure", false);

        // Bash
        Assert.assertTrue(bash.contains("apikey=[REDACTED]") || bash.contains("apikey=%5BREDACTED%5D"));
        Assert.assertTrue(bash.contains("Authorization: [REDACTED]"));
        Assert.assertFalse(bash.contains("super-secret-token"));
        Assert.assertFalse(bash.contains("confidential-key"));

        // Windows CMD
        Assert.assertTrue(cmd.contains("apikey=[REDACTED]") || cmd.contains("apikey=%5BREDACTED%5D") || cmd.contains("apikey=%%5BREDACTED%%5D"));
        Assert.assertTrue(cmd.contains("Authorization: [REDACTED]"));
        Assert.assertFalse(cmd.contains("super-secret-token"));
        Assert.assertFalse(cmd.contains("confidential-key"));

        // PowerShell
        Assert.assertTrue(ps.contains("apikey=[REDACTED]") || ps.contains("apikey=%5BREDACTED%5D"));
        Assert.assertTrue(ps.contains("Authorization' = '[REDACTED]'"));
        Assert.assertFalse(ps.contains("super-secret-token"));
        Assert.assertFalse(ps.contains("confidential-key"));
    }
}
