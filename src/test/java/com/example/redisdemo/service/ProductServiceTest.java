package com.example.redisdemo.service;

import com.example.redisdemo.config.RabbitConfig;
import com.example.redisdemo.event.ProductEvent;
import com.example.redisdemo.model.Product;
import com.example.redisdemo.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private ProductService productService;

    @Test
    void shouldReturnAllProducts() {
        List<Product> products = List.of(
                new Product(1L, "Keyboard", new BigDecimal("59.99")),
                new Product(2L, "Mouse", new BigDecimal("29.99"))
        );
        when(productRepository.findAll()).thenReturn(products);

        List<Product> result = productService.getAll();

        assertThat(result).hasSize(2);
        verify(productRepository).findAll();
    }

    @Test
    void shouldReturnProductById() {
        Product product = new Product(1L, "Keyboard", new BigDecimal("59.99"));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        Product result = productService.getById(1L);

        assertThat(result.getName()).isEqualTo("Keyboard");
        verify(productRepository).findById(1L);
    }

    @Test
    void shouldThrowWhenProductNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getById(99L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Product not found: 99");
    }

    @Test
    void shouldCreateProductAndPublishEvent() {
        Product request = new Product(null, "Headphone", new BigDecimal("99.99"));
        Product saved = new Product(4L, "Headphone", new BigDecimal("99.99"));
        when(productRepository.save(any(Product.class))).thenReturn(saved);

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
        Product existing = new Product(2L, "Mouse", new BigDecimal("29.99"));
        Product request = new Product(null, "Gaming Mouse", new BigDecimal("49.99"));
        Product updated = new Product(2L, "Gaming Mouse", new BigDecimal("49.99"));

        when(productRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(productRepository.save(any(Product.class))).thenReturn(updated);

        Product result = productService.update(2L, request);

        assertThat(result.getName()).isEqualTo("Gaming Mouse");
        assertThat(result.getPrice()).isEqualTo(new BigDecimal("49.99"));

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
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(99L, request))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Product not found: 99");
    }

    @Test
    void shouldDeleteProductAndPublishEvent() {
        Product existing = new Product(1L, "Keyboard", new BigDecimal("59.99"));
        when(productRepository.findById(1L)).thenReturn(Optional.of(existing));

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
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.delete(99L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Product not found: 99");
    }
}
