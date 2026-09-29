package com.example.redisdemo.controller;

import com.example.redisdemo.model.Product;
import com.example.redisdemo.service.ProductService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductControllerTest {

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductController productController;

    @Test
    void shouldReturnAllProducts() {
        List<Product> products = List.of(
                new Product(1L, "Keyboard", new BigDecimal("59.99")),
                new Product(2L, "Mouse", new BigDecimal("29.99"))
        );
        when(productService.getAll()).thenReturn(products);

        List<Product> result = productController.getAll();

        assertThat(result).hasSize(2);
        verify(productService).getAll();
    }

    @Test
    void shouldReturnProductById() {
        Product product = new Product(1L, "Keyboard", new BigDecimal("59.99"));
        when(productService.getById(1L)).thenReturn(product);

        Product result = productController.getById(1L);

        assertThat(result.getName()).isEqualTo("Keyboard");
        verify(productService).getById(1L);
    }

    @Test
    void shouldCreateProduct() {
        Product request = new Product(null, "Headphone", new BigDecimal("99.99"));
        Product created = new Product(4L, "Headphone", new BigDecimal("99.99"));
        when(productService.create(request)).thenReturn(created);

        Product result = productController.create(request);

        assertThat(result.getId()).isEqualTo(4L);
        verify(productService).create(request);
    }

    @Test
    void shouldUpdateProduct() {
        Product request = new Product(null, "Headphone Pro", new BigDecimal("129.99"));
        Product updated = new Product(1L, "Headphone Pro", new BigDecimal("129.99"));
        when(productService.update(1L, request)).thenReturn(updated);

        Product result = productController.update(1L, request);

        assertThat(result.getName()).isEqualTo("Headphone Pro");
        verify(productService).update(1L, request);
    }

    @Test
    void shouldDeleteProduct() {
        productController.delete(1L);
        verify(productService).delete(1L);
    }
}
