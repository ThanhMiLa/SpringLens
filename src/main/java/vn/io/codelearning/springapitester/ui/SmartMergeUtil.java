package vn.io.codelearning.springapitester.ui;

import com.google.gson.*;

import java.util.Map;

/**
 * Tiện ích hỗ trợ gộp JSON (Smart Merge) khi người dùng ấn nút "Sync Schema".
 */
public class SmartMergeUtil {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Gộp value từ oldJson vào newSchemaJson.
     * Thuật toán: Giữ nguyên cấu trúc key của newSchemaJson, nếu key đó tồn tại trong oldJson thì copy value qua.
     */
    public static String mergeJson(String oldJson, String newSchemaJson) {
        if (oldJson == null || oldJson.trim().isEmpty()) {
            return newSchemaJson;
        }

        try {
            JsonElement oldElement = JsonParser.parseString(oldJson);
            JsonElement newElement = JsonParser.parseString(newSchemaJson);

            JsonElement mergedElement = mergeElements(oldElement, newElement);
            return GSON.toJson(mergedElement);
        } catch (JsonSyntaxException e) {
            // Nếu oldJson bị lỗi cú pháp, chấp nhận bỏ qua và dùng luôn template mới
            return newSchemaJson;
        }
    }

    private static JsonElement mergeElements(JsonElement oldEl, JsonElement newEl) {
        if (newEl.isJsonObject() && oldEl.isJsonObject()) {
            JsonObject oldObj = oldEl.getAsJsonObject();
            JsonObject newObj = newEl.getAsJsonObject();
            JsonObject result = new JsonObject();

            for (Map.Entry<String, JsonElement> entry : newObj.entrySet()) {
                String key = entry.getKey();
                JsonElement newValue = entry.getValue();

                if (oldObj.has(key)) {
                    // Đệ quy gộp value
                    result.add(key, mergeElements(oldObj.get(key), newValue));
                } else {
                    // Key mới hoàn toàn, dùng value rỗng từ Schema
                    result.add(key, newValue);
                }
            }
            return result;
        } else if (newEl.isJsonArray() && oldEl.isJsonArray()) {
            // Đối với mảng, ta giữ nguyên mảng cũ nếu nó có phần tử (người dùng đã nhập liệu)
            // Nếu mảng cũ rỗng thì mới lấy phần tử template của mảng mới
            JsonArray oldArr = oldEl.getAsJsonArray();
            if (oldArr.size() > 0) {
                return oldArr.deepCopy();
            }
            return newEl.deepCopy();
        } else {
            // Trị số cơ bản (Primitive/String/Null)
            // Ưu tiên value cũ, trừ khi kiểu dữ liệu thay đổi hoàn toàn (ví dụ cũ là String, mới là Object)
            // Nếu kiểu dữ liệu tương thích, giữ value cũ
            if (isCompatible(oldEl, newEl)) {
                return oldEl.deepCopy();
            }
            return newEl.deepCopy();
        }
    }

    private static boolean isCompatible(JsonElement oldEl, JsonElement newEl) {
        if (oldEl.isJsonPrimitive() && newEl.isJsonPrimitive()) {
            return true;
        }
        if (oldEl.isJsonNull() && newEl.isJsonNull()) {
            return true;
        }
        return false;
    }
}
