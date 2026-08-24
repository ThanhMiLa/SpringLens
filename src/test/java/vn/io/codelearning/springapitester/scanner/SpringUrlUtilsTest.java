package vn.io.codelearning.springapitester.scanner;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class SpringUrlUtilsTest {

    @Test
    public void testNormalizePath() {
        Assert.assertEquals("", SpringUrlUtils.normalizePath(null));
        Assert.assertEquals("", SpringUrlUtils.normalizePath(""));
        Assert.assertEquals("/api/v1/users", SpringUrlUtils.normalizePath("api/v1/users"));
        Assert.assertEquals("/api/v1/users", SpringUrlUtils.normalizePath("/api/v1/users/"));
        Assert.assertEquals("/api/v1/users", SpringUrlUtils.normalizePath("//api///v1//users//"));
        Assert.assertEquals("/", SpringUrlUtils.normalizePath("/"));
    }

    @Test
    public void testCombinePaths() {
        Assert.assertEquals("/api/v1/users", SpringUrlUtils.combinePaths("/api/v1", "/users"));
        Assert.assertEquals("/api/v1/users", SpringUrlUtils.combinePaths("/api/v1", "users"));
        Assert.assertEquals("/api/v1/users", SpringUrlUtils.combinePaths("/api/v1/", "/users/"));
        Assert.assertEquals("/users", SpringUrlUtils.combinePaths("", "/users"));
        Assert.assertEquals("/api/v1", SpringUrlUtils.combinePaths("/api/v1", ""));
        Assert.assertEquals("/api/v1/users/{id}", SpringUrlUtils.combinePaths("/api/v1", "/users/{id}"));
    }

    @Test
    public void testExtractPathVariableNames() {
        List<String> vars = SpringUrlUtils.extractPathVariableNames("/users/{userId}/orders/{orderId}");
        Assert.assertEquals(2, vars.size());
        Assert.assertEquals("userId", vars.get(0));
        Assert.assertEquals("orderId", vars.get(1));

        // Test with regex in path variable
        List<String> regexVars = SpringUrlUtils.extractPathVariableNames("/users/{id:[0-9]+}");
        Assert.assertEquals(1, regexVars.size());
        Assert.assertEquals("id", regexVars.get(0));
    }
}
