package team.anonyms.converter.services.frontend;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import team.anonyms.converter.dto.service.modification.ModificationServiceDto;
import team.anonyms.converter.entities.Pattern;
import team.anonyms.converter.mappers.ModificationMapper;
import team.anonyms.converter.repositories.ModificationRepository;
import team.anonyms.converter.repositories.PatternRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ModificationService {
    private final ModificationRepository modificationRepository;
    private final PatternRepository patternRepository;
    private final ModificationMapper modificationMapper;

    public ModificationService(
            ModificationRepository modificationRepository,
            PatternRepository patternRepository,
            ModificationMapper modificationMapper
    ) {
        this.modificationRepository = modificationRepository;
        this.patternRepository = patternRepository;
        this.modificationMapper = modificationMapper;
    }

    /**
     * TODO: adapt {@code getAllPatternsByUserId} for lazy loading.
     */
    public List<ModificationServiceDto> getAllModificationsByPatternId(UUID patternId, UUID userId) {
        Optional<Pattern> patternOptional = patternRepository.findById(patternId);
        if (patternOptional.isEmpty()) {
            throw new EntityNotFoundException("Pattern not found; id=" + patternId);
        }

        Pattern pattern = patternOptional.get();
        if (!pattern.getUser().getId().equals(userId)) {
            throw new AccessDeniedException(
                    "Sender ID doesn't match with user ID of that pattern; userId=" + pattern.getUser().getId()
                            + "; senderId=" + userId
            );
        }

        return modificationRepository.findAllByPatternId(patternId).stream()
                .map(modificationMapper::modificationToServiceDto)
                .toList();
    }

    public Long getNumberOfAllModificationsByPatternId(UUID patternId, UUID userId) {
        Optional<Pattern> patternOptional = patternRepository.findById(patternId);
        if (patternOptional.isEmpty()) {
            throw new EntityNotFoundException("Pattern not found; id=" + patternId);
        }

        Pattern pattern = patternOptional.get();
        if (!pattern.getUser().getId().equals(userId)) {
            throw new AccessDeniedException(
                    "Sender ID doesn't match with user ID of that pattern; userId=" + pattern.getUser().getId()
                            + "; senderId=" + userId
            );
        }


        return modificationRepository.countAllByPatternId(patternId);
    }
}
