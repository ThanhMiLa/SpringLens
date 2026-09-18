package vn.io.codelearning.springapitester.scanner;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.ParamTypeEnum;

import java.util.List;

public class SpringEndpointScannerSecurityParameterTest extends BasePlatformTestCase {

    public void testScannerSkipsSecurityInjectedParameters() {
        addAnnotations();
        myFixture.addFileToProject("org/springframework/security/oauth2/jwt/Jwt.java", """
                package org.springframework.security.oauth2.jwt;
                public class Jwt {
                    String headers;
                    String claims;
                    String tokenValue;
                    String issuedAt;
                    String expiresAt;
                }
                """);
        myFixture.addFileToProject("demo/PostController.java", """
                package demo;

                import org.springframework.security.core.annotation.AuthenticationPrincipal;
                import org.springframework.security.core.annotation.CurrentSecurityContext;
                import org.springframework.security.oauth2.jwt.Jwt;
                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestParam;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                public class PostController {
                    @GetMapping("/my-posts")
                    public void findPosts(
                            @AuthenticationPrincipal Jwt jwt,
                            @CurrentSecurityContext Object securityContext,
                            @RequestParam int page) {}
                }
                """);

        List<EndpointModel> endpoints = SpringEndpointScanner.getInstance().scanEndpoints(getProject());
        EndpointModel endpoint = endpoints.stream()
                .filter(candidate -> "/my-posts".equals(candidate.getPath()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Endpoint not found"));

        assertEquals(1, endpoint.getParameters().size());
        assertEquals("page", endpoint.getParameters().get(0).getName());
        assertEquals(ParamTypeEnum.QUERY_PARAM, endpoint.getParameters().get(0).getParamType());
    }

    private void addAnnotations() {
        myFixture.addFileToProject("org/springframework/web/bind/annotation/RestController.java", """
                package org.springframework.web.bind.annotation;
                public @interface RestController {}
                """);
        myFixture.addFileToProject("org/springframework/web/bind/annotation/GetMapping.java", """
                package org.springframework.web.bind.annotation;
                public @interface GetMapping { String[] value() default {}; String[] path() default {}; }
                """);
        myFixture.addFileToProject("org/springframework/web/bind/annotation/RequestParam.java", """
                package org.springframework.web.bind.annotation;
                public @interface RequestParam { String value() default ""; String name() default ""; }
                """);
        myFixture.addFileToProject("org/springframework/security/core/annotation/AuthenticationPrincipal.java", """
                package org.springframework.security.core.annotation;
                public @interface AuthenticationPrincipal {}
                """);
        myFixture.addFileToProject("org/springframework/security/core/annotation/CurrentSecurityContext.java", """
                package org.springframework.security.core.annotation;
                public @interface CurrentSecurityContext {}
                """);
    }
}
