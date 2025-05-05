package com.soprasteria.azure.openai.journey3;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.SearchIndexClientBuilder;
import com.azure.search.documents.indexes.models.*;
import com.soprasteria.azure.openai.journey3.utils.Scenarios;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AzureSearchIndexManager {

    private final SearchIndexClient searchIndexClient;
    private final String indexNamePrefix;
    private final int vectorDimensions;

    private List<Map<String, Object>> scenarios;

    public AzureSearchIndexManager(String serviceEndpoint, String credential, String indexNamePrefix, int vectorDimensions) {
        this.searchIndexClient = new SearchIndexClientBuilder()
            .endpoint(serviceEndpoint)
            .credential(new AzureKeyCredential(credential))
            .buildClient();
        this.indexNamePrefix = indexNamePrefix;
        this.vectorDimensions = vectorDimensions;
    }

    public void createIndexes() {
        final var scenarios = Scenarios.defineTestScenarios();

        final var createdIndexes = new ArrayList<Map<String, String>>();
        for (final var scenario : scenarios) {
            try {
                final var indexName = createIndex(scenario);
                final var result = new HashMap<String, String>();
                result.put("index_name", indexName);
                result.put("configuration", (String) scenario.get("description"));
                createdIndexes.add(result);
            } catch (Exception e) {
                System.out.println("Error creating index for scenario " + scenario.get("name") + ": " + e.getMessage());
            }
        }

        // Print created indexes
        if (!createdIndexes.isEmpty()) {
            System.out.println("\nCreated Indexes:");
            for (final var entry : createdIndexes) {
                System.out.println(entry);
            }
        } else {
            System.out.println("\nNo indexes were created successfully.");
        }
    }

    private List<SearchField> createBaseFields(boolean storedEmbedding) {
        final var fields = new ArrayList<SearchField>();
        fields.add(new SearchField("id", SearchFieldDataType.STRING)
            .setKey(true)
            .setFilterable(true));
        fields.add(new SearchField("title", SearchFieldDataType.STRING)
            .setSearchable(true));
        fields.add(new SearchField("content", SearchFieldDataType.STRING)
            .setSearchable(true));
        fields.add(new SearchField("embedding", SearchFieldDataType.collection(SearchFieldDataType.SINGLE))
            .setSearchable(true)
            .setVectorSearchDimensions(vectorDimensions)
            .setVectorSearchProfileName("default-profile")
            .setHidden(!storedEmbedding));
        return fields;
    }

    private VectorSearchCompression createCompressionConfig(
        final String configType,
        final Integer truncateDims,
        final boolean discardOriginals
    ) {
        final var compressionName = configType + "-compression";

        // Enable rescoring only if originals are preserved
        final boolean enableRescoring = !discardOriginals;

        // Java SDK does not support setting rescoring, or truncation dimension
        if ("scalar".equals(configType)) {
            return new ScalarQuantizationCompression(compressionName)
                .setParameters(new ScalarQuantizationParameters()
                    .setQuantizedDataType(VectorSearchCompressionTarget.INT8));
        } else if ("binary".equals(configType)) {
            return new BinaryQuantizationCompression(compressionName);
        } else {
            return null;
        }
    }

    private VectorSearch createVectorSearchConfig(final VectorSearchCompression compressionConfig) {
        final var algorithmConfig = new HnswAlgorithmConfiguration("hnsw-config")
            .setParameters(new HnswParameters()
                .setM(4)
                .setEfConstruction(400)
                .setEfSearch(500)
                .setMetric(VectorSearchAlgorithmMetric.COSINE));

        final var profiles = new ArrayList<VectorSearchProfile>();
        final var profile = new VectorSearchProfile("default-profile", "hnsw-config");

        if (compressionConfig != null) {
            profile.setCompressionName(compressionConfig.getCompressionName());
        }
        profiles.add(profile);

        final var vectorSearch = new VectorSearch()
            .setAlgorithms(List.of(algorithmConfig))
            .setProfiles(profiles);

        if (compressionConfig != null) {
            vectorSearch.setCompressions(List.of(compressionConfig));
        }
        return vectorSearch;
    }

    public String createIndex(final Map<String, Object> scenario) {
        final var indexName = indexNamePrefix + "-" + scenario.get("name");

        final var storedEmbedding = (boolean) scenario.getOrDefault("stored_embedding", true);

        final var fields = createBaseFields(storedEmbedding);

        VectorSearchCompression compressionConfig = null;
        if (scenario.get("compression_type") != null) {
            compressionConfig = createCompressionConfig(
                (String) scenario.get("compression_type"),
                (Integer) scenario.get("truncate_dims"),
                (boolean) scenario.getOrDefault("discard_originals", false)
            );
        }

        final var vectorSearch = createVectorSearchConfig(compressionConfig);

        final var index = new SearchIndex(indexName)
            .setFields(fields)
            .setVectorSearch(vectorSearch);

        try {
            searchIndexClient.createOrUpdateIndex(index);
            System.out.println("Created or updated index: " + indexName);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("already exists")) {
                System.out.println("Index " + indexName + " already exists.");
            } else {
                System.out.println("Error creating index " + indexName + ": " + e.getClass() + " - " + e.getMessage());
            }
        }

        return indexName;
    }

}
