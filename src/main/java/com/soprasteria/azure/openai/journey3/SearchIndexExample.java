package com.soprasteria.azure.openai.journey3;

import java.util.List;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.indexes.SearchIndexClientBuilder;
import com.azure.search.documents.indexes.models.BinaryQuantizationCompression;
import com.azure.search.documents.indexes.models.HnswAlgorithmConfiguration;
import com.azure.search.documents.indexes.models.HnswParameters;
import com.azure.search.documents.indexes.models.SearchField;
import com.azure.search.documents.indexes.models.SearchFieldDataType;
import com.azure.search.documents.indexes.models.SearchIndex;
import com.azure.search.documents.indexes.models.VectorSearch;
import com.azure.search.documents.indexes.models.VectorSearchAlgorithmMetric;
import com.azure.search.documents.indexes.models.VectorSearchProfile;

/**
 * RAG Journey 3 - Step 5. Example Search Index Configuration.
 */
public class SearchIndexExample {

    private static final String searchEndpoint = "https://aisearch-fastai-rag.search.windows.net";
    private static final String searchApiKey = "<api-key>";

    public static void main(String[] args) {
        final var searchIndexClient = new SearchIndexClientBuilder()
            .endpoint(searchEndpoint)
            .credential(new AzureKeyCredential(searchApiKey))
            .buildClient();

        final var fields = List.of(
            new SearchField("id", SearchFieldDataType.STRING)
                .setKey(true)
                .setFilterable(true),
            new SearchField("embedding", SearchFieldDataType.collection(SearchFieldDataType.SINGLE))
                .setSearchable(true)
                .setVectorSearchDimensions(3)
                .setVectorSearchProfileName("embedding_profile") // vector profile
                .setHidden(true) // equivalent to stored=False in Python
        );

        final var compression = new BinaryQuantizationCompression("binary_compression");
        /* Rescoring not supported in Java
            .setRescoringOptions(new RescoringOptions()
                .setEnableRescoring(false)
                .setRescoreStorageMethod(VectorSearchCompressionRescoreStorageMethod.DISCARD_ORIGINALS)
            );
         */

        final var algorithmConfig = new HnswAlgorithmConfiguration("hnsw_config")
            .setParameters(new HnswParameters()
                .setMetric(VectorSearchAlgorithmMetric.COSINE)
            );

        final var vectorSearch = new VectorSearch()
            .setAlgorithms(List.of(algorithmConfig))
            .setProfiles(List.of(
                new VectorSearchProfile("embedding_profile", "hnsw_config")
                    .setCompressionName("binary_compression")
            ))
            .setCompressions(List.of(compression));

        final var index = new SearchIndex("tinyindex")
            .setFields(fields)
            .setVectorSearch(vectorSearch);

        searchIndexClient.createIndex(index);
    }

}
