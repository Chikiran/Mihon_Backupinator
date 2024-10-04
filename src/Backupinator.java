import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class Backupinator {

    public static void main(String[] args) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode backup1 = objectMapper.readTree(new File("backup1.json"));
            JsonNode backup2 = objectMapper.readTree(new File("backup2.json"));

            JsonNode mergedBackup = mergeBackups(backup1, backup2);

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File("merged_backup.json"), mergedBackup);
            System.out.println("Merged backup saved as merged_backup.json");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static JsonNode mergeBackups(JsonNode backup1, JsonNode backup2) {
        ObjectMapper objectMapper = new ObjectMapper();
        ObjectNode mergedBackup = objectMapper.createObjectNode();

        Map<String, String> sourceMap = new HashMap<>();
        Map<String, String> categoryMap = new HashMap<>();

        ArrayNode mergedSources = mergeSources(backup1.get("backupSources"), backup2.get("backupSources"), sourceMap);
        ArrayNode mergedCategories = mergeCategories(backup1.get("backupCategories"), backup2.get("backupCategories"), categoryMap);
        mergedBackup.set("backupSources", mergedSources);
        mergedBackup.set("backupCategories", mergedCategories);

        ArrayNode mergedManga = objectMapper.createArrayNode();
        Map<String, JsonNode> mangaMap = new HashMap<>();

        addMangaToMap(backup1.get("backupManga"), mangaMap, sourceMap, categoryMap);
        addMangaToMap(backup2.get("backupManga"), mangaMap, sourceMap, categoryMap);

        for (JsonNode manga : mangaMap.values()) {
            mergedManga.add(manga);
        }

        mergedBackup.set("backupManga", mergedManga);
        mergedBackup.set("backupPreferences", backup1.get("backupPreferences"));

        return mergedBackup;
    }

    private static void addMangaToMap(JsonNode mangaArray, Map<String, JsonNode> mangaMap, Map<String, String> sourceMap, Map<String, String> categoryMap) {
        for (JsonNode manga : mangaArray) {
            String source = sourceMap.get(manga.get("source").asText());
            String title = manga.get("title").asText();
            String key = source + "_" + title;

            ObjectNode mangaCopy = manga.deepCopy();
            mangaCopy.put("source", source);
            if (manga.has("category")) {
                mangaCopy.put("category", categoryMap.get(manga.get("category").asText()));
            }

            if (!mangaMap.containsKey(key)) {
                mangaMap.put(key, mangaCopy);
            } else {
                JsonNode existingManga = mangaMap.get(key);
                mergeChapters(existingManga, mangaCopy);
            }
        }
    }

    private static void mergeChapters(JsonNode existingManga, JsonNode newManga) {
        ArrayNode existingChapters = (ArrayNode) existingManga.get("chapters");
        ArrayNode newChapters = (ArrayNode) newManga.get("chapters");

        if (existingChapters == null && newChapters == null) {
            return;
        }

        if (existingChapters == null) {
            ((ObjectNode) existingManga).set("chapters", newChapters);
            return;
        }

        if (newChapters == null) {
            return;
        }

        Map<Integer, JsonNode> chapterMap = new HashMap<>();

        for (JsonNode chapter : existingChapters) {
            chapterMap.put(chapter.get("chapterNumber").asInt(), chapter);
        }

        for (JsonNode chapter : newChapters) {
            int chapterNumber = chapter.get("chapterNumber").asInt();
            if (!chapterMap.containsKey(chapterNumber)) {
                chapterMap.put(chapterNumber, chapter);
            } else {
                JsonNode existingChapter = chapterMap.get(chapterNumber);
                boolean newRead = chapter.has("read") && chapter.get("read").asBoolean();
                if (newRead) {
                    existingChapter = mergeReadStates(existingChapter, chapter);
                }
                if (chapter.has("bookmark")) {
                    existingChapter = mergeBookmarks(existingChapter, chapter);
                }
                chapterMap.put(chapterNumber, existingChapter);
            }
        }

        ArrayNode mergedChapters = new ObjectMapper().createArrayNode();
        for (JsonNode chapter : chapterMap.values()) {
            mergedChapters.add(chapter);
        }
        ((ObjectNode) existingManga).set("chapters", mergedChapters);
    }

    private static JsonNode mergeReadStates(JsonNode existingChapter, JsonNode newChapter) {
        ((ObjectNode) existingChapter).put("read", true);
        return existingChapter;
    }

    private static JsonNode mergeBookmarks(JsonNode existingChapter, JsonNode newChapter) {
        if (newChapter.has("bookmark")) {
            ((ObjectNode) existingChapter).put("bookmark", true);
        }
        return existingChapter;
    }

    private static ArrayNode mergeSources(JsonNode sources1, JsonNode sources2, Map<String, String> sourceMap) {
        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode mergedSources = objectMapper.createArrayNode();

        int sourceIndex = 0;
        for (JsonNode source : sources1) {
            sourceMap.put(String.valueOf(sourceIndex), source.asText());
            mergedSources.add(source);
            sourceIndex++;
        }

        for (JsonNode source : sources2) {
            if (!sourceMap.containsValue(source.asText())) {
                sourceMap.put(String.valueOf(sourceIndex), source.asText());
                mergedSources.add(source);
                sourceIndex++;
            }
        }

        return mergedSources;
    }

    private static ArrayNode mergeCategories(JsonNode categories1, JsonNode categories2, Map<String, String> categoryMap) {
        ObjectMapper objectMapper = new ObjectMapper();
        ArrayNode mergedCategories = objectMapper.createArrayNode();

        int categoryIndex = 0;
        for (JsonNode category : categories1) {
            categoryMap.put(String.valueOf(categoryIndex), category.asText());
            mergedCategories.add(category);
            categoryIndex++;
        }

        for (JsonNode category : categories2) {
            if (!categoryMap.containsValue(category.asText())) {
                categoryMap.put(String.valueOf(categoryIndex), category.asText());
                mergedCategories.add(category);
                categoryIndex++;
            }
        }

        return mergedCategories;
    }
}
