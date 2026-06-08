package com.esprit.threadservice.feign;

import com.esprit.threadservice.dto.CategoryDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "category-service")
public interface CategoryFeignClient {

    @GetMapping("/api/categories/{id}")
    CategoryDto getCategory(@PathVariable Long id);
}
