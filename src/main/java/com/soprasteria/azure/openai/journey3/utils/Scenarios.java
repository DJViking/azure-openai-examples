package com.soprasteria.azure.openai.journey3.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Scenarios {

    /**
     * RAG Journey 3 - Step 6. Define Test Scenarios.
     */
    public static List<Map<String, Object>> defineTestScenarios() {
        final var scenarios = new ArrayList<Map<String, Object>>();

        final var baseline = new HashMap<String, Object>();
        baseline.put("name", "baseline");
        baseline.put("compression_type", null);
        baseline.put("truncate_dims", null);
        baseline.put("discard_originals", false);
        baseline.put("stored_embedding", true);
        baseline.put("description", "Baseline configuration without compression");
        scenarios.add(baseline);

        final var baselineS = new HashMap<String, Object>();
        baselineS.put("name", "baseline-s");
        baselineS.put("compression_type", null);
        baselineS.put("truncate_dims", null);
        baselineS.put("discard_originals", false);
        baselineS.put("stored_embedding", false);
        baselineS.put("description", "Baseline configuration without compression, with stored=False");
        scenarios.add(baselineS);

        final var scalarFull = new HashMap<String, Object>();
        scalarFull.put("name", "scalar-full");
        scalarFull.put("compression_type", "scalar");
        scalarFull.put("truncate_dims", null);
        scalarFull.put("discard_originals", false);
        scalarFull.put("stored_embedding", false);
        scalarFull.put("description", "Scalar quantization (int8) with full dimensions, preserved originals");
        scenarios.add(scalarFull);

        final var scalarTruncated1024 = new HashMap<String, Object>();
        scalarTruncated1024.put("name", "scalar-truncated-1024");
        scalarTruncated1024.put("compression_type", "scalar");
        scalarTruncated1024.put("truncate_dims", 1024);
        scalarTruncated1024.put("discard_originals", false);
        scalarTruncated1024.put("stored_embedding", false);
        scalarTruncated1024.put("description", "Scalar quantization (int8) with 1024 dimensions, preserved originals");
        scenarios.add(scalarTruncated1024);

        final var scalarTruncated1024Discard = new HashMap<String, Object>();
        scalarTruncated1024Discard.put("name", "scalar-truncated-1024-discard");
        scalarTruncated1024Discard.put("compression_type", "scalar");
        scalarTruncated1024Discard.put("truncate_dims", 1024);
        scalarTruncated1024Discard.put("discard_originals", true);
        scalarTruncated1024Discard.put("stored_embedding", false);
        scalarTruncated1024Discard.put("description", "Scalar quantization (int8) with 1024 dimensions, discarded originals");
        scenarios.add(scalarTruncated1024Discard);

        final var binaryFull = new HashMap<String, Object>();
        binaryFull.put("name", "binary-full");
        binaryFull.put("compression_type", "binary");
        binaryFull.put("truncate_dims", null);
        binaryFull.put("discard_originals", false);
        binaryFull.put("stored_embedding", false);
        binaryFull.put("description", "Binary quantization with full dimensions, preserved originals");
        scenarios.add(binaryFull);

        final var binaryTruncated1024 = new HashMap<String, Object>();
        binaryTruncated1024.put("name", "binary-truncated-1024");
        binaryTruncated1024.put("compression_type", "binary");
        binaryTruncated1024.put("truncate_dims", 1024);
        binaryTruncated1024.put("discard_originals", false);
        binaryTruncated1024.put("stored_embedding", false);
        binaryTruncated1024.put("description", "Binary quantization with 1024 dimensions, preserved originals");
        scenarios.add(binaryTruncated1024);

        final var binaryTruncated1024Discard = new HashMap<String, Object>();
        binaryTruncated1024Discard.put("name", "binary-truncated-1024-discard");
        binaryTruncated1024Discard.put("compression_type", "binary");
        binaryTruncated1024Discard.put("truncate_dims", 1024);
        binaryTruncated1024Discard.put("discard_originals", true);
        binaryTruncated1024Discard.put("stored_embedding", false);
        binaryTruncated1024Discard.put("description", "Binary quantization with 1024 dimensions, discarded originals");
        scenarios.add(binaryTruncated1024Discard);

        return scenarios;
    }

}
