package com.example.redisdemo.exception;

import com.example.redisdemo.service.ProductNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Mock
    private MethodArgumentNotValidException validationException;

    @Test
    void shouldReturnNotFoundForProductNotFoundException() {
        ProductNotFoundException ex = new ProductNotFoundException(99L);

        ResponseEntity<Map<String, Object>> response = handler.handleProductNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody())
                .containsEntry("status", 404)
                .containsEntry("message", "Product not found: 99");
    }

    @Test
    void shouldReturnBadRequestForValidationError() {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "product");
        bindingResult.addError(new FieldError("product", "name", "Name is required"));
        when(validationException.getBindingResult()).thenReturn(bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidationError(validationException);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .containsEntry("status", 400)
                .containsEntry("message", "name: Name is required");
    }

    @Test
    void shouldReturnInternalServerErrorForGenericException() {
        Exception ex = new RuntimeException("Something went wrong");

        ResponseEntity<Map<String, Object>> response = handler.handleGeneric(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody())
                .containsEntry("status", 500)
                .containsEntry("message", "Internal server error");
    }
}
