package cringe.baza.presentation.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MemeRequest(
        @JsonProperty("description") String description
) {}