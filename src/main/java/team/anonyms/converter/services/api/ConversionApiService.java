package team.anonyms.converter.services.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ConversionApiService {
    private final JsonMapper jsonMapper;
    private final XmlMapper xmlMapper;

    public ConversionApiService(JsonMapper jsonMapper, XmlMapper xmlMapper) {
        this.jsonMapper = jsonMapper;
        this.xmlMapper = xmlMapper;
    }

    /**
     * Converts JSON data to XML data.
     *
     * @param body an arbitrary body of JSON request.
     *
     * @return converted XML data from {@code body}.
     *
     * @throws IllegalArgumentException if an incorrect JSON structure was provided.
     */
    public String convertJsonToXml(Map<String, Object> body) {
        try {
            return xmlMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    /**
     * Converts XML data to JSON data.
     *
     * @param body an arbitrary body of XML request.
     *
     * @return converted JSON data from {@code body}.
     *
     * @throws IllegalArgumentException if an incorrect XML structure was provided.
     */
    public String convertXmlToJson(Map<String, Object> body) {
        try {
            return jsonMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }
}
