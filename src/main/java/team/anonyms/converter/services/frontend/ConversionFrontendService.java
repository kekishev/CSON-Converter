package team.anonyms.converter.services.frontend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.fasterxml.jackson.dataformat.csv.CsvWriteException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import jakarta.persistence.EntityNotFoundException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import team.anonyms.converter.entities.Modification;
import team.anonyms.converter.entities.Pattern;
import team.anonyms.converter.repositories.ModificationRepository;
import team.anonyms.converter.repositories.PatternRepository;
import team.anonyms.converter.exceptions.IllegalPatternException;
import team.anonyms.converter.exceptions.UnsupportedExtensionException;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/*
TODO: add option of choosing separator for CSV format
 */
@Service
public class ConversionFrontendService {
    private static final Logger logger = LoggerFactory.getLogger(ConversionFrontendService.class);

    private final PatternRepository patternRepository;
    private final ModificationRepository modificationRepository;

    private final JsonMapper jsonMapper;
    private final XmlMapper xmlMapper;
    private final CsvMapper csvMapper;

    public ConversionFrontendService(
            PatternRepository patternRepository,
            ModificationRepository modificationRepository,
            JsonMapper jsonMapper,
            XmlMapper xmlMapper,
            CsvMapper csvMapper
    ) {
        this.patternRepository = patternRepository;
        this.modificationRepository = modificationRepository;

        this.jsonMapper = jsonMapper;
        this.xmlMapper = xmlMapper;
        this.csvMapper = csvMapper;
    }

    /**
     * Counts number of occurrences for provided string and substring.
     *
     * @param string main string.
     * @param substring substring.
     *
     * @return number of occurrences.
     */
    // Apparently, carry this method out to a new class
    public static int countNumberOfOccurrences(@NonNull String string, @NonNull String substring) {
        int count = 0;
        int index = 0;

        while ((index = string.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }

        return count;
    }

    /**
     * <p>
     *     This method represents custom conversion from {@link String} to {@link Boolean}.<br>
     *     <b>The key feature</b> is that it converts {@code 1} to {@code true}.
     * </p>
     *
     * @param string any {@link String} value to convert.
     */
    // Apparently, carry this method out to a new class
    public static Boolean toBoolean(String string) {
        if (string == null) {
            return null;
        }

        boolean result = false;
        try {
            if (Integer.parseInt(string) == 1) {
                result = true;
            }
        } catch (NumberFormatException e) {
            result = Boolean.parseBoolean(string);
        }
        return result;
    }

    /**
     * <p>
     *     Converts JSON file to CSV file. Any other extensions are not supported.<br>
     *     <b>Assumption</b>: all nested objects in JSON file will be written to CSV file as strings.
     * </p>
     *
     * @param jsonFile JSON file written in {@link MultipartFile} instance.
     * @param patternId ID of a pattern, which already exists in database.
     *
     * @return path to converted CSV file.
     *
     * @throws IllegalArgumentException if an incorrect JSON structure was provided in {@code jsonFile}.
     * @throws NullPointerException if filename is null.
     * @throws UnsupportedExtensionException if {@code jsonFile} was provided without '.json' extension.
     * @throws EntityNotFoundException if pattern is not found in database by {@code patternId}.
     * @throws IllegalPatternException if pattern contains modification with null or empty {@code oldName} and {@code newName} fields.
     */
    @SuppressWarnings(value = {"unchecked"})
    public @NonNull Path convertJsonFileToCsv(
            @NonNull MultipartFile jsonFile,
            UUID patternId
    ) throws IOException {
        validateArgumentsForConversion(jsonFile, ".json");

        Path csvPath = Files.createTempFile(getFilenameWithoutExtension(jsonFile, ".json"), ".csv");

        if (jsonFile.isEmpty()) {
            return csvPath;
        }

        // Start converting
        List<Map<String, Object>> rows = new ArrayList<>();

        JsonNode root;
        try {
            root = jsonMapper.readTree(jsonFile.getInputStream());
        } catch (JsonProcessingException e) {
            logger.error("convertJsonFileToCsv: JsonProcessingException has been thrown");
            throw new IllegalArgumentException(e.getMessage());
        }

        if (root.isArray()) {
            for (JsonNode node : root) {
                rows.add(jsonMapper.convertValue(node, Map.class));
            }
        } else if (root.isObject()) {
            rows.add(jsonMapper.convertValue(root, Map.class));
        } else {
            throw new IllegalArgumentException("Unsupported JSON structure for CSV conversion");
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("JSON file contains no rows to convert");
        }

        rows = applyPattern(rows, patternRepository.findPatternById(patternId));

        CsvSchema.Builder csvSchemaBuilder = CsvSchema.builder();
        for (String column : rows.getFirst().keySet()) {
            csvSchemaBuilder.addColumn(column);
        }

        // Converting nested structures to JSON strings
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }

            for (Map.Entry<String, Object> entry : new ArrayList<>(row.entrySet())) {
                Object value = entry.getValue();

                if (value instanceof Map || value instanceof Collection) {
                    entry.setValue(jsonMapper.writeValueAsString(value));
                } else if (value == null) {
                    entry.setValue("");
                }
            }
        }

        // Writing converted data
        CsvSchema csvSchema = csvSchemaBuilder.build().withHeader();

        try {
            csvMapper.writerFor(List.class).with(csvSchema).writeValue(csvPath.toFile(), rows);
        } catch (CsvWriteException e) {
            logger.error("convertJsonFileToCsv: CsvWriteException has been thrown");
            throw new IllegalArgumentException(e.getMessage());
        }

        return csvPath;
    }

    /**
     * Converts CSV file to JSON file. Any other extensions are not supported.
     *
     * @param csvFile CSV file written in {@link MultipartFile} instance.
     * @param patternId ID of a pattern, which already exists in database.
     *
     * @return path to converted JSON file.
     *
     * @throws IllegalArgumentException if an incorrect CSV structure was provided in {@code csvFile}.
     * @throws NullPointerException if filename is null.
     * @throws UnsupportedExtensionException if {@code csvFile} was provided without '.csv' extension.
     * @throws EntityNotFoundException if pattern is not found in database by {@code patternId}.
     * @throws IllegalPatternException if pattern contains modification with null or empty {@code oldName} and {@code newName} fields.
     */
    public @NonNull Path convertCsvFileToJson(
            @NonNull MultipartFile csvFile,
            UUID patternId
    ) throws IOException {
        validateArgumentsForConversion(csvFile, ".csv");

        // Create temporarily JSON file for writing converted data
        String filenameWithoutExtension = getFilenameWithoutExtension(csvFile, ".csv");
        Path jsonPath = Files.createTempFile(filenameWithoutExtension, ".json");

        if (csvFile.isEmpty()) {
            return jsonPath;
        }

        // Start converting
        CsvSchema csvSchema = CsvSchema.emptySchema().withHeader();

        List<Map<String, Object>> rows = new ArrayList<>();
        MappingIterator<Map<String, String>> iterator = csvMapper.readerFor(Map.class).
                with(csvSchema).readValues(csvFile.getInputStream());

        while (iterator.hasNext()) {
            Map<String, String> row = iterator.next();
            Map<String, Object> convertedRow = new LinkedHashMap<>();

            // Convert data types
            for (Map.Entry<String, String> entry : row.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                if (value == null || value.isEmpty()) {
                    convertedRow.put(key, null);
                    continue;
                }

                try {
                    if (value.startsWith("{") || value.startsWith("[")) {
                        JsonNode node = csvMapper.readTree(value);
                        if (node.isObject()) {
                            convertedRow.put(key, csvMapper.convertValue(node, Map.class));
                        } else if (node.isArray()) {
                            convertedRow.put(key, csvMapper.convertValue(node, List.class));
                        }
                    } else if (value.matches("-?\\d+")) {
                        convertedRow.put(key, Long.parseLong(value));
                    } else if (value.matches("-?\\d*\\.\\d+")) {
                        convertedRow.put(key, Double.parseDouble(value));
                    } else {
                        convertedRow.put(key, value);
                    }
                } catch (JsonProcessingException | NumberFormatException e) {
                    convertedRow.put(key, value);
                }
            }

            rows.add(convertedRow);
        }

        rows = applyPattern(rows, patternRepository.findPatternById(patternId));

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("CSV file contains no rows to convert");
        }

        // Writing converted data
        if (rows.size() == 1) {
            jsonMapper.writeValue(jsonPath.toFile(), rows.getFirst());
        } else {
            jsonMapper.writeValue(jsonPath.toFile(), rows);
        }

        return jsonPath;
    }

    /**
     * Converts JSON file to XML file. Any other extensions are not supported.
     *
     * @param jsonFile JSON file written in {@link MultipartFile} instance.
     * @param patternId ID of a pattern, which already exists in database.
     *
     * @return path to converted XML file.
     *
     * @throws IllegalArgumentException if an incorrect JSON structure was provided in {@code jsonFile}.
     * @throws NullPointerException if filename is null.
     * @throws UnsupportedExtensionException if {@code jsonFile} was provided without '.json' extension.
     * @throws EntityNotFoundException if pattern is not found in database by {@code patternId}.
     * @throws IllegalPatternException if pattern contains modification with null or empty {@code oldName} and {@code newName} fields.
     */
    @SuppressWarnings(value = {"unchecked"})
    public @NonNull Path convertJsonFileToXml(
            @NonNull MultipartFile jsonFile,
            UUID patternId
    ) throws IOException {
        validateArgumentsForConversion(jsonFile, ".json");

        // Create temporarily XML file for writing converted data
        String filenameWithoutExtension = getFilenameWithoutExtension(jsonFile, ".json");
        Path xmlPath = Files.createTempFile(filenameWithoutExtension, ".xml");

        if (jsonFile.isEmpty()) {
            return xmlPath;
        }

        // Start converting
        List<Map<String, Object>> rows = new ArrayList<>();
        JsonNode root = jsonMapper.readTree(jsonFile.getInputStream());

        if (root.isArray()) {
            for (JsonNode node : root) {
                rows.add(jsonMapper.convertValue(node, Map.class));
            }
        } else if (root.isObject()) {
            rows.add(jsonMapper.convertValue(root, Map.class));
        } else {
            throw new IllegalArgumentException("Unsupported JSON structure for XML conversion");
        }

        rows = applyPattern(rows, patternRepository.findPatternById(patternId));

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("JSON file contains no rows to convert");
        }

        // Writing converted data
        xmlMapper.writeValue(xmlPath.toFile(), rows);

        return xmlPath;
    }

    /**
     * Converts XML file to JSON file. Any other extensions are not supported.
     *
     * @param xmlFile XML file written in {@link MultipartFile} instance.
     * @param patternId ID of a pattern, which already exists in database.
     *
     * @return path to converted JSON file.
     *
     * @throws IllegalArgumentException if an incorrect XML structure was provided in {@code xmlFile}.
     * @throws NullPointerException if filename is null.
     * @throws UnsupportedExtensionException if {@code xmlFile} was provided without '.xml' extension.
     * @throws EntityNotFoundException if pattern is not found in database by {@code patternId}.
     * @throws IllegalPatternException if pattern contains modification with null or empty {@code oldName} and {@code newName} fields.
     */
    @SuppressWarnings(value = {"unchecked"})
    public @NonNull Path convertXmlFileToJson(
            @NonNull MultipartFile xmlFile,
            UUID patternId
    ) throws IOException {
        validateArgumentsForConversion(xmlFile, ".xml");

        // Create temporarily JSON file for writing converted data
        String filenameWithoutExtension = getFilenameWithoutExtension(xmlFile, ".xml");
        Path jsonPath = Files.createTempFile(filenameWithoutExtension, ".json");

        if (xmlFile.isEmpty()) {
            return jsonPath;
        }

        // Start converting
        List<Map<String, Object>> rows = new ArrayList<>();

        JsonNode root;
        try {
            root = xmlMapper.readTree(xmlFile.getInputStream());
        } catch (JsonProcessingException e) {
            logger.error("convertXmlFileToJson: JsonProcessingException has been thrown");
            throw new IllegalArgumentException(e.getMessage());
        }

        if (root.isObject() && root.size() == 1 && root.has("item")) {
            root = root.get("item");
        }

        if (root.isArray()) {
            for (JsonNode node : root) {
                rows.add(xmlMapper.convertValue(node, Map.class));
            }
        } else if (root.isObject()) {
            Map<String, Object> map = xmlMapper.convertValue(root, Map.class);
            if (!map.isEmpty()) {
                rows.add(map);
            }
        } else {
            throw new IllegalArgumentException("Unsupported XML structure for JSON conversion");
        }

        // Convert data types
        for (Map<String, Object> row : rows) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String stringValue) {
                    if (stringValue.isEmpty()) {
                        entry.setValue(null);
                        continue;
                    }

                    try {
                        if (stringValue.matches("-?\\d+")) {
                            entry.setValue(Long.parseLong(stringValue));
                        } else if (stringValue.matches("-?\\d*\\.\\d+")) {
                            entry.setValue(Double.parseDouble(stringValue));
                        }
                    } catch (NumberFormatException _) {}
                }
            }
        }

        rows = applyPattern(rows, patternRepository.findPatternById(patternId));

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("XML file contains no rows to convert");
        }

        // Writing converted data
        if (rows.size() == 1) {
            jsonMapper.writeValue(jsonPath.toFile(), rows.getFirst());
        } else {
            jsonMapper.writeValue(jsonPath.toFile(), rows);
        }

        return jsonPath;
    }

    /**
     * <p>
     *     Converts XML file to CSV file. Any other extensions are not supported.<br>
     *     <b>Assumption</b>: all nested objects in XML file will be written to CSV file as strings.
     * </p>
     *
     * @param xmlFile XML file written in {@link MultipartFile} instance.
     * @param patternId ID of a pattern, which already exists in database.
     *
     * @return path to converted CSV file.
     *
     * @throws IllegalArgumentException if an incorrect XML structure was provided in {@code xmlFile}.
     * @throws NullPointerException if filename is null.
     * @throws UnsupportedExtensionException if {@code xmlFile} was provided without '.xml' extension.
     * @throws EntityNotFoundException if pattern is not found in database by {@code patternId}.
     * @throws IllegalPatternException if pattern contains modification with null or empty {@code oldName} and {@code newName} fields.
     */
    @SuppressWarnings(value = {"unchecked"})
    public @NonNull Path convertXmlFileToCsv(
            @NonNull MultipartFile xmlFile,
            UUID patternId
    ) throws IOException {
        validateArgumentsForConversion(xmlFile, ".xml");

        // Possible vulnerability here
        // Create temporarily CSV file for writing converted data
        String filenameWithoutExtension = getFilenameWithoutExtension(xmlFile, ".xml");
        Path csvPath = Files.createTempFile(filenameWithoutExtension, ".csv");

        if (xmlFile.isEmpty()) {
            return csvPath;
        }

        // Start converting
        List<Map<String, Object>> rows = new ArrayList<>();

        JsonNode root;
        try {
            root = xmlMapper.readTree(xmlFile.getInputStream());
        } catch (JsonProcessingException e) {
            logger.error("convertXmlFileToCsv: JsonProcessingException has been thrown");
            throw new IllegalArgumentException(e.getMessage());
        }

        if (root.isObject() && root.size() == 1 && root.has("item")) {
            root = root.get("item");
        }

        if (root.isArray()) {
            for (JsonNode node : root) {
                rows.add(xmlMapper.convertValue(node, Map.class));
            }
        } else if (root.isObject()) {
            rows.add(xmlMapper.convertValue(root, Map.class));
        } else {
            throw new IllegalArgumentException("Unsupported XML structure for CSV conversion");
        }

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("XML file contains no rows to convert");
        }

        // Converting nested structures to XML strings
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }

            for (Map.Entry<String, Object> entry : new ArrayList<>(row.entrySet())) {
                Object value = entry.getValue();

                if (value instanceof Map || value instanceof Collection) {
                    entry.setValue(xmlMapper.writeValueAsString(value));
                } else if (value == null) {
                    entry.setValue("");
                }
            }
        }

        rows = applyPattern(rows, patternRepository.findPatternById(patternId));

        CsvSchema.Builder csvSchemaBuilder = CsvSchema.builder();
        for (String column : rows.getFirst().keySet()) {
            csvSchemaBuilder.addColumn(column);
        }

        // Writing converted data
        try {
            CsvSchema csvSchema = csvSchemaBuilder.build().withHeader();
            csvMapper.writerFor(List.class).with(csvSchema).writeValue(csvPath.toFile(), rows);
        } catch (CsvWriteException e) {
            logger.error("convertXmlFileToCsv: CsvWriteException has been thrown");
            throw new IllegalArgumentException(e.getMessage());
        }

        return csvPath;
    }

    /**
     * Converts CSV file to XML file. Any other extensions are not supported.
     *
     * @param csvFile CSV file written in {@link MultipartFile} instance.
     * @param patternId ID of a pattern, which already exists in database.
     *
     * @return path to converted XML file.
     *
     * @throws IllegalArgumentException if an incorrect CSV structure was provided in {@code csvFile}.
     * @throws NullPointerException if filename is null.
     * @throws UnsupportedExtensionException if {@code csvFile} was provided without '.csv' extension.
     * @throws EntityNotFoundException if pattern is not found in database by {@code patternId}.
     * @throws IllegalPatternException if pattern contains modification with null or empty {@code oldName} and {@code newName} fields.
     */
    public @NonNull Path convertCsvFileToXml(
            @NonNull MultipartFile csvFile,
            UUID patternId
    ) throws IOException {
        validateArgumentsForConversion(csvFile, ".csv");

        // Create temporarily XML file for writing converted data
        String filenameWithoutExtension = getFilenameWithoutExtension(csvFile, ".csv");
        Path xmlPath = Files.createTempFile(filenameWithoutExtension, ".xml");

        if (csvFile.isEmpty()) {
            return xmlPath;
        }

        // Start converting
        CsvSchema csvSchema = CsvSchema.emptySchema().withHeader();

        List<Map<String, Object>> rows = new ArrayList<>();
        MappingIterator<Map<String, String>> iterator = csvMapper.readerFor(Map.class).
                with(csvSchema).readValues(csvFile.getInputStream());

        while (iterator.hasNext()) {
            Map<String, String> row = iterator.next();
            Map<String, Object> convertedRow = new LinkedHashMap<>();

            for (Map.Entry<String, String> entry : row.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                if (value == null || value.isEmpty()) {
                    convertedRow.put(key, null);
                    continue;
                }

                try {
                    if (value.startsWith("{") || value.startsWith("[")) {
                        JsonNode node = csvMapper.readTree(value);
                        if (node.isObject()) {
                            convertedRow.put(key, csvMapper.convertValue(node, Map.class));
                        } else if (node.isArray()) {
                            convertedRow.put(key, csvMapper.convertValue(node, List.class));
                        }
                    } else if (value.matches("-?\\d+")) {
                        convertedRow.put(key, Long.parseLong(value));
                    } else if (value.matches("-?\\d*\\.\\d+")) {
                        convertedRow.put(key, Double.parseDouble(value));
                    } else {
                        convertedRow.put(key, value);
                    }
                } catch (JsonProcessingException | NumberFormatException e) {
                    convertedRow.put(key, value);
                }
            }

            rows.add(convertedRow);
        }

        rows = applyPattern(rows, patternRepository.findPatternById(patternId));

        if (rows.isEmpty()) {
            throw new IllegalArgumentException("CSV file contains no rows to convert");
        }

        // Writing converted data
        if (rows.size() == 1) {
            xmlMapper.writeValue(xmlPath.toFile(), rows.getFirst());
        } else {
            xmlMapper.writeValue(xmlPath.toFile(), rows);
        }

        return xmlPath;
    }

    /**
     * @param file any {@link MultipartFile}.
     * @param currentExtension current extension of {@code file}. It is supposed to have "." in itself.
     * For example, {@code .json} or {@code .csv} are valid values for {@code currentExtension},
     * and {@code json}, {@code csv} are not valid.
     *
     * @return filename without provided {@code currentExtension}.
     *
     * @throws IllegalArgumentException if {@code currentExtension} doesn't contain the only "." at the beginning of itself.
     * @throws NullPointerException if the filename of {@code file} is null.
     * @throws UnsupportedExtensionException if {@code currentExtension} is not extension of the {@code file}.
     */
    private @NonNull String getFilenameWithoutExtension(@NonNull MultipartFile file, @NonNull String currentExtension) {
        if (!currentExtension.startsWith(".")) {
            throw new IllegalArgumentException("currentExtension doesn't start with '.' symbol; currentExtension="
                    + currentExtension);
        }

        if (countNumberOfOccurrences(currentExtension, ".") > 1) {
            throw new IllegalArgumentException("currentExtension has more than one dot; currentExtension="
                    + currentExtension);
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new NullPointerException("filename is null");
        }

        if (!filename.endsWith(currentExtension)) {
            throw new UnsupportedExtensionException("Unsupported extension was provided; currentExtension="
                    + currentExtension);
        }

        return filename.substring(0, filename.length() - currentExtension.length()) ;
    }

    /**
     * <p>
     *     This method validates arguments for a conversion.
     *     It throws an exception if validation doesn't pass.
     * </p>
     *
     * @param file requested file to convert.
     * @param currentExtension current extension of {@code file}. {@code currentExtension} is supposed to start with dot.
     * For example, it is more preferably to send {@code .json} instead of {@code json} to this method despite the fact
     * that the validation doesn't fail in both cases.
     *
     * @throws NullPointerException if filename is null.
     * @throws UnsupportedExtensionException if {@code file} has extension, which doesn't correspond to {@code currentExtension}.
     */
    private void validateArgumentsForConversion(@NonNull MultipartFile file, @NonNull String currentExtension) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            throw new NullPointerException("filename is null");
        }

        if (!filename.endsWith(currentExtension)) {
            throw new UnsupportedExtensionException("Provided file doesn't have '" + currentExtension + "' extension");
        }
    }

    /**
     * Applies {@code pattern} to {@code fieldList}.
     *
     * @param fieldsList read data.
     * @param pattern pattern to apply.
     *
     * @return modified {@code fieldsList} after applying {@code pattern}.
     * If {@code pattern} is null {@code fieldsList} will be returned with no changes.
     *
     * @throws IllegalPatternException if pattern contains modification with null or empty {@code oldName}
     * and {@code newName} fields.
     */
    private @NonNull List<Map<String, Object>> applyPattern(
            @NonNull List<Map<String, Object>> fieldsList,
            @Nullable Pattern pattern
    ) {
        if (pattern == null) {
            return fieldsList;
        }

        List<Modification> modifications = modificationRepository.findAllByPatternId(pattern.getId());
        return PatternHandler.applyModifications(fieldsList, modifications);
    }

    private static class PatternHandler {
        /**
         * Apply {@code modifications} to {@code fieldList}.
         *
         * @param fieldsList read data.
         * @param modifications modifications to apply.
         *
         * @throws IllegalPatternException if pattern contains modification with null or empty {@code oldName}
         * and {@code newName} fields.
         */
        public static @NonNull List<Map<String, Object>> applyModifications(
                @NonNull List<Map<String, Object>> fieldsList,
                @NonNull List<Modification> modifications
        ) {
            for (Map<String, Object> fields : fieldsList) {
                for (Modification modification : modifications) {
                    DeletingFieldResult deletionAttempt = deleteField(fields, modification);
                    if (deletionAttempt.isSucceeded()) {
                        fields = deletionAttempt.fields();
                        continue;
                    }

                    fields = alterField(fields, modification);
                }
            }

            return fieldsList;
        }
        
        private static @NonNull DeletingFieldResult deleteField(
                @NonNull Map<String, Object> fields,
                @NonNull Modification modification
        ) {
            if (fields.containsKey(modification.getOldName()) && (modification.getNewName() == null)
                    && (modification.getNewType() == null) && (modification.getNewValue() == null)
            ) {
                fields.remove(modification.getOldName());
                return new DeletingFieldResult(fields, true);
            }

            return new DeletingFieldResult(fields, false);
        }

        /**
         * Changes value, name and type of field.
         */
        private static @NonNull Map<String, Object> alterField(
                @NonNull Map<String, Object> fields,
                @NonNull Modification modification
        ) {
            String fieldNameForTypeConversion = null;
            boolean isNewFieldAdded = false;

            AddingFieldResult addingAttempt = addField(fields, modification);
            if (addingAttempt.isSucceeded()) {
                fields = addingAttempt.fields();

                isNewFieldAdded = true;
                fieldNameForTypeConversion = modification.getNewName();
            }

            if (!isNewFieldAdded) {
                UpdateFieldResult updatingAttempt = updateField(fields, modification);

                fields = updatingAttempt.fields();
                fieldNameForTypeConversion = updatingAttempt.fieldNameForTypeConversion();
            }

            return convertField(
                    fields,
                    fieldNameForTypeConversion,
                    modification.getNewType()
            );
        }

        private static @NonNull AddingFieldResult addField(
                @NonNull Map<String, Object> fields,
                @NonNull Modification modification
        ) {
            boolean isSucceeded = false;

            if (modification.getOldName() == null) {
                if (modification.getNewName() == null) {
                    throw new IllegalPatternException(
                            "Modification with null or empty oldName and newName was provided; modification=" +
                                    modification
                    );
                }

                fields.put(modification.getNewName(), modification.getNewValue());
                isSucceeded = true;
            }

            return new AddingFieldResult(fields, isSucceeded);
        }

        private static @NonNull UpdateFieldResult updateField(
                @NonNull Map<String, Object> fields,
                @NonNull Modification modification
        ) {
            String fieldNameForTypeConversion = null;

            // Updating existing fields
            if (fields.containsKey(modification.getOldName())) {
                fieldNameForTypeConversion = modification.getOldName();

                // Changing values of fields
                if (modification.getNewValue() != null) {
                    fields.put(modification.getOldName(), modification.getNewValue());
                }

                // Changing names of fields
                if (modification.getNewName() != null) {
                    Object value = fields.get(modification.getOldName());
                    fields.remove(modification.getOldName());
                    fields.put(modification.getNewName(), value);

                    fieldNameForTypeConversion = modification.getNewName();
                }
            }

            return new UpdateFieldResult(fields, fieldNameForTypeConversion);
        }

        private static @NonNull Map<String, Object> convertField(
                @NonNull Map<String, Object> fields,
                @Nullable String fieldName,
                @Nullable String newType
        ) {
            if (newType == null) {
                return fields;
            }

            if (fields.containsKey(fieldName)) {
                Object value = fields.get(fieldName);

                if (value == null) {
                    return fields;
                }

                switch (newType) {
                    case "Integer":
                        fields.put(fieldName, Integer.parseInt(value.toString()));
                        break;
                    case "Float":
                        fields.put(fieldName, Float.parseFloat(value.toString()));
                        break;
                    case "Boolean":
                        fields.put(fieldName, toBoolean(value.toString()));
                        break;
                    default:
                        fields.put(fieldName, value.toString());
                        break;
                }
            }

            return fields;
        }

        private record AddingFieldResult(
                Map<String, Object> fields,
                boolean isSucceeded
        ) {
        }

        private record UpdateFieldResult(
                Map<String, Object> fields,
                String fieldNameForTypeConversion
        ) {
        }

        private record DeletingFieldResult(
                Map<String, Object> fields,
                boolean isSucceeded
        ) {
        }
    }
}