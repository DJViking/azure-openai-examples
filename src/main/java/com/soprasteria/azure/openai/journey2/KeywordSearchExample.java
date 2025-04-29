package com.soprasteria.azure.openai.journey2;

import java.util.ArrayList;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.util.Context;
import com.azure.search.documents.SearchClientBuilder;
import com.azure.search.documents.SearchDocument;
import com.azure.search.documents.models.SearchOptions;
import com.azure.search.documents.models.SearchResult;
import com.azure.search.documents.util.SearchPagedIterable;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class KeywordSearchExample {

    private static final String endpoint = "https://aisearch-fastai-rag.search.windows.net";
    private static final String indexName = "sopravector2";
    private static final String apiKey = "<api-key>";

    private static final String SEARCH_TEXT = "Hvor mange feriedager får jeg?";

    public static void main(String[] args) {
        final var searchClient = new SearchClientBuilder()
            .endpoint(endpoint)
            .indexName(indexName)
            .credential(new AzureKeyCredential(apiKey))
            .buildClient();

        final var searchOptions = new SearchOptions()
            .setTop(5)
            .setSelect("title", "chunk");
        final var searchResults = searchClient.search(SEARCH_TEXT, searchOptions, Context.NONE);

        displayResults(searchResults);
    }

    private static void displayResults(SearchPagedIterable searchResults) {
        final var formattedResults = new ArrayList<ObjectNode>();
        final var mapper = new ObjectMapper();

        for (SearchResult result : searchResults) {
            final var document = result.getDocument(SearchDocument.class);
            final var node = mapper.createObjectNode();

            // Extract selected fields
            final var title = (String) document.get("title");
            var chunk = (String) document.get("chunk");

            // Truncate 'chunk' if necessary
            if (chunk.length() > 300) {
                chunk = chunk.substring(0, 300) + "...";
            }

            // Populate the node
            node.put("title", title);
            node.put("chunk", chunk);
            node.put("@search.score", result.getScore());

            formattedResults.add(node);
        }

        // Print the results nicely
        for (ObjectNode node : formattedResults) {
            System.out.println("---------------------------------------------");
            System.out.println("Title: " + node.get("title").asText());
            System.out.println("Chunk: " + node.get("chunk").asText());
            System.out.println("Score: " + node.get("@search.score").asDouble());
        }
    }

}
