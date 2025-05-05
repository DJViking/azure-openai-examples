package com.soprasteria.azure.openai.journey3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.EmbeddingsOptions;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.util.Context;
import com.azure.search.documents.SearchClientBuilder;
import com.azure.search.documents.SearchDocument;
import com.azure.search.documents.models.SearchOptions;
import com.azure.search.documents.models.VectorSearchOptions;
import com.azure.search.documents.models.VectorizableTextQuery;
import com.soprasteria.azure.openai.journey3.utils.Scenarios;

public class SearchQualityExample {

    private static final String openaiEndpoint = "https://aoai-fastai-rag.openai.azure.com/";
    private static final String openaiApiKey = "<api-key>";
    private static final String embeddingDeployment = "text-embedding-3-large";

    private static final String searchEndpoint = "https://aisearch-fastai-rag.search.windows.net";
    private static final String searchIndexPrefix = "compression-test";
    private static final String searchApiKey = "<api-key>";


    public static void main(String[] args) throws ExecutionException, InterruptedException {
        // Assume scenarios is a List<Map<String, Object>> (or a POJO list)
        final var scenarios = Scenarios.defineTestScenarios();

        final var openAIClient = new OpenAIClientBuilder()
            .endpoint(openaiEndpoint)
            .credential(new AzureKeyCredential(openaiApiKey))
            .buildClient();

        final var query = "first avian dinosaur in the fossil record";
        final var searchVector = getEmbedding(openAIClient, query);

        final var resultMap = new LinkedHashMap<String, List<String>>();

        for (final var scenario : scenarios) {
            final var name = scenario.get("name").toString();
            final var indexName = searchIndexPrefix + "-" + name;

            final var searchClient = new SearchClientBuilder()
                .endpoint(searchEndpoint)
                .credential(new AzureKeyCredential(searchApiKey))
                .indexName(indexName)
                .buildClient();

            final var vectorSearchOptions = new VectorSearchOptions()
                .setQueries(List.of(
                    new VectorizedQuery(searchVector)
                        .setFields("embedding")
                        .setKNearestNeighborsCount(50)
                ));

            final var searchOptions = new SearchOptions()
                .setTop(5)
                .setSelect("title")
                .setVectorSearchOptions(vectorSearchOptions);
            final var searchResults = searchClient.search(query, searchOptions, Context.NONE);

            final var titles = new ArrayList<String>();
            for (final var result : searchResults) {
                final var document = result.getDocument(SearchDocument.class);
                final var titleObj = document.get("title");
                if (titleObj instanceof String title) {
                    titles.add(title);
                }
            }

            resultMap.put(name, titles);
        }

        // Print table-like output
        printComparisonTable(resultMap, 5);
    }

    private static List<Float> getEmbedding(OpenAIClient client, String input) {
        final var options = new EmbeddingsOptions(List.of(input));
        final var embeddings = client.getEmbeddings(embeddingDeployment, options);
        final var item = embeddings.getData().get(0);
        return item.getEmbedding();
    }

    private static void printComparisonTable(Map<String, List<String>> scenarioToTitles, int rowCount) {
        final var headers = new ArrayList<String>(scenarioToTitles.keySet());
        System.out.println("\n--- Search Results (Top " + rowCount + ") ---");
        System.out.println(String.join(" | ", headers));

        for (int i = 0; i < rowCount; i++) {
            int finalI = i;
            final var row = headers.stream()
                .map(header -> scenarioToTitles.get(header).size() > finalI ? scenarioToTitles.get(header).get(finalI) : "")
                .collect(Collectors.joining(" | "));
            System.out.println(row);
        }
    }

}
