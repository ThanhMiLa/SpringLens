package vn.io.codelearning.springapitester.scanner;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.RequestBodyType;

import java.util.List;

public class SpringEndpointScannerBodyTypeTest extends BasePlatformTestCase {

    public void testScannerDeterminesBodyTypeFromControllerSignature() {
        addSpringWebAnnotations();
        myFixture.addFileToProject("org/springframework/web/multipart/MultipartFile.java", """
                package org.springframework.web.multipart;
                public interface MultipartFile {}
                """);
        myFixture.addFileToProject("demo/ApiController.java", """
                package demo;

                import org.springframework.web.bind.annotation.*;
                import org.springframework.web.multipart.MultipartFile;

                @RestController
                public class ApiController {
                    @PostMapping("/json")
                    public void create(@RequestBody Payload payload) {}

                    @PostMapping("/form")
                    public void submit(@ModelAttribute FormData form) {}

                    @PostMapping("/multipart")
                    public void upload(@RequestPart MultipartFile file) {}

                    @PostMapping("/part")
                    public void uploadMetadata(@RequestPart Payload metadata) {}

                    @GetMapping("/without-body")
                    public void list() {}
                }

                class Payload { String name; }
                class FormData { String name; }
                """);

        List<EndpointModel> endpoints = SpringEndpointScanner.getInstance().scanEndpoints(getProject());

        assertBodyType(endpoints, "/json", RequestBodyType.JSON);
        assertBodyType(endpoints, "/form", RequestBodyType.FORM_DATA);
        assertBodyType(endpoints, "/multipart", RequestBodyType.FORM_DATA);
        assertBodyType(endpoints, "/part", RequestBodyType.FORM_DATA);
        assertBodyType(endpoints, "/without-body", RequestBodyType.JSON);
    }

    private void addSpringWebAnnotations() {
        myFixture.addFileToProject("org/springframework/web/bind/annotation/RestController.java", """
                package org.springframework.web.bind.annotation;
                public @interface RestController {}
                """);
        myFixture.addFileToProject("org/springframework/web/bind/annotation/PostMapping.java", """
                package org.springframework.web.bind.annotation;
                public @interface PostMapping { String[] value() default {}; String[] path() default {}; }
                """);
        myFixture.addFileToProject("org/springframework/web/bind/annotation/GetMapping.java", """
                package org.springframework.web.bind.annotation;
                public @interface GetMapping { String[] value() default {}; String[] path() default {}; }
                """);
        myFixture.addFileToProject("org/springframework/web/bind/annotation/RequestBody.java", """
                package org.springframework.web.bind.annotation;
                public @interface RequestBody { boolean required() default true; }
                """);
        myFixture.addFileToProject("org/springframework/web/bind/annotation/ModelAttribute.java", """
                package org.springframework.web.bind.annotation;
                public @interface ModelAttribute { String value() default \"\"; boolean required() default true; }
                """);
        myFixture.addFileToProject("org/springframework/web/bind/annotation/RequestPart.java", """
                package org.springframework.web.bind.annotation;
                public @interface RequestPart { String value() default \"\"; String name() default \"\"; boolean required() default true; }
                """);
    }

    private void assertBodyType(List<EndpointModel> endpoints, String path, RequestBodyType expected) {
        EndpointModel endpoint = endpoints.stream()
                .filter(candidate -> path.equals(candidate.getPath()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Endpoint not found for path: " + path));
        assertEquals(expected, endpoint.getBodyType());
    }
}
