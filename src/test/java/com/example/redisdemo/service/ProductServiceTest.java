package com.example.redisdemo.service;

import com.example.redisdemo.config.RabbitConfig;
import com.example.redisdemo.event.ProductEvent;
import com.example.redisdemo.model.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private ProductService productService;

    @BeforeEach
    void setUp() {
        // Constructor ของ ProductService seed ข้อมูล 3 ตัวอยู่แล้ว
    }

    @Test
    void shouldReturnSeededProducts() {
        List<Product> products = productService.getAll();

        assertThat(products).hasSize(3);
    }

    @Test
    void shouldReturnProductById() {
        Product product = productService.getById(1L);

        assertThat(product.getName()).isEqualTo("Keyboard");
    }

    @Test
    void shouldThrowWhenProductNotFound() {
        assertThatThrownBy(() -> productService.getById(99L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Product not found: 99");
    }

    @Test
    void shouldCreateProductAndPublishEvent() {
        Product request = new Product(null, "Headphone", new BigDecimal("99.99"));

        Product created = productService.create(request);

        assertThat(created.getId()).isEqualTo(4L);
        assertThat(created.getName()).isEqualTo("Headphone");

        ArgumentCaptor<ProductEvent> captor = ArgumentCaptor.forClass(ProductEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.PRODUCT_EXCHANGE),
                eq(RabbitConfig.PRODUCT_ROUTING_KEY),
                captor.capture()
        );

        ProductEvent event = captor.getValue();
        assertThat(event.getAction()).isEqualTo("CREATE");
        assertThat(event.getId()).isEqualTo(4L);
        assertThat(event.getName()).isEqualTo("Headphone");
    }

    @Test
    void shouldUpdateProductAndPublishEvent() {
        Product request = new Product(null, "Gaming Mouse", new BigDecimal("49.99"));

        Product updated = productService.update(2L, request);

        assertThat(updated.getName()).isEqualTo("Gaming Mouse");
        assertThat(updated.getPrice()).isEqualTo(new BigDecimal("49.99"));

        ArgumentCaptor<ProductEvent> captor = ArgumentCaptor.forClass(ProductEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.PRODUCT_EXCHANGE),
                eq(RabbitConfig.PRODUCT_ROUTING_KEY),
                captor.capture()
        );

        ProductEvent event = captor.getValue();
        assertThat(event.getAction()).isEqualTo("UPDATE");
        assertThat(event.getId()).isEqualTo(2L);
    }

    @Test
    void shouldThrowWhenUpdatingNonExistingProduct() {
        Product request = new Product(null, "Headphone", new BigDecimal("99.99"));

        assertThatThrownBy(() -> productService.update(99L, request))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Product not found: 99");
    }

    @Test
    void shouldDeleteProductAndPublishEvent() {
        productService.delete(1L);

        ArgumentCaptor<ProductEvent> captor = ArgumentCaptor.forClass(ProductEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitConfig.PRODUCT_EXCHANGE),
                eq(RabbitConfig.PRODUCT_ROUTING_KEY),
                captor.capture()
        );

        ProductEvent event = captor.getValue();
        assertThat(event.getAction()).isEqualTo("DELETE");
        assertThat(event.getId()).isEqualTo(1L);
    }

    @Test
    void shouldThrowWhenDeletingNonExistingProduct() {
        assertThatThrownBy(() -> productService.delete(99L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Product not found: 99");
    }
}
