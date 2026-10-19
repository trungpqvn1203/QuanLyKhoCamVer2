package com.grainwarehouse.common.search;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;
import org.springframework.data.jpa.domain.Specification;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Giữ mọi module tìm kiếm theo cùng một khuôn (docs/architecture/search-pattern.md).
 * Quét class trong com.grainwarehouse, không cần Spring context hay DB, không thêm thư viện (ArchUnit...).
 * Fail ở đây nghĩa là module mới lệch quy ước — sửa module, đừng nới test.
 */
class SearchConventionTest {

    private static final String BASE_PACKAGE = "com.grainwarehouse";

    private static List<Class<?>> criteriaClasses;
    private static List<Class<?>> specificationClasses;

    @BeforeAll
    static void scan() {
        criteriaClasses = scan(".*\\.dto\\.\\w+SearchCriteria");
        specificationClasses = scan(".*\\.repository\\.\\w+Specifications");
    }

    @Test
    @DisplayName("Quét được các module hiện có (tránh test 'pass' vì không tìm thấy gì)")
    void shouldFindExistingModules() {
        assertThat(criteriaClasses).extracting(Class::getSimpleName)
                .contains("ProductSearchCriteria", "SupplierSearchCriteria", "WarehouseSearchCriteria",
                        "InboundReceiptSearchCriteria");
    }

    @Test
    @DisplayName("XxxSearchCriteria là record (bất biến, Spring bind thẳng từ query string)")
    void criteriaShouldBeRecords() {
        assertThat(criteriaClasses).allSatisfy(type -> assertThat(type.isRecord())
                .as("%s phải là record", type.getSimpleName()).isTrue());
    }

    @Test
    @DisplayName("Có field keyword → implement HasKeyword; có field active → implement HasActive")
    void criteriaShouldImplementMatchingInterfaces() {
        assertThat(criteriaClasses).allSatisfy(type -> {
            List<String> fields = Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList();
            if (fields.contains("keyword")) {
                assertThat(HasKeyword.class.isAssignableFrom(type))
                        .as("%s có keyword nhưng chưa implement HasKeyword", type.getSimpleName()).isTrue();
            }
            if (fields.contains("active")) {
                assertThat(HasActive.class.isAssignableFrom(type))
                        .as("%s có active nhưng chưa implement HasActive", type.getSimpleName()).isTrue();
            }
        });
    }

    @Test
    @DisplayName("Mỗi XxxSearchCriteria có XxxSpecifications với public static from(XxxSearchCriteria) trả Specification")
    void eachCriteriaShouldHaveSpecificationsFrom() {
        assertThat(criteriaClasses).allSatisfy(criteria -> {
            String entityName = criteria.getSimpleName().replace("SearchCriteria", "");
            Class<?> specifications = specificationClasses.stream()
                    .filter(type -> type.getSimpleName().equals(entityName + "Specifications"))
                    .findFirst()
                    .orElse(null);
            assertThat(specifications).as("Thiếu %sSpecifications cho %s", entityName, criteria.getSimpleName())
                    .isNotNull();

            Method from = Arrays.stream(specifications.getDeclaredMethods())
                    .filter(method -> method.getName().equals("from"))
                    .filter(method -> Arrays.equals(method.getParameterTypes(), new Class<?>[] {criteria}))
                    .findFirst()
                    .orElse(null);
            assertThat(from).as("%s thiếu from(%s)", specifications.getSimpleName(), criteria.getSimpleName())
                    .isNotNull();
            assertThat(Modifier.isStatic(from.getModifiers()) && Modifier.isPublic(from.getModifiers()))
                    .as("%s.from phải public static", specifications.getSimpleName()).isTrue();
            assertThat(Specification.class.isAssignableFrom(from.getReturnType()))
                    .as("%s.from phải trả Specification", specifications.getSimpleName()).isTrue();
        });
    }

    @Test
    @DisplayName("XxxSpecifications là lớp tiện ích: final, không tạo instance được")
    void specificationsShouldBeUtilityClasses() {
        assertThat(specificationClasses).allSatisfy(type -> {
            assertThat(Modifier.isFinal(type.getModifiers())).as("%s phải final", type.getSimpleName()).isTrue();
            assertThat(type.getDeclaredConstructors()).allSatisfy(constructor -> assertThat(
                    Modifier.isPrivate(constructor.getModifiers()))
                    .as("Constructor của %s phải private", type.getSimpleName()).isTrue());
        });
    }

    private static List<Class<?>> scan(String classNameRegex) {
        // Mặc định Spring chỉ nhận @Component; ghi đè để nhận mọi class khớp tên (record, lớp tiện ích...)
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return true;
            }
        };
        scanner.addIncludeFilter(new RegexPatternTypeFilter(Pattern.compile(classNameRegex)));
        return scanner.findCandidateComponents(BASE_PACKAGE).stream()
                .<Class<?>>map(definition -> {
                    try {
                        return Class.forName(definition.getBeanClassName());
                    } catch (ClassNotFoundException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .toList();
    }
}
