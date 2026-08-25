package vn.io.codelearning.springapitester.generator;

import com.google.gson.*;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.HashSet;
import java.util.Set;

/**
 * Động cơ sinh JSON Schema (Bộ khung rỗng) từ DTO Class sử dụng IntelliJ PSI.
 */
public class DtoJsonGenerator {

    private static final int MAX_DEPTH = 3;
    // Khởi tạo Gson với PrettyPrinting để chuỗi JSON sinh ra có thụt lề đẹp mắt
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Hàm chính: Nhận FQN của class DTO (vd: "com.example.CreateUserRequest") và sinh ra JSON Template.
     */
    public static String generateJsonTemplate(String classFqn, Project project) {
        if (classFqn == null || classFqn.trim().isEmpty()) {
            return "{}";
        }

        // Bắt buộc phải chạy trong ReadAction khi làm việc với cây PSI
        return ReadAction.compute(() -> {
            PsiClass psiClass = JavaPsiFacade.getInstance(project)
                    .findClass(classFqn, GlobalSearchScope.projectScope(project));

            if (psiClass == null) {
                return "{}";
            }

            JsonObject jsonObject = buildJsonObject(psiClass, 0, new HashSet<>());
            return GSON.toJson(jsonObject);
        });
    }

    private static JsonObject buildJsonObject(PsiClass psiClass, int depth, Set<String> visited) {
        if (depth > MAX_DEPTH) {
            return new JsonObject();
        }

        String qualifiedName = psiClass.getQualifiedName();
        if (qualifiedName != null) {
            // Chặn đệ quy vòng (Circular Dependency Guard)
            if (visited.contains(qualifiedName)) {
                return new JsonObject();
            }
            visited.add(qualifiedName);
        }

        JsonObject result = new JsonObject();

        if (psiClass.isRecord()) {
            // Nhánh 1: Xử lý Java 14+ Record
            for (PsiRecordComponent component : psiClass.getRecordComponents()) {
                if (hasJsonIgnore(component)) continue;
                String jsonKey = resolveJsonKey(component);
                JsonElement jsonValue = resolveEmptyValue(component.getType(), depth, visited);
                result.add(jsonKey, jsonValue);
            }
        } else {
            // Nhánh 2: Xử lý Class thông thường (Lombok hỗ trợ sẵn qua PSI)
            for (PsiField field : psiClass.getAllFields()) {
                // Bỏ qua static và transient
                if (field.hasModifierProperty(PsiModifier.STATIC) || field.hasModifierProperty(PsiModifier.TRANSIENT)) {
                    continue;
                }
                // Bỏ qua field hệ thống (synthetic)
                if (field.getName().startsWith("$")) {
                    continue;
                }
                // Bỏ qua @JsonIgnore
                if (hasJsonIgnore(field)) {
                    continue;
                }
                // Bỏ qua field thừa kế từ các class gốc của Java (như serialVersionUID)
                PsiClass containingClass = field.getContainingClass();
                if (containingClass != null) {
                    String fqn = containingClass.getQualifiedName();
                    if (fqn != null && fqn.startsWith("java.")) {
                        continue;
                    }
                }

                String jsonKey = resolveJsonKey(field);
                JsonElement jsonValue = resolveEmptyValue(field.getType(), depth, visited);
                result.add(jsonKey, jsonValue);
            }
        }

        // Gỡ class khỏi visited để các nhánh khác trong cây vẫn có thể duyệt lại class này
        if (qualifiedName != null) {
            visited.remove(qualifiedName);
        }

        return result;
    }

    private static boolean hasJsonIgnore(PsiModifierListOwner element) {
        return element.hasAnnotation("com.fasterxml.jackson.annotation.JsonIgnore");
    }

    private static String resolveJsonKey(PsiVariable field) {
        PsiModifierListOwner owner = (PsiModifierListOwner) field;
        PsiAnnotation annotation = owner.getAnnotation("com.fasterxml.jackson.annotation.JsonProperty");
        if (annotation != null) {
            PsiAnnotationMemberValue valueAttr = annotation.findAttributeValue("value");
            if (valueAttr != null) {
                String text = valueAttr.getText().replace("\"", "").trim();
                if (!text.isEmpty()) {
                    return text; // Dùng tên custom
                }
            }
        }
        return field.getName(); // Dùng tên gốc
    }

    private static JsonElement resolveEmptyValue(PsiType type, int depth, Set<String> visited) {
        String typeFqn = type.getCanonicalText();
        String rawType = typeFqn;
        int genericIdx = typeFqn.indexOf('<');
        if (genericIdx > 0) {
            rawType = typeFqn.substring(0, genericIdx); // Cắt bỏ generic để so sánh (java.util.List<...>)
        }

        // 1. Primitives
        if (rawType.equals("int") || rawType.equals("long") || rawType.equals("short") ||
            rawType.equals("byte") || rawType.equals("float") || rawType.equals("double")) {
            return new JsonPrimitive(0);
        }
        if (rawType.equals("boolean")) {
            return new JsonPrimitive(false);
        }
        if (rawType.equals("char")) {
            return new JsonPrimitive("");
        }

        // 2. Wrappers Numbers
        if (rawType.equals("java.lang.Integer") || rawType.equals("java.lang.Long") ||
            rawType.equals("java.lang.Short") || rawType.equals("java.lang.Byte") ||
            rawType.equals("java.lang.Float") || rawType.equals("java.lang.Double") ||
            rawType.equals("java.math.BigDecimal") || rawType.equals("java.math.BigInteger") ||
            rawType.equals("java.lang.Number")) {
            return new JsonPrimitive(0);
        }

        // 3. Wrapper Boolean
        if (rawType.equals("java.lang.Boolean")) {
            return new JsonPrimitive(false);
        }

        // 4. String & CharSequence
        if (rawType.equals("java.lang.String") || rawType.equals("java.lang.CharSequence")) {
            return new JsonPrimitive("");
        }

        // 5. Date/Time & UUID
        if (rawType.equals("java.time.LocalDate") || rawType.equals("java.time.LocalDateTime") ||
            rawType.equals("java.time.Instant") || rawType.equals("java.time.ZonedDateTime") ||
            rawType.equals("java.time.OffsetDateTime") || rawType.equals("java.util.Date") ||
            rawType.equals("java.sql.Date") || rawType.equals("java.util.UUID")) {
            return new JsonPrimitive("");
        }

        // 6. Array (T[])
        if (type instanceof PsiArrayType arrayType) {
            PsiType componentType = arrayType.getComponentType();
            JsonArray array = new JsonArray();
            array.add(resolveEmptyValue(componentType, depth, visited));
            return array;
        }

        // 7. Collection (List<T>, Set<T>)
        if (isCollectionType(rawType)) {
            PsiType innerType = extractGenericParameter(type, 0);
            JsonArray array = new JsonArray();
            if (innerType != null) {
                array.add(resolveEmptyValue(innerType, depth, visited));
            }
            return array;
        }

        // 8. Map<K, V> -> { "key": V }
        if (isMapType(rawType)) {
            PsiType valueType = extractGenericParameter(type, 1);
            JsonObject mapObj = new JsonObject();
            if (valueType != null) {
                mapObj.add("key", resolveEmptyValue(valueType, depth, visited));
            } else {
                mapObj.addProperty("key", "");
            }
            return mapObj;
        }

        // 9. Enum & Nested DTO
        PsiClass psiClass = resolveClass(type);
        if (psiClass != null) {
            if (psiClass.isEnum()) {
                return new JsonPrimitive("");
            }
            if (!rawType.startsWith("java.")) {
                return buildJsonObject(psiClass, depth + 1, visited); // Đệ quy vào Object con
            }
        }

        // 10. Fallback
        return new JsonPrimitive("");
    }

    private static PsiType extractGenericParameter(PsiType type, int index) {
        if (type instanceof PsiClassType classType) {
            PsiType[] parameters = classType.getParameters();
            if (parameters.length > index) {
                return parameters[index];
            }
        }
        return null;
    }

    private static boolean isCollectionType(String rawType) {
        return rawType.equals("java.util.List") || rawType.equals("java.util.Set") ||
               rawType.equals("java.util.Collection") || rawType.equals("java.util.ArrayList") ||
               rawType.equals("java.util.LinkedList") || rawType.equals("java.util.HashSet") ||
               rawType.equals("java.util.TreeSet");
    }

    private static boolean isMapType(String rawType) {
        return rawType.equals("java.util.Map") || rawType.equals("java.util.HashMap") ||
               rawType.equals("java.util.LinkedHashMap") || rawType.equals("java.util.TreeMap") ||
               rawType.equals("java.util.concurrent.ConcurrentHashMap");
    }

    private static PsiClass resolveClass(PsiType type) {
        if (type instanceof PsiClassType classType) {
            return classType.resolve();
        }
        return null;
    }
}
