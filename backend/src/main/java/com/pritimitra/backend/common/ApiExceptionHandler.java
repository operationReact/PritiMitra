package com.pritimitra.backend.common;

import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {

	@ExceptionHandler(ApiException.class)
	ProblemDetail handleApiException(ApiException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.getStatus(), ex.getMessage());
		problem.setProperty("code", ex.getCode());
		return problem;
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
		Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
				.collect(Collectors.toMap(e -> e.getField(), e -> e.getDefaultMessage(), (a, b) -> a));
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
		problem.setProperty("code", "VALIDATION_FAILED");
		problem.setProperty("fields", fields);
		return problem;
	}

	/** Unknown enum codes and malformed JSON land here, so clients get a stable code rather than a generic 400. */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request body is not valid");
		problem.setProperty("code", "INVALID_REQUEST");
		return problem;
	}
}
