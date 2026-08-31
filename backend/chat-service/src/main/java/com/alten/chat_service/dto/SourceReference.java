package com.alten.chat_service.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single source document cited in a RAG-generated answer.
 * Displayed in the chat interface below the answer.
 *
 * This DTO is deserialized FROM the Python RAG service and serialized TO the
 * Angular app, and the two use different field names. The RAG service returns
 * {content, source, page} (cf. rag-service/app/models.py SourceDocument) while
 * the frontend template reads {documentTitle, page, excerpt}.
 *
 * @JsonAlias is what bridges them: it adds accepted names on deserialization
 * only, so the outgoing JSON keeps the names Angular expects. Using
 * @JsonProperty("source") instead would have renamed the outgoing field too and
 * broken the frontend. Before this, only 'page' matched — every source rendered
 * as a bare "Page 1" with no title and no excerpt.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceReference {

    /** Title of the source document — 'source' in the RAG payload */
    @JsonAlias({"source", "document_title"})
    private String documentTitle;

    /** Page number where the relevant chunk was found */
    private Integer page;

    /** Short excerpt from the chunk used to generate the answer — 'content' in the RAG payload */
    @JsonAlias({"content"})
    private String excerpt;

    /** Similarity score of this chunk — higher means more relevant */
    private Double score;
}