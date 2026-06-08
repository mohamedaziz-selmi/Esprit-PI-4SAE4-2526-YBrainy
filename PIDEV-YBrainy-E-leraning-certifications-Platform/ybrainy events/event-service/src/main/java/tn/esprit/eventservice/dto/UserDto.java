package tn.esprit.eventservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Lightweight user representation received from user-service.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserDto(
        @JsonAlias({"userId", "idUser"}) long idUser,
        @JsonAlias({"firstName", "nom"}) String nom,
        @JsonAlias({"lastName", "prenom"}) String prenom,
        String email,
        String role
) {}
