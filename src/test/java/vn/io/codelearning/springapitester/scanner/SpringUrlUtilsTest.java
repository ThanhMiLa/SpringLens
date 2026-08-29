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

        Assert.assertTrue(SpringUrlUtils.extractPathVariableNames(null).isEmpty());
        Assert.assertTrue(SpringUrlUtils.extractPathVariableNames("").isEmpty());
    }

    @Test
    public void testReplacePathVariable() {
        String url = "/users/{id}/orders/{orderId}";
        String replaced = SpringUrlUtils.replacePathVariable(url, "id", "123");
        Assert.assertEquals("/users/123/orders/{orderId}", replaced);

        replaced = SpringUrlUtils.replacePathVariable(replaced, "orderId", "999");
        Assert.assertEquals("/users/123/orders/999", replaced);

        // Test with regex in path variable
        String regexUrl = "/users/{id:[0-9]+}";
        String regexReplaced = SpringUrlUtils.replacePathVariable(regexUrl, "id", "456");
        Assert.assertEquals("/users/456", regexReplaced);

        // Test edge cases
        Assert.assertNull(SpringUrlUtils.replacePathVariable(null, "id", "123"));
        Assert.assertEquals("/users/{id}", SpringUrlUtils.replacePathVariable("/users/{id}", null, "123"));
        Assert.assertEquals("/users/", SpringUrlUtils.replacePathVariable("/users/{id}", "id", null));
    }

    @Test
    public void testHasUnresolvedPathVariables() {
        Assert.assertTrue(SpringUrlUtils.hasUnresolvedPathVariables("/users/{id}"));
        Assert.assertTrue(SpringUrlUtils.hasUnresolvedPathVariables("/users/{id:[0-9]+}"));
        Assert.assertTrue(SpringUrlUtils.hasUnresolvedPathVariables("/users/{id}/details/{subId}"));
        Assert.assertFalse(SpringUrlUtils.hasUnresolvedPathVariables("/users/123"));
        Assert.assertFalse(SpringUrlUtils.hasUnresolvedPathVariables("/users/123/details/456"));
        Assert.assertFalse(SpringUrlUtils.hasUnresolvedPathVariables(""));
        Assert.assertFalse(SpringUrlUtils.hasUnresolvedPathVariables(null));
    }

    @Test
    public void testGetUnresolvedPathVariables() {
        List<String> missing = SpringUrlUtils.getUnresolvedPathVariables("/users/{userId:[0-9]+}/posts/{postId}");
        Assert.assertEquals(2, missing.size());
        Assert.assertEquals("userId", missing.get(0));
        Assert.assertEquals("postId", missing.get(1));

        Assert.assertTrue(SpringUrlUtils.getUnresolvedPathVariables("/users/123/posts/456").isEmpty());
    }
}
