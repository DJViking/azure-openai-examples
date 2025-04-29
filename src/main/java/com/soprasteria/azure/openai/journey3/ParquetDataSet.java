package com.soprasteria.azure.openai.journey3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.apache.arrow.memory.RootAllocator;
import org.apache.arrow.vector.Float4Vector;
import org.apache.arrow.vector.VarCharVector;
import org.apache.arrow.vector.VectorSchemaRoot;
import org.apache.arrow.vector.complex.FixedSizeListVector;
import org.apache.arrow.vector.types.FloatingPointPrecision;
import org.apache.arrow.vector.types.pojo.ArrowType;
import org.apache.arrow.vector.types.pojo.Field;
import org.apache.arrow.vector.types.pojo.FieldType;
import org.apache.arrow.vector.types.pojo.Schema;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.example.GroupReadSupport;
import org.apache.parquet.hadoop.example.GroupWriteSupport;
import org.apache.parquet.hadoop.metadata.CompressionCodecName;
import org.apache.parquet.schema.MessageTypeParser;

/**
 * RAG Journey 3 - Step 6. Define Test Scenarios.
 */
public class ParquetDataSet {

    private final String parquetFileName = "dbpedia_100k.parquet";

    public static void main(String[] args) throws Exception {
        ParquetDataSet parquetDataSet = new ParquetDataSet();
        parquetDataSet.createDataSet();
        parquetDataSet.readDataSet();
    }

    public void createDataSet() throws Exception {
        final var outputParquet = parquetFileName;
        final var dimension = 3072;
        final var numRows = 100_000;

        // Step 1: Generate mock dataset
        System.out.println("Generating dataset...");
        final var dataset = generateSampleData();

        // Step 2: Define Arrow Schema
        final var allocator = new RootAllocator();
        final var schema = new Schema(List.of(
            new Field("_id", FieldType.notNullable(new ArrowType.Utf8()), null),
            new Field("title", FieldType.notNullable(new ArrowType.Utf8()), null),
            new Field("text", FieldType.notNullable(new ArrowType.Utf8()), null),
            new Field("embedding",
                FieldType.notNullable(new ArrowType.FixedSizeList(dimension)),
                List.of(new Field("item", FieldType.notNullable(new ArrowType.FloatingPoint(FloatingPointPrecision.SINGLE)), null))
            )
        ));

        final var root = VectorSchemaRoot.create(schema, allocator);
        root.allocateNew();

        final var idVector = (VarCharVector) root.getVector("_id");
        final var titleVector = (VarCharVector) root.getVector("title");
        final var textVector = (VarCharVector) root.getVector("text");
        final var embeddingVector = (FixedSizeListVector) root.getVector("embedding");
        final var floatVector = (Float4Vector) embeddingVector.getDataVector();

        // Step 3: Fill Arrow vectors
        System.out.println("Populating Arrow Vectors...");
        for (int row = 0; row < dataset.size(); row++) {
            final var entry = dataset.get(row);

            idVector.setSafe(row, entry.get("_id").toString().getBytes(StandardCharsets.UTF_8));
            titleVector.setSafe(row, entry.get("title").toString().getBytes(StandardCharsets.UTF_8));
            textVector.setSafe(row, entry.get("text").toString().getBytes(StandardCharsets.UTF_8));

            embeddingVector.setNotNull(row);
            final var embedding = (float[]) entry.get("embedding");
            for (int i = 0; i < embedding.length; i++) {
                floatVector.setSafe(row * dimension + i, embedding[i]);
            }
        }

        root.setRowCount(numRows);

        // Step 4: Build Parquet Schema
        final var parquetSchemaString = """
            message dbpedia_dataset {
              required binary id (UTF8);
              required binary title (UTF8);
              required binary text (UTF8);
              repeated float embedding;
            }
            """;
        final var parquetSchema = MessageTypeParser.parseMessageType(parquetSchemaString);

        // Step 5: Write to Parquet
        System.out.println("Writing Parquet file...");
        final var configuration = new Configuration();
        GroupWriteSupport.setSchema(parquetSchema, configuration);

        try (final var writer = new ParquetWriter<>(
            new Path(outputParquet),
            new GroupWriteSupport(),
            CompressionCodecName.SNAPPY,
            ParquetWriter.DEFAULT_BLOCK_SIZE,
            ParquetWriter.DEFAULT_PAGE_SIZE,
            ParquetWriter.DEFAULT_PAGE_SIZE,
            true,
            false,
            ParquetWriter.DEFAULT_WRITER_VERSION,
            configuration)
        ) {

            final var groupFactory = new SimpleGroupFactory(parquetSchema);

            for (int row = 0; row < root.getRowCount(); row++) {
                final var group = groupFactory.newGroup()
                    .append("id", idVector.getObject(row).toString())
                    .append("title", titleVector.getObject(row).toString())
                    .append("text", textVector.getObject(row).toString());

                for (int i = 0; i < dimension; i++) {
                    group.append("embedding", floatVector.get(row * dimension + i));
                }
                writer.write(group);
            }
        }

        root.close();
        allocator.close();

        System.out.println("✅ Done! Parquet file written: " + outputParquet);
    }

    private List<Map<String, Object>> generateSampleData() {
        final var rand = new Random();
        final var data = new ArrayList<Map<String, Object>>(100000);

        for (int i = 0; i < 100000; i++) {
            final var entry = new HashMap<String, Object>();
            entry.put("_id", UUID.randomUUID().toString());
            entry.put("title", "Title " + i);
            entry.put("text", "This is the content of document " + i);

            final var embedding = new float[3072];
            for (int j = 0; j < 3072; j++) {
                embedding[j] = rand.nextFloat();
            }
            entry.put("embedding", embedding);

            data.add(entry);
        }
        return data;
    }

    public void readDataSet() throws Exception {
        System.out.println("Reading data from " + parquetFileName);
        final var path = new Path(parquetFileName);
        final var configuration = new Configuration();
        final var reader = ParquetReader.builder(new GroupReadSupport(), path)
            .withConf(configuration)
            .build();

        final var titles = new ArrayList<String>();
        final var texts = new ArrayList<String>();
        final var embeddings = new ArrayList<float[]>();

        int rowCount = 0;
        Group group;
        Group firstDoc = null;

        while ((group = reader.read()) != null) {
            if (firstDoc == null) {
                firstDoc = group; // Save the first document separately
            }

            final var title = group.getBinary("title", 0).toStringUsingUTF8();
            final var text = group.getBinary("text", 0).toStringUsingUTF8();

            titles.add(title);
            texts.add(text);

            final var embeddingSize = group.getFieldRepetitionCount("embedding");
            final var embedding = new float[embeddingSize];
            for (int i = 0; i < embeddingSize; i++) {
                embedding[i] = group.getFloat("embedding", i);
            }
            embeddings.add(embedding);

            rowCount++;
        }

        reader.close();

        // Print dataset info
        System.out.println("\nDataset Information:");
        System.out.println("Total number of rows: " + rowCount);
        System.out.println("Columns: [id, title, text, embedding]");

        // First document structure
        if (firstDoc != null) {
            final var firstDocData = new java.util.HashMap<String, Object>();
            firstDocData.put("id", firstDoc.getBinary("id", 0).toStringUsingUTF8());
            firstDocData.put("title", firstDoc.getBinary("title", 0).toStringUsingUTF8());
            firstDocData.put("content", firstDoc.getBinary("text", 0).toStringUsingUTF8());

            final var firstEmbedding = new ArrayList<Float>();
            final var embSize = firstDoc.getFieldRepetitionCount("embedding");
            for (int i = 0; i < embSize; i++) {
                firstEmbedding.add(firstDoc.getFloat("embedding", i));
            }
            firstDocData.put("embedding", firstEmbedding);

            System.out.println("\nFirst Document Structure:");
            System.out.println(firstDocData);
        }

        // Stats
        final var avgTitleLength = titles.stream()
            .mapToInt(String::length)
            .average()
            .orElse(0.0);

        final var avgTextLength = texts.stream()
            .mapToInt(String::length)
            .average()
            .orElse(0.0);

        System.out.println("\nData Statistics:");
        System.out.printf("Embedding dimension: %d\n", embeddings.isEmpty() ? 0 : embeddings.get(0).length);
        System.out.printf("Average title length: %.1f characters\n", avgTitleLength);
        System.out.printf("Average text length: %.1f characters\n", avgTextLength);
    }

}
