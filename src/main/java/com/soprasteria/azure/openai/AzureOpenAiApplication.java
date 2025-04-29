package com.soprasteria.azure.openai;

import java.util.List;
import java.util.Scanner;

import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatRequestSystemMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.util.Context;
import com.azure.search.documents.SearchClientBuilder;
import com.azure.search.documents.SearchDocument;
import com.azure.search.documents.models.SearchOptions;
import com.azure.search.documents.models.SearchResult;
import com.azure.search.documents.models.VectorSearchOptions;
import com.azure.search.documents.models.VectorizableTextQuery;
import com.azure.search.documents.util.SearchPagedIterable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AzureOpenAiApplication {

    @Value("${azure.openai.endpoint}")
    private String openaiEndpoint;

    @Value("${azure.openai.api-key}")
    private String openaiApiKey;

    @Value("${azure.openai.chat-model}")
    private String chatModel;

    @Value("${azure.search.endpoint}")
    private String searchEndpoint;

    @Value("${azure.search.index-name}")
    private String searchIndex;

    @Value("${azure.search.api-key}")
    private String searchApiKey;

    public static void main(String[] args) {
        SpringApplication.run(AzureOpenAiApplication.class, args);
    }

    @Bean
    public CommandLineRunner run() {
        return args -> {
            final var scanner = new Scanner(System.in);
            System.out.println("Skriv inn spm og trykk ENTER");
            final var userQuestion = scanner.nextLine();

            final var searchClient = new SearchClientBuilder()
                .endpoint(searchEndpoint)
                .credential(new AzureKeyCredential(searchApiKey))
                .indexName(searchIndex)
                .buildClient();

            final var openAIClient = new OpenAIClientBuilder()
                .endpoint(openaiEndpoint)
                .credential(new AzureKeyCredential(openaiApiKey))
                .buildClient();

            final var vectorSearchOptions = new VectorSearchOptions()
                .setQueries(List.of(
                    new VectorizableTextQuery(userQuestion).setFields("text_vector")
                ));

            final var searchOptions = new SearchOptions().setVectorSearchOptions(vectorSearchOptions);
            final var searchResults = searchClient.search(userQuestion, searchOptions, Context.NONE);

            final var context = getContext(searchResults);

            final var systemPrompt = String.format("""
                Du er en AI Assistent som har innsikt i Sopra Steria sin personalh\u00e5ndbok vedlagt som context.
                V\u00e6r kortfattet og detaljert i svarene dine, bruk kun informasjon fra fra personalh\u00e5ndboken.
                
                Context:
                %s
                """, context);

            final var messages = List.of(
                new ChatRequestSystemMessage(systemPrompt),
                new ChatRequestUserMessage(userQuestion)
            );

            final var options = new ChatCompletionsOptions(messages).setTemperature(0.7d);

            System.out.println("Svarer...");
            openAIClient.getChatCompletions(chatModel, options)
                .getChoices()
                .forEach(choice -> System.out.println(choice.getMessage().getContent()));
        };
    }

    private String getContext(SearchPagedIterable results) {
        final var contextBuilder = new StringBuilder();
        for (SearchResult result : results) {
            final var document = result.getDocument(SearchDocument.class);
            final var chunkObj = document.get("chunk");
            if (chunkObj instanceof String chunk) {
                contextBuilder.append(chunk).append("\n");
            }
        }
        return contextBuilder.toString();
    }

}
