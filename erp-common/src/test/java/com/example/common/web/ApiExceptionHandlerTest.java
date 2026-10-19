package com.example.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void integrityViolationsAreConflictsWithoutLeakingSqlDetails() {
        var problem = handler.onIntegrityViolation(
                new DataIntegrityViolationException("duplicate key value violates unique constraint \"ux_item_sku\""));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getDetail()).doesNotContain("ux_item_sku");
    }

    @Test
    void illegalArgumentsAreBadRequests() {
        var problem = handler.onIllegalArgument(new IllegalArgumentException("bad uuid"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("bad uuid");
    }

    @Test
    void badSortParametersAreBadRequestsButOtherApiMisuseStaysAServerError() {
        var sort = handler.onInvalidDataAccessUsage(new org.springframework.dao.InvalidDataAccessApiUsageException(
                "Sort expression '[\"x\"]: ASC' must only contain property references"));
        var other = handler.onInvalidDataAccessUsage(
                new org.springframework.dao.InvalidDataAccessApiUsageException("something else"));

        assertThat(sort.getStatus()).isEqualTo(400);
        assertThat(other.getStatus()).isEqualTo(500);
    }

    @Test
    void unknownSortPropertyIsABadRequest() {
        var problem = handler.onUnknownSortProperty(new org.springframework.data.mapping.PropertyReferenceException(
                "nope", org.springframework.data.util.TypeInformation.of(String.class), java.util.List.of()));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).contains("nope");
    }
}
