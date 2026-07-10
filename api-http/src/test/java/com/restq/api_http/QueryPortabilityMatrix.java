package com.restq.api_http;

import com.restq.api_http.Repositories.tpch.*;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Executes every TPC-H repository {@code @Query} against a live database so
 * dialect translation is proven, not assumed. Engine subclasses provide the
 * database via Testcontainers; the schema is created from the entities on an
 * empty database — results are empty, but any JPQL construct that a dialect
 * cannot translate (LIMIT, EXISTS, date arithmetic, ...) fails loudly here
 * instead of at benchmark time.
 */
abstract class QueryPortabilityMatrix {

    static final List<Class<?>> TPCH_REPOSITORIES = List.of(
            CustomerRepository.class, LineItemRepository.class,
            NationRepository.class, OrderRepository.class,
            PartRepository.class, PartSuppRepository.class,
            SupplierRepository.class);

    @Autowired
    ApplicationContext context;

    @TestFactory
    List<DynamicTest> everyRepositoryQueryExecutes() {
        List<DynamicTest> tests = new ArrayList<>();
        for (Class<?> repoType : TPCH_REPOSITORIES) {
            Object repo = context.getBean(repoType);
            for (Method method : repoType.getDeclaredMethods()) {
                if (method.getAnnotation(Query.class) == null) {
                    continue;
                }
                tests.add(DynamicTest.dynamicTest(
                        repoType.getSimpleName() + "." + method.getName(),
                        () -> invoke(repo, method)));
            }
        }
        return tests;
    }

    private void invoke(Object repo, Method method) {
        Object[] args = Arrays.stream(method.getGenericParameterTypes())
                .map(QueryPortabilityMatrix::sampleValue).toArray();
        try {
            method.invoke(repo, args);
        } catch (InvocationTargetException e) {
            fail("query failed on this engine: " + e.getCause(), e.getCause());
        } catch (IllegalAccessException e) {
            fail(e);
        }
    }

    /** Any type-correct value: the DB is empty, only SQL validity matters.
     * Generic types are inspected so List&lt;Integer&gt; and List&lt;String&gt;
     * parameters get element-type-correct samples. */
    private static Object sampleValue(java.lang.reflect.Type generic) {
        if (generic instanceof java.lang.reflect.ParameterizedType p
                && p.getRawType() == List.class) {
            Object element = sampleValue(p.getActualTypeArguments()[0]);
            return List.of(element, element);
        }
        if (!(generic instanceof Class<?> type)) {
            throw new IllegalArgumentException("no sample value for " + generic);
        }
        if (type == LocalDate.class) return LocalDate.of(1995, 3, 15);
        if (type == String.class) return "ASIA";
        if (type == Integer.class || type == int.class) return 10;
        if (type == Long.class || type == long.class) return 1L;
        if (type == Double.class || type == double.class) return 0.05;
        if (type == BigDecimal.class) return new BigDecimal("0.05");
        if (Pageable.class.isAssignableFrom(type)) return PageRequest.of(0, 10);
        throw new IllegalArgumentException("no sample value for " + generic);
    }
}
