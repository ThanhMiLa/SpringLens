package vn.io.codelearning.springapitester.model;

import org.junit.Assert;
import org.junit.Test;

public class EndpointModelTest {

    @Test
    public void testHttpMethodFromAnnotation() {
        Assert.assertEquals(HttpMethodEnum.GET, HttpMethodEnum.fromAnnotation("GetMapping"));
        Assert.assertEquals(HttpMethodEnum.POST, HttpMethodEnum.fromAnnotation("org.springframework.web.bind.annotation.PostMapping"));
        Assert.assertEquals(HttpMethodEnum.PUT, HttpMethodEnum.fromAnnotation("PutMapping"));
        Assert.assertEquals(HttpMethodEnum.DELETE, HttpMethodEnum.fromAnnotation("DeleteMapping"));
        Assert.assertEquals(HttpMethodEnum.PATCH, HttpMethodEnum.fromAnnotation("PatchMapping"));
        Assert.assertEquals(HttpMethodEnum.GET, HttpMethodEnum.fromAnnotation("RequestMapping"));
    }

    @Test
    public void testParamTypeEnumRecognition() {
        Assert.assertEquals(ParamTypeEnum.PATH_VARIABLE, ParamTypeEnum.fromAnnotationOrType("PathVariable", "java.lang.Long"));
        Assert.assertEquals(ParamTypeEnum.QUERY_PARAM, ParamTypeEnum.fromAnnotationOrType("RequestParam", "java.lang.String"));
        Assert.assertEquals(ParamTypeEnum.HEADER, ParamTypeEnum.fromAnnotationOrType("RequestHeader", "java.lang.String"));
        Assert.assertEquals(ParamTypeEnum.COOKIE, ParamTypeEnum.fromAnnotationOrType("CookieValue", "java.lang.String"));
        Assert.assertEquals(ParamTypeEnum.REQUEST_BODY, ParamTypeEnum.fromAnnotationOrType("RequestBody", "com.demo.UserDTO"));
        Assert.assertEquals(ParamTypeEnum.MULTIPART_FILE, ParamTypeEnum.fromAnnotationOrType("RequestPart", "org.springframework.web.multipart.MultipartFile"));
        Assert.assertEquals(ParamTypeEnum.FORM_DATA, ParamTypeEnum.fromAnnotationOrType("RequestPart", "com.demo.UserDTO"));
        Assert.assertEquals(ParamTypeEnum.MULTIPART_FILE, ParamTypeEnum.fromAnnotationOrType("RequestParam", "org.springframework.web.multipart.MultipartFile"));
        Assert.assertEquals(ParamTypeEnum.MULTIPART_FILE, ParamTypeEnum.fromAnnotationOrType(null, "jakarta.servlet.http.Part"));
        Assert.assertEquals(ParamTypeEnum.FRAMEWORK_INTERNAL, ParamTypeEnum.fromAnnotationOrType(null, "jakarta.servlet.http.HttpServletRequest"));
        Assert.assertEquals(ParamTypeEnum.FRAMEWORK_INTERNAL, ParamTypeEnum.fromAnnotationOrType(null, "java.security.Principal"));
    }

    @Test
    public void testEndpointModelCreationAndHelpers() {
        EndpointModel endpoint = new EndpointModel(
                HttpMethodEnum.POST,
                "/api/v1/users",
                "UserController",
                "com.example.demo.controller",
                "createUser"
        );

        Assert.assertEquals("[POST] /api/v1/users", endpoint.getDisplayName());
        Assert.assertEquals(HttpMethodEnum.POST, endpoint.getHttpMethod());
        Assert.assertEquals("/api/v1/users", endpoint.getPath());
        Assert.assertFalse(endpoint.isAllowInsecureTls());
        endpoint.setAllowInsecureTls(true);
        Assert.assertTrue(endpoint.isAllowInsecureTls());

        // Add params
        endpoint.addParameter(new ParameterModel("userId", ParamTypeEnum.PATH_VARIABLE, "Long"));
        endpoint.addParameter(new ParameterModel("role", ParamTypeEnum.QUERY_PARAM, "String", "USER", false, "ADMIN", "Role filter"));
        endpoint.addParameter(new ParameterModel("jwtCookie", ParamTypeEnum.COOKIE, "String"));
        endpoint.addParameter(new ParameterModel("avatar", ParamTypeEnum.MULTIPART_FILE, "MultipartFile"));
        endpoint.addParameter(new ParameterModel("request", ParamTypeEnum.FRAMEWORK_INTERNAL, "HttpServletRequest"));

        Assert.assertEquals(1, endpoint.getPathVariables().size());
        Assert.assertEquals(1, endpoint.getQueryParams().size());
        Assert.assertEquals(1, endpoint.getCookieParameters().size());
        Assert.assertEquals(1, endpoint.getMultipartParameters().size());
        Assert.assertEquals(4, endpoint.getUserEditableParameters().size()); // 4 editable, 1 framework internal skipped

        // Custom headers
        endpoint.addCustomHeader(new HeaderItem("X-Custom-Header", "Value123"));
        Assert.assertEquals(1, endpoint.getCustomHeaders().size());
        Assert.assertTrue(endpoint.getCustomHeaders().get(0).isEnabled());
    }

    @Test
    public void testResponseModel() {
        ResponseModel response = new ResponseModel();
        response.setStatusCode(200);
        response.setStatusMessage("OK");
        response.setBody("{\"success\":true}");
        response.setExecutionTimeMs(45);

        Assert.assertTrue(response.isSuccess());
        Assert.assertEquals("200 OK", response.getFormattedStatus());
        Assert.assertEquals(45, response.getExecutionTimeMs());

        ResponseModel errorResponse = ResponseModel.error("Connection Refused", 100);
        Assert.assertFalse(errorResponse.isSuccess());
        Assert.assertEquals(0, errorResponse.getStatusCode());
        Assert.assertTrue(errorResponse.getBody().contains("Connection Refused"));
    }
}
