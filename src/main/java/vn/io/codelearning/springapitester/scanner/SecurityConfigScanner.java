package vn.io.codelearning.springapitester.scanner;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.TextOccurenceProcessor;
import com.intellij.psi.search.UsageSearchContext;
import vn.io.codelearning.springapitester.model.HttpMethodEnum;
import vn.io.codelearning.springapitester.model.PublicSecurityRule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SecurityConfigScanner {

    public static List<PublicSecurityRule> scanForPublicRules(Project project) {
        List<PublicSecurityRule> rules = new ArrayList<>();
        Set<PsiElement> processedCalls = new HashSet<>();

        String[] searchWords = {"permitAll", "anonymous", "ignoring"};
        
        for (String word : searchWords) {
            PsiSearchHelper.getInstance(project).processElementsWithWord(
                (element, offsetInElement) -> {
                    if (element instanceof PsiIdentifier && element.getParent() instanceof PsiReferenceExpression) {
                        PsiReferenceExpression refExpr = (PsiReferenceExpression) element.getParent();
                        if (refExpr.getParent() instanceof PsiMethodCallExpression) {
                            PsiMethodCallExpression methodCall = (PsiMethodCallExpression) refExpr.getParent();
                            if (processedCalls.add(methodCall)) {
                                extractRulesFromMethodCall(methodCall, rules);
                            }
                        }
                    }
                    return true;
                },
                GlobalSearchScope.projectScope(project),
                word,
                UsageSearchContext.IN_CODE,
                true
            );
        }
        
        return rules;
    }

    private static void extractRulesFromMethodCall(PsiMethodCallExpression publicCall, List<PublicSecurityRule> rules) {
        // publicCall is something like .permitAll()
        // We need to find the preceding .requestMatchers(...) or .antMatchers(...) or .mvcMatchers(...)
        PsiExpression qualifier = publicCall.getMethodExpression().getQualifierExpression();
        while (qualifier instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression qualCall = (PsiMethodCallExpression) qualifier;
            String qualMethodName = qualCall.getMethodExpression().getReferenceName();
            
            if (qualMethodName != null && (qualMethodName.equals("requestMatchers") || 
                qualMethodName.equals("antMatchers") || 
                qualMethodName.equals("mvcMatchers") ||
                qualMethodName.equals("ignoring"))) {
                
                parseMatcherArguments(qualCall, rules);
                break;
            }
            qualifier = qualCall.getMethodExpression().getQualifierExpression();
        }
    }

    private static void parseMatcherArguments(PsiMethodCallExpression matcherCall, List<PublicSecurityRule> rules) {
        PsiExpression[] args = matcherCall.getArgumentList().getExpressions();
        if (args.length == 0) return;

        HttpMethodEnum method = null;
        int startIndex = 0;

        // Check if first argument is HttpMethod
        if (args[0] instanceof PsiReferenceExpression) {
            PsiReferenceExpression ref = (PsiReferenceExpression) args[0];
            PsiElement resolved = ref.resolve();
            if (resolved instanceof PsiEnumConstant || resolved instanceof PsiField) {
                String name = ref.getReferenceName();
                if (name != null) {
                    try {
                        method = HttpMethodEnum.valueOf(name);
                        startIndex = 1; // Paths start from next arg
                    } catch (IllegalArgumentException e) {
                        // Not an HttpMethod we know
                    }
                }
            }
        }

        // Parse path strings
        Set<PsiElement> visited = new HashSet<>();
        for (int i = startIndex; i < args.length; i++) {
            extractStringsFromExpression(args[i], rules, method, visited);
        }
    }

    private static void extractStringsFromExpression(PsiExpression expr, List<PublicSecurityRule> rules, HttpMethodEnum method, Set<PsiElement> visited) {
        if (expr == null || !visited.add(expr)) return;

        if (expr instanceof PsiLiteralExpression) {
            Object value = ((PsiLiteralExpression) expr).getValue();
            if (value instanceof String) {
                rules.add(new PublicSecurityRule((String) value, method));
            }
        } else if (expr instanceof PsiReferenceExpression) {
            PsiElement resolved = ((PsiReferenceExpression) expr).resolve();
            if (resolved instanceof PsiVariable) {
                PsiExpression initializer = ((PsiVariable) resolved).getInitializer();
                if (initializer != null) {
                    extractStringsFromExpression(initializer, rules, method, visited);
                }
            }
        } else if (expr instanceof PsiArrayInitializerExpression) {
            for (PsiExpression child : ((PsiArrayInitializerExpression) expr).getInitializers()) {
                extractStringsFromExpression(child, rules, method, visited);
            }
        } else if (expr instanceof PsiNewExpression) {
            PsiArrayInitializerExpression arrayInitializer = ((PsiNewExpression) expr).getArrayInitializer();
            if (arrayInitializer != null) {
                extractStringsFromExpression(arrayInitializer, rules, method, visited);
            }
        } else if (expr instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression call = (PsiMethodCallExpression) expr;
            String methodName = call.getMethodExpression().getReferenceName();
            if ("of".equals(methodName) || "asList".equals(methodName)) {
                for (PsiExpression arg : call.getArgumentList().getExpressions()) {
                    extractStringsFromExpression(arg, rules, method, visited);
                }
            }
        }
    }
}
