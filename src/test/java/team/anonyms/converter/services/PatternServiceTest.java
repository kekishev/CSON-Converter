package team.anonyms.converter.services;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import team.anonyms.converter.dto.service.pattern.PatternServiceDto;
import team.anonyms.converter.dto.service.pattern.PatternToCreateServiceDto;
import team.anonyms.converter.dto.service.pattern.PatternToUpdateServiceDto;
import team.anonyms.converter.entities.Pattern;
import team.anonyms.converter.entities.User;
import team.anonyms.converter.mappers.ModificationMapper;
import team.anonyms.converter.mappers.PatternMapper;
import team.anonyms.converter.repositories.ModificationRepository;
import team.anonyms.converter.repositories.PatternRepository;
import team.anonyms.converter.repositories.UserRepository;
import team.anonyms.converter.services.frontend.PatternService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;

@ExtendWith(MockitoExtension.class)
class PatternServiceTest {

    @Mock
    private PatternRepository patternRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ModificationRepository modificationRepository;
    @Mock
    private PatternMapper patternMapper;
    @Mock
    private ModificationMapper modificationMapper;

    @InjectMocks
    private PatternService patternService;

    @Test
    void testCreatePattern_Success() {
        UUID userId = UUID.randomUUID();
        PatternToCreateServiceDto createDto = new PatternToCreateServiceDto("New Pattern", List.of());

        User mockUser = Mockito.mock(User.class);
        Pattern mockPattern = Mockito.mock(Pattern.class);
        PatternServiceDto responseDto = new PatternServiceDto(UUID.randomUUID(), "New Pattern");

        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        Mockito.when(patternMapper.patternToCreateServiceDtoToEntity(createDto, mockUser)).thenReturn(mockPattern);
        Mockito.when(patternMapper.patternToServiceDto(mockPattern)).thenReturn(responseDto);

        PatternServiceDto result = patternService.createPattern(createDto, userId);

        assertNotNull(result);
        assertEquals("New Pattern", result.name());
        Mockito.verify(patternRepository).save(mockPattern);
        Mockito.verify(modificationRepository).saveAll(anyList());
    }

    @Test
    void testCreatePattern_ThrowsEntityNotFound() {
        UUID userId = UUID.randomUUID();
        PatternToCreateServiceDto createDto = new PatternToCreateServiceDto("New Pattern", List.of());

        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> patternService.createPattern(createDto, userId));
    }

    @Test
    void testUpdatePattern_Success() {
        UUID patternId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PatternToUpdateServiceDto updateDto = new PatternToUpdateServiceDto(patternId, "Updated Name", List.of());

        Pattern mockPattern = Mockito.mock(Pattern.class);
        User mockUser = Mockito.mock(User.class);
        PatternServiceDto responseDto = new PatternServiceDto(patternId, "Updated Name");

        Mockito.when(patternRepository.findById(patternId)).thenReturn(Optional.of(mockPattern));
        Mockito.when(mockPattern.getUser()).thenReturn(mockUser);
        Mockito.when(mockUser.getId()).thenReturn(userId);
        Mockito.when(mockPattern.getId()).thenReturn(patternId);

        Mockito.when(patternMapper.patternToServiceDto(mockPattern)).thenReturn(responseDto);

        PatternServiceDto result = patternService.updatePattern(updateDto, userId);

        assertNotNull(result);
        Mockito.verify(mockPattern).setName("Updated Name");
        Mockito.verify(modificationRepository).deleteAllByPatternId(patternId);
        Mockito.verify(patternRepository).save(mockPattern);
        Mockito.verify(modificationRepository).saveAll(anyList());
    }

    @Test
    void testUpdatePattern_ThrowsAccessDenied() {
        UUID patternId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID hackerId = UUID.randomUUID();
        PatternToUpdateServiceDto updateDto = new PatternToUpdateServiceDto(patternId, "Hacked Name", List.of());

        Pattern mockPattern = Mockito.mock(Pattern.class);
        User mockUser = Mockito.mock(User.class);

        Mockito.when(patternRepository.findById(patternId)).thenReturn(Optional.of(mockPattern));
        Mockito.when(mockPattern.getUser()).thenReturn(mockUser);
        Mockito.when(mockUser.getId()).thenReturn(ownerId);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> patternService.updatePattern(updateDto, hackerId));
        assertTrue(ex.getMessage().contains("Sender ID doesn't match"));
    }

    @Test
    void testDeletePattern_Success() {
        UUID patternId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Pattern mockPattern = Mockito.mock(Pattern.class);
        User mockUser = Mockito.mock(User.class);

        Mockito.when(patternRepository.findById(patternId)).thenReturn(Optional.of(mockPattern));
        Mockito.when(mockPattern.getUser()).thenReturn(mockUser);
        Mockito.when(mockUser.getId()).thenReturn(userId);

        patternService.deletePattern(patternId, userId);

        Mockito.verify(modificationRepository).deleteAllByPatternId(patternId);
        Mockito.verify(patternRepository).delete(mockPattern);
    }

    @Test
    void testDeletePattern_ThrowsAccessDenied() {
        UUID patternId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID hackerId = UUID.randomUUID();

        Pattern mockPattern = Mockito.mock(Pattern.class);
        User mockUser = Mockito.mock(User.class);

        Mockito.when(patternRepository.findById(patternId)).thenReturn(Optional.of(mockPattern));
        Mockito.when(mockPattern.getUser()).thenReturn(mockUser);
        Mockito.when(mockUser.getId()).thenReturn(ownerId);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class, () -> patternService.deletePattern(patternId, hackerId));
        assertTrue(ex.getMessage().contains("Sender ID doesn't match"));
    }

    @Test
    void testDeletePattern_ThrowsEntityNotFound() {
        UUID patternId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Mockito.when(patternRepository.findById(patternId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> patternService.deletePattern(patternId, userId));
    }
}