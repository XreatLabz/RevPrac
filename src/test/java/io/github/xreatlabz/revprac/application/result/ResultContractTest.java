package io.github.xreatlabz.revprac.application.result;

import static io.github.xreatlabz.revprac.ContractTestSupport.firstEnumConstant;
import static io.github.xreatlabz.revprac.ContractTestSupport.instantiateRecord;
import static io.github.xreatlabz.revprac.ContractTestSupport.loadClass;
import static io.github.xreatlabz.revprac.ContractTestSupport.recordComponentValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ResultContractTest {

    private static final String RESULT_TYPE = "io.github.xreatlabz.revprac.application.result.Result";
    private static final String OK_TYPE = "io.github.xreatlabz.revprac.application.result.Ok";
    private static final String ERR_TYPE = "io.github.xreatlabz.revprac.application.result.Err";
    private static final String PROBLEM_TYPE = "io.github.xreatlabz.revprac.application.result.Problem";
    private static final String PROBLEM_CATEGORY_TYPE = "io.github.xreatlabz.revprac.application.result.ProblemCategory";

    @Test
    void resultAndProblemRecordsPreserveCodeCategoryMessageAndPath() {
        Class<?> resultType = loadClass(RESULT_TYPE);
        Class<?> okType = loadClass(OK_TYPE);
        Class<?> errType = loadClass(ERR_TYPE);
        Class<?> problemType = loadClass(PROBLEM_TYPE);
        Class<?> problemCategoryType = loadClass(PROBLEM_CATEGORY_TYPE);

        Object category = firstEnumConstant(problemCategoryType);
        Object problem = instantiateRecord(problemType, problemValues(category));
        Object ok = instantiateRecord(okType, Map.of("value", "ready"));
        Object err = instantiateRecord(errType, Map.of("problem", problem));

        assertTrue(resultType.isAssignableFrom(ok.getClass()), "Ok should implement Result");
        assertTrue(resultType.isAssignableFrom(err.getClass()), "Err should implement Result");
        assertEquals("ready", recordComponentValue(ok, "value"));

        assertSame(problem, recordComponentValue(err, "problem"), "Err should preserve the same Problem instance");
        assertEquals("bootstrap.invalid", recordComponentValue(problem, "code"));
        assertSame(category, recordComponentValue(problem, "category"));
        assertEquals("Bootstrap config is invalid", recordComponentValue(problem, "message"));
        assertEquals("bootstrap.fail-fast-on-enable", recordComponentValue(problem, "path"));
    }

    private static Map<String, Object> problemValues(Object category) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("code", "bootstrap.invalid");
        values.put("category", category);
        values.put("message", "Bootstrap config is invalid");
        values.put("path", "bootstrap.fail-fast-on-enable");
        return values;
    }
}
