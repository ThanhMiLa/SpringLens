package vn.io.codelearning.springapitester.model;

import org.junit.Assert;
import org.junit.Test;

public class AuthConfigTest {

    @Test
    public void testDefaultValues() {
        AuthConfig config = new AuthConfig();
        Assert.assertEquals(AuthTypeEnum.INHERIT, config.getAuthType());
        Assert.assertEquals("", config.getBearerToken());
        Assert.assertEquals("", config.getUsername());
        Assert.assertEquals("", config.getPassword());
        Assert.assertEquals("X-API-Key", config.getApiKeyName());
        Assert.assertEquals("", config.getApiKeyValue());
        Assert.assertTrue(config.isApiKeyInHeader());
    }

    @Test
    public void testSettersAndGetters() {
        AuthConfig config = new AuthConfig();
        config.setAuthType(AuthTypeEnum.BEARER_TOKEN);
        config.setBearerToken("my-jwt-token");
        config.setUsername("admin");
        config.setPassword("secret");
        config.setApiKeyName("Api-Key");
        config.setApiKeyValue("val-123");
        config.setApiKeyInHeader(false);

        Assert.assertEquals(AuthTypeEnum.BEARER_TOKEN, config.getAuthType());
        Assert.assertEquals("my-jwt-token", config.getBearerToken());
        Assert.assertEquals("admin", config.getUsername());
        Assert.assertEquals("secret", config.getPassword());
        Assert.assertEquals("Api-Key", config.getApiKeyName());
        Assert.assertEquals("val-123", config.getApiKeyValue());
        Assert.assertFalse(config.isApiKeyInHeader());

        // Test null handling
        config.setAuthType(null);
        Assert.assertEquals(AuthTypeEnum.INHERIT, config.getAuthType());

        config.setBearerToken(null);
        Assert.assertEquals("", config.getBearerToken());

        config.setUsername(null);
        Assert.assertEquals("", config.getUsername());

        config.setPassword(null);
        Assert.assertEquals("", config.getPassword());

        config.setApiKeyName(null);
        Assert.assertEquals("", config.getApiKeyName());

        config.setApiKeyValue(null);
        Assert.assertEquals("", config.getApiKeyValue());
    }

    @Test
    public void testCloneConfig() {
        AuthConfig original = new AuthConfig();
        original.setAuthType(AuthTypeEnum.BASIC_AUTH);
        original.setUsername("john");
        original.setPassword("doe");
        original.setBearerToken("token-xyz");
        original.setApiKeyName("X-Key");
        original.setApiKeyValue("val-999");
        original.setApiKeyInHeader(false);

        AuthConfig clone = original.cloneConfig();
        Assert.assertEquals(original, clone);
        Assert.assertNotSame(original, clone);

        // Modifying clone should not affect original
        clone.setUsername("jane");
        clone.setBearerToken("new-token");
        Assert.assertEquals("john", original.getUsername());
        Assert.assertEquals("token-xyz", original.getBearerToken());
    }

    @Test
    public void testEqualsAndHashCode() {
        AuthConfig config1 = new AuthConfig();
        config1.setAuthType(AuthTypeEnum.BEARER_TOKEN);
        config1.setBearerToken("token-abc");

        AuthConfig config2 = new AuthConfig();
        config2.setAuthType(AuthTypeEnum.BEARER_TOKEN);
        config2.setBearerToken("token-abc");

        Assert.assertEquals(config1, config2);
        Assert.assertEquals(config1.hashCode(), config2.hashCode());

        config2.setBearerToken("token-diff");
        Assert.assertNotEquals(config1, config2);
        Assert.assertNotEquals(config1, null);
        Assert.assertNotEquals(config1, "not-an-auth-config");
    }
}
