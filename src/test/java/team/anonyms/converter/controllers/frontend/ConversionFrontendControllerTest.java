package team.anonyms.converter.controllers.frontend;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import team.anonyms.converter.services.frontend.ConversionFrontendService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;

@WebMvcTest(ConversionFrontendController.class)
@ContextConfiguration(classes = ConversionFrontendController.class)
class ConversionFrontendControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversionFrontendService conversionFrontendService;

    // ЭТО НУЖНО!!! вспомогательный метод для json-части запроса
    private MockPart getPatternPart() {
        UUID id = UUID.randomUUID();
        MockPart part = new MockPart("pattern", id.toString().getBytes());
        part.getHeaders().setContentType(MediaType.TEXT_PLAIN);
        return part;
    }

    @Test
    void testConvertJsonFileToCsv_Success() throws Exception {
        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "data.json",
                MediaType.APPLICATION_JSON_VALUE,
                "{\"key\":\"value\"}".getBytes()
        );

        Path tempFile = Files.createTempFile("converted", ".csv");
        Files.write(tempFile, "key,value".getBytes());

        // теперь везде вместо null передается два any()
        Mockito.when(conversionFrontendService.convertJsonFileToCsv(any(), any())).thenReturn(tempFile);

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/conversion/json/csv")
                        .file(mockFile)
                        .part(getPatternPart()))
                .andExpect(MockMvcResultMatchers.request().asyncStarted())
                .andReturn();

        mockMvc.perform(MockMvcRequestBuilders.asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.header().string("Content-Disposition", "attachment; filename=\"data.csv\""))
                .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith("text/csv"))
                .andExpect(MockMvcResultMatchers.content().string("key,value"));
    }

    @Test
    void testConvertCsvFileToJson_Success() throws Exception {
        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "data.csv",
                "text/csv",
                "key,value\nname,test".getBytes()
        );

        Path tempFile = Files.createTempFile("converted", ".json");
        Files.write(tempFile, "{\"name\":\"test\"}".getBytes());

        Mockito.when(conversionFrontendService.convertCsvFileToJson(any(), any())).thenReturn(tempFile);

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/conversion/csv/json")
                        .file(mockFile)
                        .part(getPatternPart()))
                .andExpect(MockMvcResultMatchers.request().asyncStarted())
                .andReturn();

        mockMvc.perform(MockMvcRequestBuilders.asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.header().string("Content-Disposition", "attachment; filename=\"data.json\""))
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(MockMvcResultMatchers.content().string("{\"name\":\"test\"}"));
    }

    @Test
    void testConvertJsonFileToXml_Success() throws Exception {
        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "data.json",
                MediaType.APPLICATION_JSON_VALUE,
                "{\"name\":\"test\"}".getBytes()
        );

        Path tempFile = Files.createTempFile("converted", ".xml");
        Files.write(tempFile, "<name>test</name>".getBytes());

        Mockito.when(conversionFrontendService.convertJsonFileToXml(any(), any())).thenReturn(tempFile);

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/conversion/json/xml")
                        .file(mockFile)
                        .part(getPatternPart()))
                .andExpect(MockMvcResultMatchers.request().asyncStarted())
                .andReturn();

        mockMvc.perform(MockMvcRequestBuilders.asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.header().string("Content-Disposition", "attachment; filename=\"data.xml\""))
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(MockMvcResultMatchers.content().string("<name>test</name>"));
    }

    @Test
    void testConvertXmlFileToJson_Success() throws Exception {
        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "data.xml",
                MediaType.APPLICATION_XML_VALUE,
                "<name>test</name>".getBytes()
        );

        Path tempFile = Files.createTempFile("converted", ".json");
        Files.write(tempFile, "{\"name\":\"test\"}".getBytes());

        Mockito.when(conversionFrontendService.convertXmlFileToJson(any(), any())).thenReturn(tempFile);

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/conversion/xml/json")
                        .file(mockFile)
                        .part(getPatternPart()))
                .andExpect(MockMvcResultMatchers.request().asyncStarted())
                .andReturn();

        mockMvc.perform(MockMvcRequestBuilders.asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.header().string("Content-Disposition", "attachment; filename=\"data.json\""))
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(MockMvcResultMatchers.content().string("{\"name\":\"test\"}"));
    }

    @Test
    void testConvertXmlFileToCsv_Success() throws Exception {
        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "data.xml",
                MediaType.APPLICATION_XML_VALUE,
                "<name>test</name>".getBytes()
        );

        Path tempFile = Files.createTempFile("converted", ".csv");
        Files.write(tempFile, "name\ntest".getBytes());

        Mockito.when(conversionFrontendService.convertXmlFileToCsv(any(), any())).thenReturn(tempFile);

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/conversion/xml/csv")
                        .file(mockFile)
                        .part(getPatternPart()))
                .andExpect(MockMvcResultMatchers.request().asyncStarted())
                .andReturn();

        mockMvc.perform(MockMvcRequestBuilders.asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.header().string("Content-Disposition", "attachment; filename=\"data.csv\""))
                .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith("text/csv"))
                .andExpect(MockMvcResultMatchers.content().string("name\ntest"));
    }

    @Test
    void testConvertCsvFileToXml_Success() throws Exception {
        MockMultipartFile mockFile = new MockMultipartFile(
                "file",
                "data.csv",
                "text/csv",
                "name\ntest".getBytes()
        );

        Path tempFile = Files.createTempFile("converted", ".xml");
        Files.write(tempFile, "<name>test</name>".getBytes());

        Mockito.when(conversionFrontendService.convertCsvFileToXml(any(), any())).thenReturn(tempFile);

        MvcResult mvcResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/conversion/csv/xml")
                        .file(mockFile)
                        .part(getPatternPart()))
                .andExpect(MockMvcResultMatchers.request().asyncStarted())
                .andReturn();

        mockMvc.perform(MockMvcRequestBuilders.asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.header().string("Content-Disposition", "attachment; filename=\"data.xml\""))
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(MockMvcResultMatchers.content().string("<name>test</name>"));
    }
}