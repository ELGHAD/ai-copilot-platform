package com.alten.chat_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RagResponse {

    /** Réponse générée par le LLM */
    private String answer;

    /** Python retourne "sources" ou "source_documents" selon ta config FastAPI */
    @JsonProperty("sources")          // ← adapter si ton endpoint retourne autre chose
    private List<SourceReference> sources;

    /** Python retourne "confidence_score" (snake_case) */
    @JsonProperty("confidence_score")
    private Double confidenceScore;
}