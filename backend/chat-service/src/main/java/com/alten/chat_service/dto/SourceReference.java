package com.alten.chat_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single source document cited in a RAG-generated answer.
 * Displayed in the chat interface below the answer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceReference {

    /** Title of the source document */
    private String documentTitle;

    /** Page number where the relevant chunk was found */
    private Integer page;

    /** Short excerpt from the chunk used to generate the answer */
    private String excerpt;

    /** Similarity score of this chunk — higher means more relevant */
    private Double score;
}