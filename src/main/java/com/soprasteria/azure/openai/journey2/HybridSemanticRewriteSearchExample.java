package com.soprasteria.azure.openai.journey2;

import java.util.Collections;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.util.Context;
import com.azure.search.documents.SearchClientBuilder;
import com.azure.search.documents.SearchDocument;
import com.azure.search.documents.models.QueryType;
import com.azure.search.documents.models.SemanticSearchOptions;
import com.azure.search.documents.models.VectorSearchOptions;
import com.azure.search.documents.models.VectorizableTextQuery;
import com.azure.search.documents.models.SearchOptions;
import org.slf4j.LoggerFactory;

public class HybridSemanticRewriteSearchExample {

    private static final String endpoint = "https://aisearch-fastai-rag.search.windows.net";
    private static final String indexName = "sopravector2";
    private static final String apiKey = "api-key";

    private static final String SEARCH_TEXT = "Hvor mange feriedager får jeg?";

    public static void main(String[] args) {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.DEBUG);

        final var searchClient = new SearchClientBuilder()
            .endpoint(endpoint)
            .indexName(indexName)
            .credential(new AzureKeyCredential(apiKey))
            .buildClient();

        final var vectorQuery = new VectorizableTextQuery(SEARCH_TEXT)
            .setFields("text_vector")
            .setKNearestNeighborsCount(50);

        final var vectorSearchOptions = new VectorSearchOptions()
            .setQueries(Collections.singletonList(vectorQuery));

        // Java SDK does not support setting query rewrites
        final var semanticOptions = new SemanticSearchOptions()
            //.setQueryRewrites(Collections.singletonList(QueryRewrites.GENERATIVE))
            //.setQueryLanguage(QueryLanguage.EN)
            .setSemanticConfigurationName("sopravector2-semantic-configuration");

        final var searchOptions = new SearchOptions()
            .setTop(5)
            .setSelect("title", "chunk")
            .setQueryType(QueryType.SEMANTIC)
            .setVectorSearchOptions(vectorSearchOptions)
            .setSemanticSearchOptions(semanticOptions);

        final var searchResults = searchClient.search(
            SEARCH_TEXT,
            searchOptions,
            Context.NONE
        );

        searchResults.forEach(result -> {
            final var document = result.getDocument(SearchDocument.class);
            final var title = (String) document.get("title");
            var chunk = (String) document.get("chunk");
            if (chunk != null && chunk.length() > 300) {
                chunk = chunk.substring(0, 300) + "...";
            }

            System.out.println("Title: " + title);
            System.out.println("Chunk: " + chunk);
            System.out.println("Score: " + result.getScore());
            System.out.println("-----------------------------------");
        });
    }

}
