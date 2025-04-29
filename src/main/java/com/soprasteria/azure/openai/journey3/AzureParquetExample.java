package com.soprasteria.azure.openai.journey3;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.SearchClientBuilder;
import com.azure.search.documents.indexes.models.IndexDocumentsBatch;
import com.azure.search.documents.models.IndexAction;
import com.azure.search.documents.models.IndexActionType;
import com.azure.search.documents.models.IndexDocumentsResult;
import com.azure.search.documents.models.IndexingResult;
import com.soprasteria.azure.openai.journey3.utils.Utils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.example.GroupReadSupport;

/**
 * RAG Journey 3 - Step 7. Execute Index Creation.
 */
public class AzureParquetExample {

    private static final String searchEndpoint = "https://aisearch-fastai-rag.search.windows.net";
    private static final String searchApiKey = "bOPPOpP7hSAA59vcW1JUMRoL5r3jCh0yUMiiU4SE5cAzSeCDhpqp";
    private static final String searchIndexPrefix = "dbpediaindex";
    private static final String parquetFile = "dbpedia_100k.parquet";

    private static final ParquetDataSet parquetDataSet = new ParquetDataSet();

    public static void main(String[] args) throws Exception {
        final var manager = new AzureSearchIndexManager(
            searchEndpoint,
            searchApiKey,
            "demoindex",
            3072
        );

        manager.createIndexes();
        final var scenarios = manager.getScenarios();

        parquetDataSet.createDataSet();
        parquetDataSet.readDataSet();

        final var documents = prepareDocuments(parquetFile);

        uploadToAllIndexes(
            documents,
            scenarios,
            100
        );
    }

    /**
     * Convert Arrow table to list of documents with base64 encoded IDs.
     */
    public static List<Map<String, Object>> prepareDocuments(final String parquetFile) throws Exception {
        System.out.println("Reading Parquet file...");
        final var path = new Path(parquetFile);
        final var configuration = new Configuration();
        final var reader = ParquetReader.builder(new GroupReadSupport(), path)
            .withConf(configuration)
            .build();

        final var documents = new ArrayList<Map<String, Object>>();
        Group group;
        int row = 0;

        while ((group = reader.read()) != null) {
            final var originalId = group.getBinary("id", 0).toStringUsingUTF8();
            final var encodedId = Utils.encodeKey(originalId);

            final var doc = new HashMap<String, Object>();
            doc.put("id", encodedId);
            doc.put("title", group.getBinary("title", 0).toStringUsingUTF8());
            doc.put("content", group.getBinary("text", 0).toStringUsingUTF8());

            final var embeddingSize = group.getFieldRepetitionCount("embedding");
            final var embedding = new ArrayList<Float>(embeddingSize);
            for (int i = 0; i < embeddingSize; i++) {
                embedding.add(group.getFloat("embedding", i));
            }
            doc.put("embedding", embedding);

            documents.add(doc);

            if (row % 1000 == 0) {
                System.out.printf("Processed %d documents...\n", row);
            }
            row++;
        }

        reader.close();

        System.out.println("Document preparation complete. Total documents: " + documents.size());

        // Print sample document
        final var sampleDoc = documents.getFirst();
        System.out.println("\nSample Document:");
        System.out.println(sampleDoc);

        return documents;
    }

    public static void uploadToAllIndexes(
        List<Map<String, Object>> documents,
        List<Map<String, Object>> scenarios,
        int batchSize
    ) throws Exception {
        for (int i = 0; i < scenarios.size(); i++) {
            final var scenario = scenarios.get(i);
            final var searchIndexName = searchIndexPrefix + "-" + scenario.get("name");
            System.out.printf("\nUploading to index (%d/%d): %s\n", i + 1, scenarios.size(), searchIndexName);

            uploadToSearch(documents, searchIndexName, batchSize);
        }
    }

    public static void uploadToSearch(
        final List<Map<String, Object>> documents,
        final String indexName,
        final int batchSize
    ) throws Exception {
        final var searchClient = new SearchClientBuilder()
            .endpoint(searchEndpoint)
            .credential(new AzureKeyCredential(searchApiKey))
            .indexName(indexName)
            .buildClient();

        final var totalDocs = documents.size();
        final var batches = Utils.chunkList(documents, batchSize);
        final var totalBatches = batches.size();

        int successfulDocs = 0;
        int failedDocs = 0;
        final var startTime = Instant.now();

        System.out.printf("\nUploading to index: %s\n", indexName);
        System.out.printf("Total documents: %d\n", totalDocs);

        for (int batchNum = 0; batchNum < totalBatches; batchNum++) {
            final var batch = batches.get(batchNum);

            int retryCount = 0;
            final int maxRetries = 3;

            while (retryCount < maxRetries) {
                try {
                    final var actions = batch.stream()
                        .map(doc -> new IndexAction<Map<String, Object>>()
                            .setActionType(IndexActionType.UPLOAD)
                            .setDocument(doc))
                        .toList();

                    final var batchUpload = new IndexDocumentsBatch<Map<String, Object>>();
                    batchUpload.addActions(actions);

                    final IndexDocumentsResult result = searchClient.indexDocuments(batchUpload);


                    for (IndexingResult r : result.getResults()) {
                        if (r.isSucceeded()) {
                            successfulDocs++;
                        } else {
                            failedDocs++;
                            System.out.printf("Failed to upload document: %s\n", r.getKey());
                        }
                    }

                    final var elapsed = Duration.between(startTime, Instant.now());
                    System.out.printf("Batch %d/%d processed. Uploaded: %d/%d documents. Elapsed: %ds\n",
                        batchNum + 1, totalBatches, successfulDocs, totalDocs, elapsed.toSeconds());

                    Thread.sleep(250); // Pause between batches
                    break;

                } catch (Exception e) {
                    retryCount++;
                    if (retryCount >= maxRetries) {
                        System.out.println("Batch failed after retries: " + e.getMessage());
                        failedDocs += batch.size();
                    } else {
                        System.out.printf("Retrying batch (%d/%d) after error: %s\n", retryCount, maxRetries, e.getMessage());
                        Thread.sleep(2000L * retryCount); // Exponential backoff
                    }
                }
            }
        }

        final var totalTime = Duration.between(startTime, Instant.now());
        System.out.println("\nUpload finished:");
        System.out.printf("Successfully uploaded: %d documents\n", successfulDocs);
        System.out.printf("Failed documents: %d\n", failedDocs);
        System.out.printf("Total time: %ds\n", totalTime.toSeconds());
    }

}
