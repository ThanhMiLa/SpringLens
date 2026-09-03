package vn.io.codelearning.springapitester.scanner;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.search.GlobalSearchScope;
import vn.io.codelearning.springapitester.model.EndpointModel;
import vn.io.codelearning.springapitester.model.HttpMethodEnum;
import vn.io.codelearning.springapitester.model.ParamTypeEnum;
import vn.io.codelearning.springapitester.model.ParameterModel;

import java.util.*;

/**
 * Động cơ quét PSI: Duyệt toàn bộ Spring Controller trong project và xây dựng danh sách EndpointModel.
 */
public class SpringEndpointScanner {

    private static final SpringEndpointScanner INSTANCE = new SpringEndpointScanner();

    public static SpringEndpointScanner getInstance() {
        return INSTANCE;
    }

    private SpringEndpointScanner() {}

    /**
     * Quét toàn bộ Project trong môi trường SmartMode và ReadAction an toàn.
     */
    public List<EndpointModel> scanEndpoints(Project project) {
        if (project == null || project.isDisposed()) {
            return Collections.emptyList();
        }

        return DumbService.getInstance(project).runReadActionInSmartMode(new Computable<>() {
            @Override
            public List<EndpointModel> compute() {
                return doScan(project);
            }
        });
    }

    private List<EndpointModel> doScan(Project project) {
        List<EndpointModel> result = new ArrayList<>();
        Set<PsiClass> controllerClasses = findControllerClasses(project);
        List<vn.io.codelearning.springapitester.model.PublicSecurityRule> globalPublicRules = SecurityConfigScanner.scanForPublicRules(project);

        for (PsiClass controllerClass : controllerClasses) {
            String packageName = extractPackageName(controllerClass);
            String controllerName = controllerClass.getName();
            List<String> classPaths = extractClassBasePaths(controllerClass);
            boolean classIsRest = SpringAnnotationUtils.isRestController(controllerClass);

            // Determine module and base URL
            com.intellij.openapi.module.Module module = com.intellij.openapi.module.ModuleUtilCore.findModuleForPsiElement(controllerClass);
            String moduleName = "Unknown";
            String directBaseUrl = "http://localhost:8080";
            if (module != null) {
                String rawName = module.getName();
                moduleName = rawName.endsWith(".main") ? rawName.substring(0, rawName.length() - 5) : rawName;
                
                // Try to get folder name from content roots
                com.intellij.openapi.vfs.VirtualFile[] contentRoots = com.intellij.openapi.roots.ModuleRootManager.getInstance(module).getContentRoots();
                if (contentRoots.length > 0) {
                    moduleName = contentRoots[0].getName();
                }
                
                vn.io.codelearning.springapitester.util.SpringBootConfigReader.AppConfig config = vn.io.codelearning.springapitester.util.SpringBootConfigReader.extractAppConfig(project, module);
                directBaseUrl = config.baseUrl;
                if (config.appName != null && !config.appName.isEmpty()) {
                    moduleName = config.appName;
                }
            } else {
                vn.io.codelearning.springapitester.util.SpringBootConfigReader.AppConfig config = vn.io.codelearning.springapitester.util.SpringBootConfigReader.extractAppConfig(project);
                directBaseUrl = config.baseUrl;
                if (config.appName != null && !config.appName.isEmpty()) {
                    moduleName = config.appName;
                }
            }

            // Dùng getAllMethods() để bắt cả method từ Interface/Class cha, lọc trùng bằng signature
            Set<String> processedSignatures = new HashSet<>();

            for (PsiMethod method : controllerClass.getAllMethods()) {
                PsiClass containingClass = method.getContainingClass();
                if (containingClass != null && "java.lang.Object".equals(containingClass.getQualifiedName())) {
                    continue;
                }

                String signature = buildMethodSignature(method);
                if (processedSignatures.contains(signature)) {
                    continue;
                }
                processedSignatures.add(signature);

                List<EndpointModel> methodEndpoints = processMethod(method, controllerClass, classPaths, packageName, controllerName, classIsRest, globalPublicRules);
                for (EndpointModel ep : methodEndpoints) {
                    ep.setModuleName(moduleName);
                    ep.setDirectBaseUrl(directBaseUrl);
                    ep.setMethodSignature(signature);
                }
                result.addAll(methodEndpoints);
            }
        }

        result.sort(Comparator.comparing(EndpointModel::getModuleName, Comparator.nullsFirst(String::compareTo))
                .thenComparing(EndpointModel::getPackageName)
                .thenComparing(EndpointModel::getControllerName)
                .thenComparing(EndpointModel::getPath));

        return result;
    }

    private Set<PsiClass> findControllerClasses(Project project) {
        Set<PsiClass> classes = new HashSet<>();
        GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        JavaAnnotationIndex index = JavaAnnotationIndex.getInstance();

        for (String annotationShortName : List.of("RestController", "Controller")) {
            for (PsiAnnotation anno : index.get(annotationShortName, project, scope)) {
                PsiClass psiClass = getContainingClass(anno);
                if (psiClass != null && SpringAnnotationUtils.isControllerClass(psiClass)) {
                    classes.add(psiClass);
                }
            }
        }

        // Bắt meta-annotations có chứa @RequestMapping
        for (PsiAnnotation anno : index.get("RequestMapping", project, scope)) {
            PsiClass psiClass = getContainingClass(anno);
            if (psiClass != null && SpringAnnotationUtils.isControllerClass(psiClass)) {
                classes.add(psiClass);
            }
        }

        return classes;
    }

    private PsiClass getContainingClass(PsiAnnotation annotation) {
        PsiElement parent = annotation.getParent();
        if (parent instanceof PsiModifierList modifierList && modifierList.getParent() instanceof PsiClass psiClass) {
            return psiClass;
        }
        return null;
    }

    private List<String> extractClassBasePaths(PsiClass controllerClass) {
        PsiAnnotation requestMapping = controllerClass.getAnnotation(
                "org.springframework.web.bind.annotation.RequestMapping");
        if (requestMapping != null) {
            return SpringAnnotationUtils.extractPathsFromAnnotation(requestMapping);
        }

        // Kiểm tra meta-annotation
        for (PsiAnnotation anno : controllerClass.getAnnotations()) {
            PsiAnnotation metaRM = findMetaRequestMapping(anno);
            if (metaRM != null) {
                List<String> paths = SpringAnnotationUtils.extractPathsFromAnnotation(metaRM);
                if (!paths.isEmpty() && !paths.get(0).isEmpty()) {
                    return paths;
                }
            }
        }

        return List.of("");
    }

    private PsiAnnotation findMetaRequestMapping(PsiAnnotation annotation) {
        PsiJavaCodeReferenceElement ref = annotation.getNameReferenceElement();
        if (ref == null) return null;
        PsiElement resolved = ref.resolve();
        if (resolved instanceof PsiClass annoClass && annoClass.isAnnotationType()) {
            return annoClass.getAnnotation("org.springframework.web.bind.annotation.RequestMapping");
        }
        return null;
    }

    private List<EndpointModel> processMethod(PsiMethod method, PsiClass controllerClass,
                                               List<String> classPaths, String packageName,
                                               String controllerName, boolean classIsRest,
                                               List<vn.io.codelearning.springapitester.model.PublicSecurityRule> globalPublicRules) {
        List<EndpointModel> endpoints = new ArrayList<>();

        for (PsiAnnotation anno : method.getAnnotations()) {
            String qName = anno.getQualifiedName();
            if (qName == null) continue;

            if (SpringAnnotationUtils.MAPPING_ANNOTATIONS.contains(qName) || qName.endsWith("Mapping")) {
                List<HttpMethodEnum> httpMethods = SpringAnnotationUtils.extractHttpMethods(anno);
                List<String> methodPaths = SpringAnnotationUtils.extractPathsFromAnnotation(anno);

                for (HttpMethodEnum httpMethod : httpMethods) {
                    for (String classPath : classPaths) {
                        for (String methodPath : methodPaths) {
                            String fullPath = SpringUrlUtils.combinePaths(classPath, methodPath);

                            EndpointModel model = new EndpointModel(httpMethod, fullPath, controllerName, packageName, method.getName());
                            model.setSecured(SpringAnnotationUtils.isEndpointSecured(method, controllerClass, fullPath, httpMethod, globalPublicRules));
                            model.setRestEndpoint(SpringAnnotationUtils.isRestMethod(method, classIsRest));

                            PsiType returnType = method.getReturnType();
                            if (returnType != null) {
                                model.setReturnTypeClassFqn(returnType.getCanonicalText());
                            }

                            extractParameters(method, model);

                            endpoints.add(model);
                        }
                    }
                }
            }
        }
        return endpoints;
    }

    private void extractParameters(PsiMethod method, EndpointModel endpoint) {
        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            String paramName = parameter.getName();
            String typeFqn = parameter.getType().getCanonicalText();
            String simpleType = parameter.getType().getPresentableText();

            String annotationName = null;
            String explicitName = null;
            String defaultValue = "";
            Boolean explicitRequired = null;

            for (PsiAnnotation anno : parameter.getAnnotations()) {
                String qName = anno.getQualifiedName();
                if (qName == null) continue;

                if (qName.contains("PathVariable") || qName.contains("RequestParam") ||
                    qName.contains("RequestHeader") || qName.contains("CookieValue") ||
                    qName.contains("RequestBody") || qName.contains("RequestPart") ||
                    qName.contains("ModelAttribute") || qName.contains("MatrixVariable")) {
                    annotationName = qName;

                    PsiAnnotationMemberValue nameVal = anno.findAttributeValue("name");
                    if (nameVal == null) nameVal = anno.findAttributeValue("value");
                    if (nameVal != null) {
                        String extracted = nameVal.getText().replace("\"", "").trim();
                        if (!extracted.isEmpty()) {
                            explicitName = extracted;
                        }
                    }

                    PsiAnnotationMemberValue defVal = anno.findAttributeValue("defaultValue");
                    if (defVal != null) {
                        String extracted = defVal.getText().replace("\"", "").trim();
                        // Lọc bỏ hằng số ValueConstants.DEFAULT_NONE của Spring ("\n\t\t\n\t\t\n\uE000\uE001\uE002\n\t\t\t\t\n")
                        // Khi gọi getText() nó sẽ trả về raw string chứa các ký tự \ n \ t
                        if (!extracted.isEmpty() 
                            && !extracted.contains("\\n\\t") 
                            && !extracted.contains("\\uE000")) {
                            defaultValue = extracted;
                        }
                    }

                    PsiAnnotationMemberValue reqVal = anno.findAttributeValue("required");
                    if (reqVal != null) {
                        String reqText = reqVal.getText().trim();
                        if ("true".equals(reqText) || "false".equals(reqText)) {
                            explicitRequired = Boolean.parseBoolean(reqText);
                        }
                    }
                    break;
                }
            }

            ParamTypeEnum paramType = ParamTypeEnum.fromAnnotationOrType(annotationName, typeFqn);

            if (paramType == ParamTypeEnum.FRAMEWORK_INTERNAL) {
                continue;
            }

            String finalName = (explicitName != null && !explicitName.isBlank()) ? explicitName : paramName;

            boolean required;
            if (explicitRequired != null) {
                required = explicitRequired;
            } else {
                required = switch (paramType) {
                    case PATH_VARIABLE, QUERY_PARAM, HEADER, COOKIE, REQUEST_BODY -> true;
                    default -> false;
                };
            }

            if (paramType == ParamTypeEnum.REQUEST_BODY) {
                endpoint.setRequestBodyClassFqn(typeFqn);
                ParameterModel paramModel = new ParameterModel(finalName, paramType, simpleType, defaultValue, required, "", "");
                endpoint.addParameter(paramModel);
            } else if (paramType == ParamTypeEnum.MODEL_ATTRIBUTE) {
                // Xác định kiểu param sẽ bung ra (Query Param cho GET, Form Data cho POST)
                ParamTypeEnum explodedType = ParamTypeEnum.FORM_DATA;
                if (endpoint.getHttpMethod() == HttpMethodEnum.GET || endpoint.getHttpMethod() == HttpMethodEnum.DELETE) {
                    explodedType = ParamTypeEnum.QUERY_PARAM;
                }
                
                if (explodedType == ParamTypeEnum.FORM_DATA) {
                    endpoint.setBodyType(vn.io.codelearning.springapitester.model.RequestBodyType.FORM_DATA);
                }
                
                // Đệ quy nhẹ lấy các field của ModelAttribute
                PsiClass psiClass = extractPsiClass(parameter.getType());
                if (psiClass != null && !psiClass.getQualifiedName().startsWith("java.")) {
                    for (PsiField field : psiClass.getAllFields()) {
                        if (field.hasModifierProperty(PsiModifier.STATIC) || field.hasModifierProperty(PsiModifier.TRANSIENT)) {
                            continue;
                        }
                        String fieldName = field.getName();
                        String fieldType = field.getType().getPresentableText();
                        ParamTypeEnum actualFieldType = explodedType;
                        if (fieldType.contains("MultipartFile") || fieldType.contains("Part")) {
                            actualFieldType = ParamTypeEnum.MULTIPART_FILE;
                            endpoint.setBodyType(vn.io.codelearning.springapitester.model.RequestBodyType.FORM_DATA);
                        }
                        ParameterModel fieldModel = new ParameterModel(fieldName, actualFieldType, fieldType, "", false, "", "");
                        endpoint.addParameter(fieldModel);
                    }
                } else {
                    // Fallback
                    ParameterModel paramModel = new ParameterModel(finalName, explodedType, simpleType, defaultValue, required, "", "");
                    endpoint.addParameter(paramModel);
                }
            } else if (paramType == ParamTypeEnum.MULTIPART_FILE) {
                endpoint.setBodyType(vn.io.codelearning.springapitester.model.RequestBodyType.FORM_DATA);
                ParameterModel paramModel = new ParameterModel(finalName, ParamTypeEnum.MULTIPART_FILE, simpleType, defaultValue, required, "", "");
                endpoint.addParameter(paramModel);
            } else if (paramType == ParamTypeEnum.FORM_DATA) {
                endpoint.setBodyType(vn.io.codelearning.springapitester.model.RequestBodyType.FORM_DATA);
                ParameterModel paramModel = new ParameterModel(finalName, paramType, simpleType, defaultValue, required, "", "");
                endpoint.addParameter(paramModel);
            } else {
                ParameterModel paramModel = new ParameterModel(finalName, paramType, simpleType, defaultValue, required, "", "");
                endpoint.addParameter(paramModel);
            }
        }
    }

    private PsiClass extractPsiClass(PsiType psiType) {
        if (psiType instanceof PsiClassType classType) {
            return classType.resolve();
        }
        return null;
    }

    private String buildMethodSignature(PsiMethod method) {
        StringBuilder sb = new StringBuilder(method.getName());
        sb.append("(");
        PsiParameter[] params = method.getParameterList().getParameters();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(params[i].getType().getCanonicalText());
        }
        sb.append(")");
        return sb.toString();
    }

    private String extractPackageName(PsiClass psiClass) {
        PsiFile file = psiClass.getContainingFile();
        if (file instanceof PsiJavaFile javaFile) {
            return javaFile.getPackageName();
        }
        return "default";
    }
}
