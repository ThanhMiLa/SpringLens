package vn.io.codelearning.springapitester.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Assert;
import org.junit.Test;

public class SmartMergeUtilTest {

    @Test
    public void testMergeNullOrEmptyOldJson() {
        String schema = "{\"name\":\"\",\"age\":0}";
        Assert.assertEquals(schema, SmartMergeUtil.mergeJson(null, schema));
        Assert.assertEquals(schema, SmartMergeUtil.mergeJson("", schema));
        Assert.assertEquals(schema, SmartMergeUtil.mergeJson("   ", schema));
    }

    @Test
    public void testMergeInvalidJsonSyntax() {
        String badJson = "{ invalid json here ...";
        String schema = "{\"name\":\"\",\"age\":0}";
        Assert.assertEquals(schema, SmartMergeUtil.mergeJson(badJson, schema));
    }

    @Test
    public void testPreserveUserEditedValues() {
        String oldJson = "{\"name\":\"John Doe\",\"age\":28}";
        String newSchema = "{\"name\":\"\",\"age\":0,\"email\":\"\"}";

        String merged = SmartMergeUtil.mergeJson(oldJson, newSchema);
        JsonObject obj = JsonParser.parseString(merged).getAsJsonObject();

        Assert.assertEquals("John Doe", obj.get("name").getAsString());
        Assert.assertEquals(28, obj.get("age").getAsInt());
        Assert.assertEquals("", obj.get("email").getAsString()); // new field added with default value
    }

    @Test
    public void testNestedObjectMerge() {
        String oldJson = "{\"user\":{\"name\":\"Alice\",\"address\":{\"city\":\"Hanoi\"}}}";
        String newSchema = "{\"user\":{\"name\":\"\",\"address\":{\"city\":\"\",\"zip\":\"\"},\"phone\":\"\"}}";

        String merged = SmartMergeUtil.mergeJson(oldJson, newSchema);
        JsonObject obj = JsonParser.parseString(merged).getAsJsonObject();

        JsonObject user = obj.getAsJsonObject("user");
        Assert.assertEquals("Alice", user.get("name").getAsString());
        Assert.assertEquals("", user.get("phone").getAsString()); // new field in user

        JsonObject address = user.getAsJsonObject("address");
        Assert.assertEquals("Hanoi", address.get("city").getAsString());
        Assert.assertEquals("", address.get("zip").getAsString()); // new field in address
    }

    @Test
    public void testArrayMergePreservesExistingItems() {
        String oldJson = "{\"tags\":[\"java\",\"spring\"]}";
        String newSchema = "{\"tags\":[\"\"]}";

        String merged = SmartMergeUtil.mergeJson(oldJson, newSchema);
        JsonObject obj = JsonParser.parseString(merged).getAsJsonObject();

        Assert.assertEquals(2, obj.getAsJsonArray("tags").size());
        Assert.assertEquals("java", obj.getAsJsonArray("tags").get(0).getAsString());
        Assert.assertEquals("spring", obj.getAsJsonArray("tags").get(1).getAsString());
    }

    @Test
    public void testArrayMergeUsesTemplateIfOldArrayEmpty() {
        String oldJson = "{\"tags\":[]}";
        String newSchema = "{\"tags\":[\"\"]}";

        String merged = SmartMergeUtil.mergeJson(oldJson, newSchema);
        JsonObject obj = JsonParser.parseString(merged).getAsJsonObject();

        Assert.assertEquals(1, obj.getAsJsonArray("tags").size());
    }
}
