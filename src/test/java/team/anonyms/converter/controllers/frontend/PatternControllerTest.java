package team.anonyms.converter.controllers.frontend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import team.anonyms.converter.controllers.frontend.pagination.PaginationHandler;
import team.anonyms.converter.dto.controller.pattern.PatternControllerDto;
import team.anonyms.converter.dto.controller.pattern.PatternToCreateControllerDto;
import team.anonyms.converter.dto.controller.pattern.PatternToUpdateControllerDto;
import team.anonyms.converter.dto.service.pattern.PatternServiceDto;
import team.anonyms.converter.dto.service.pattern.PatternToCreateServiceDto;
import team.anonyms.converter.dto.service.pattern.PatternToUpdateServiceDto;
import team.anonyms.converter.mappers.PatternMapper;
import team.anonyms.converter.services.frontend.PatternService;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PatternControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private PatternService patternService;

    @Mock
    private PatternMapper patternMapper;

    @Mock
    private PaginationHandler<PatternControllerDto> paginationHandler;

    @InjectMocks
    private PatternController patternController;

    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(patternController).build();

        userId = UUID.randomUUID();
        Authentication authentication = Mockito.mock(Authentication.class);
        Mockito.when(authentication.getPrincipal()).thenReturn(userId);

        SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    void testCreatePattern_Success() throws Exception {
        UUID patternId = UUID.randomUUID();
        PatternToCreateControllerDto requestDto = new PatternToCreateControllerDto("New Pattern", List.of());
        PatternToCreateServiceDto serviceRequestDto = new PatternToCreateServiceDto("New Pattern", List.of());
        PatternServiceDto serviceDto = new PatternServiceDto(patternId, "New Pattern");
        PatternControllerDto responseDto = new PatternControllerDto(patternId, "New Pattern");

        Mockito.when(patternMapper.patternToCreateControllerDtoToService(any(PatternToCreateControllerDto.class)))
                .thenReturn(serviceRequestDto);

        Mockito.when(patternService.createPattern(any(PatternToCreateServiceDto.class), eq(userId)))
                .thenReturn(serviceDto);

        Mockito.when(patternMapper.patternServiceDtoToControllerDto(any(PatternServiceDto.class)))
                .thenReturn(responseDto);

        mockMvc.perform(MockMvcRequestBuilders.post("/patterns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Pattern"));
    }

    @Test
    void testUpdatePattern_Success() throws Exception {
        UUID patternId = UUID.randomUUID();
        PatternToUpdateControllerDto requestDto = new PatternToUpdateControllerDto(patternId, "Updated Pattern", List.of());
        PatternToUpdateServiceDto serviceRequestDto = new PatternToUpdateServiceDto(patternId, "Updated Pattern", List.of());
        PatternServiceDto serviceDto = new PatternServiceDto(patternId, "Updated Pattern");
        PatternControllerDto responseDto = new PatternControllerDto(patternId, "Updated Pattern");

        Mockito.when(patternMapper.patternToUpdateControllerDtoToService(any(PatternToUpdateControllerDto.class)))
                .thenReturn(serviceRequestDto);

        Mockito.when(patternService.updatePattern(any(PatternToUpdateServiceDto.class), eq(userId)))
                .thenReturn(serviceDto);

        Mockito.when(patternMapper.patternServiceDtoToControllerDto(any(PatternServiceDto.class)))
                .thenReturn(responseDto);

        mockMvc.perform(MockMvcRequestBuilders.put("/patterns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Pattern"));
    }

    @Test
    void testDeletePattern_Success() throws Exception {
        UUID patternId = UUID.randomUUID();

        Mockito.doNothing().when(patternService).deletePattern(patternId, userId);

        mockMvc.perform(MockMvcRequestBuilders.delete("/patterns/" + patternId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }
}