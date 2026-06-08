package com.esprit.categoryservice.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class CategoryRequest {
    private String name;
    private String description;
    private String iconUrl;
}
