package com.soprasteria.azure.openai.journey3.utils;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class Utils {

    public static String encodeKey(String key) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(key.getBytes());
    }

    public static <T> List<List<T>> chunkList(List<T> list, int chunkSize) {
        final var chunks = new ArrayList<List<T>>();
        for (int i = 0; i < list.size(); i += chunkSize) {
            chunks.add(list.subList(i, Math.min(i + chunkSize, list.size())));
        }
        return chunks;
    }

}
