package vn.io.codelearning.springapitester.scanner;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.impl.java.stubs.index.JavaAnnotationIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

final class SpringApplicationProfileReader {

    private SpringApplicationProfileReader() {
    }

    static Map<String, String> readDefaultProfiles(Project project, @Nullable Module module) {
        GlobalSearchScope scope = module != null
                ? GlobalSearchScope.moduleScope(module)
                : GlobalSearchScope.projectScope(project);
        Map<String, String> profiles = new LinkedHashMap<>();
        for (PsiAnnotation annotation : JavaAnnotationIndex.getInstance().get("SpringBootApplication", project, scope)) {
            PsiClass applicationClass = PsiTreeUtil.getParentOfType(annotation, PsiClass.class);
            if (applicationClass == null) continue;
            for (PsiMethod mainMethod : applicationClass.findMethodsByName("main", false)) {
                for (PsiMethodCallExpression call : PsiTreeUtil.findChildrenOfType(mainMethod, PsiMethodCallExpression.class)) {
                    if (isSpringApplicationDefaultPropertiesCall(call)) {
                        readProfileEntries(call.getArgumentList().getExpressions(), profiles);
                    }
                }
            }
        }
        return profiles;
    }

    private static boolean isSpringApplicationDefaultPropertiesCall(PsiMethodCallExpression call) {
        if (!"setDefaultProperties".equals(call.getMethodExpression().getReferenceName())) return false;
        PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier instanceof PsiReferenceExpression reference
                && reference.resolve() instanceof PsiVariable variable) {
            return "org.springframework.boot.SpringApplication".equals(variable.getType().getCanonicalText());
        }
        return false;
    }

    private static void readProfileEntries(PsiExpression[] arguments, Map<String, String> profiles) {
        if (arguments.length != 1 || !(arguments[0] instanceof PsiMethodCallExpression mapCall)) return;
        if (!"of".equals(mapCall.getMethodExpression().getReferenceName())) return;
        PsiExpression qualifier = mapCall.getMethodExpression().getQualifierExpression();
        if (!(qualifier instanceof PsiReferenceExpression reference)
                || !(reference.resolve() instanceof PsiClass mapClass)
                || !"java.util.Map".equals(mapClass.getQualifiedName())) return;

        PsiExpression[] entries = mapCall.getArgumentList().getExpressions();
        for (int index = 0; index + 1 < entries.length; index += 2) {
            Object key = JavaPsiFacade.getInstance(mapCall.getProject()).getConstantEvaluationHelper()
                    .computeConstantExpression(entries[index]);
            if (!"spring.profiles.default".equals(key) && !"spring.profiles.active".equals(key)) continue;
            Object value = JavaPsiFacade.getInstance(mapCall.getProject()).getConstantEvaluationHelper()
                    .computeConstantExpression(entries[index + 1]);
            if (value instanceof String profile && !profile.isBlank()) {
                profiles.put((String) key, profile.trim());
            }
        }
    }
}
