package com.example.common.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates persistence and argument errors into RFC 7807 problem responses. Validation errors and
 * {@code ResponseStatusException} are already rendered as problem details by Spring MVC
 * ({@code spring.mvc.problemdetails.enabled=true}).
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail onIntegrityViolation(DataIntegrityViolationException ex) {
        log.debug("Data integrity violation", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The request conflicts with existing data (duplicate or still referenced)");
    }

    /** {@code ?sort=nonsense} on a paged endpoint. */
    @ExceptionHandler(PropertyReferenceException.class)
    ProblemDetail onUnknownSortProperty(PropertyReferenceException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Unknown sort property '" + ex.getPropertyName() + "'");
    }

    /** Spring Data/Hibernate reject sort expressions that are not plain, existing property paths. */
    @ExceptionHandler(InvalidDataAccessApiUsageException.class)
    ProblemDetail onInvalidDataAccessUsage(InvalidDataAccessApiUsageException ex) {
        String message = String.valueOf(ex.getMessage());
        boolean badSort = message.startsWith("Sort expression") || message.contains("Could not resolve attribute");
        if (badSort) {
            return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid sort parameter");
        }
        log.error("Data access API misuse", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail onIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
