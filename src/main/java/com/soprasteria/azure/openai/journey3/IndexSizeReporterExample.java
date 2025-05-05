package com.soprasteria.azure.openai.journey3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.search.documents.indexes.SearchIndexClient;
import com.azure.search.documents.indexes.SearchIndexClientBuilder;
import com.azure.search.documents.indexes.models.SearchIndexStatistics;
import com.soprasteria.azure.openai.journey3.utils.Scenarios;

public class IndexSizeReporterExample {

    private static final String searchEndpoint = "https://aisearch-fastai-rag.search.windows.net";
    private static final String searchApiKey = "<api-key>";
    private static final String searchIndexPrefix = "compression-test";

    public static void main(String[] args) throws Exception {
        final var scenarios = Scenarios.defineTestScenarios();
        getIndexSizes(
            searchEndpoint,
            searchApiKey,
            searchIndexPrefix,
            scenarios,
            100
        );
    }

    private static double bytesToMB(long bytes) {
        return Math.round((bytes / (1024.0 * 1024.0)) * 10000.0) / 10000.0;
    }

    /**
     * Get and print storage sizes for all indexes, with retry logic for eventual consistency.
     */
    public static void getIndexSizes(
        String endpoint,
        String credential,
        String indexPrefix,
        List<Map<String, Object>> scenarios,
        int retryAttempts
    ) {
        SearchIndexClient client = new SearchIndexClientBuilder()
            .endpoint(endpoint)
            .credential(new AzureKeyCredential(credential))
            .buildClient();

        System.out.println("\nGathering index statistics...");
        System.out.println("Note: There may be delays in finding index statistics after document upload");
        System.out.println("Index statistics is not a real-time API\n");

        List<Map<String, Object>> indexData = new ArrayList<>();

        for (Map<String, Object> scenario : scenarios) {
            String scenarioName = scenario.get("name").toString();
            String indexName = indexPrefix + "-" + scenarioName;

            for (int attempt = 0; attempt < retryAttempts; attempt++) {
                try {
                    SearchIndexStatistics stats = client.getIndexStatistics(indexName);
                    double storageSize = bytesToMB(stats.getStorageSize());
                    double vectorSize = bytesToMB(stats.getVectorIndexSize());
                    double totalSize = storageSize + vectorSize;

                    Map<String, Object> entry = new HashMap<>();
                    entry.put("Index Name", indexName);
                    entry.put("Scenario", scenarioName);
                    entry.put("Storage Size (MB)", storageSize);
                    entry.put("Vector Size (MB)", vectorSize);
                    entry.put("Total Size (MB)", totalSize);
                    indexData.add(entry);
                    break;
                } catch (Exception e) {
                    if (attempt == retryAttempts - 1) {
                        System.out.printf("Failed to get statistics for %s after %d attempts: %s\n",
                            indexName, retryAttempts, e.getMessage());
                    } else {
                        System.out.printf("Retry %d/%d for %s\n", attempt + 1, retryAttempts, indexName);
                        try {
                            Thread.sleep((long) Math.pow(2, attempt) * 1000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        }

        Optional<Map<String, Object>> baselineOpt = indexData.stream()
            .filter(e -> "baseline".equals(e.get("Scenario")))
            .findFirst();

        if (baselineOpt.isEmpty()) {
            System.out.println("Baseline scenario not found.");
            return;
        }

        double baselineStorage = (double) baselineOpt.get().get("Storage Size (MB)");
        double baselineVector = (double) baselineOpt.get().get("Vector Size (MB)");

        for (Map<String, Object> entry : indexData) {
            double storage = (double) entry.get("Storage Size (MB)");
            double vector = (double) entry.get("Vector Size (MB)");

            double storageReduction = ((baselineStorage - storage) / baselineStorage) * 100;
            double vectorReduction = ((baselineVector - vector) / baselineVector) * 100;

            entry.put("Storage Reduction (%)", String.format("%.2f", storageReduction));
            entry.put("Vector Reduction (%)", String.format("%.2f", vectorReduction));
        }

        indexData.sort((a, b) -> Double.compare((double) b.get("Total Size (MB)"), (double) a.get("Total Size (MB)")));

        String format = "| %-25s | %-10s | %18s | %21s | %18s | %21s |%n";
        System.out.format(format,
            "Index Name", "Scenario", "Storage Size (MB)", "Storage Reduction (%)",
            "Vector Size (MB)", "Vector Reduction (%)");
        System.out.println("-".repeat(125));

        for (Map<String, Object> entry : indexData) {
            System.out.format(format,
                entry.get("Index Name"),
                entry.get("Scenario"),
                String.format("%.4f", entry.get("Storage Size (MB)")),
                entry.get("Storage Reduction (%)"),
                String.format("%.4f", entry.get("Vector Size (MB)")),
                entry.get("Vector Reduction (%)"));
        }
    }
}
