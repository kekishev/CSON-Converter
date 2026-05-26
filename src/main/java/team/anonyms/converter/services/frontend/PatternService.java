package team.anonyms.converter.services.frontend;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import team.anonyms.converter.dto.service.pattern.PatternServiceDto;
import team.anonyms.converter.dto.service.pattern.PatternToCreateServiceDto;
import team.anonyms.converter.dto.service.pattern.PatternToUpdateServiceDto;
import team.anonyms.converter.entities.Modification;
import team.anonyms.converter.entities.Pattern;
import team.anonyms.converter.entities.User;
import team.anonyms.converter.mappers.ModificationMapper;
import team.anonyms.converter.mappers.PatternMapper;
import team.anonyms.converter.repositories.ModificationRepository;
import team.anonyms.converter.repositories.PatternRepository;
import team.anonyms.converter.repositories.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class PatternService {
    private final PatternRepository patternRepository;
    private final UserRepository userRepository;
    private final ModificationRepository modificationRepository;
    private final PatternMapper patternMapper;
    private final ModificationMapper modificationMapper;

    public PatternService(
            PatternRepository patternRepository,
            UserRepository userRepository,
            ModificationRepository modificationRepository,
            PatternMapper patternMapper,
            ModificationMapper modificationMapper
    ) {
        this.patternRepository = patternRepository;
        this.userRepository = userRepository;
        this.modificationRepository = modificationRepository;
        this.patternMapper = patternMapper;
        this.modificationMapper = modificationMapper;
    }

    /**
     * TODO: adapt {@code getAllPatternsByUserId} for lazy loading.
     */
    public List<PatternServiceDto> getAllPatternsByUserId(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found; id=" + userId);
        }

        return patternRepository.findAllByUserId(userId).stream().map(patternMapper::patternToServiceDto).toList();
    }

    public Long getNumberOfAllPatternsByUserId(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found; id=" + userId);
        }

        return patternRepository.countAllByUserId(userId);
    }

    @Transactional
    public PatternServiceDto createPattern(PatternToCreateServiceDto patternToCreate, UUID userId) {
        Optional<User> userOptional = userRepository.findById(userId);

        if (userOptional.isEmpty()) {
            throw new EntityNotFoundException("User not found; id=" + userId);
        }

        Pattern patternCreated = patternMapper.patternToCreateServiceDtoToEntity(patternToCreate, userOptional.get());

        List<Modification> modifications = patternToCreate.modifications().stream()
                .map(m -> modificationMapper.modificationToCreateServiceDtoToEntity(m, patternCreated))
                .toList();

        patternRepository.save(patternCreated);
        modificationRepository.saveAll(modifications);

        return patternMapper.patternToServiceDto(patternCreated);
    }

    @Transactional
    public PatternServiceDto updatePattern(PatternToUpdateServiceDto patternToUpdate, UUID userId) {
        Optional<Pattern> patternOptional = patternRepository.findById(patternToUpdate.id());
        if (patternOptional.isEmpty()) {
            throw new EntityNotFoundException("Pattern not found; id=" + patternToUpdate.id());
        }

        Pattern patternUpdated = patternOptional.get();
        if (!patternUpdated.getUser().getId().equals(userId)) {
            throw new AccessDeniedException(
                    "Sender ID doesn't match with user ID of that pattern; userId=" + patternUpdated.getUser().getId()
                            + "; senderId=" + userId
            );
        }

        List<Modification> modifications = patternToUpdate.modifications().stream()
                .map(m -> modificationMapper.modificationToUpdateServiceDtoToEntity(m, patternUpdated))
                .toList();

        patternUpdated.setName(patternToUpdate.name());

        modificationRepository.deleteAllByPatternId(patternUpdated.getId());
        patternRepository.save(patternUpdated);
        modificationRepository.saveAll(modifications);

        return patternMapper.patternToServiceDto(patternUpdated);
    }

    @Transactional
    public void deletePattern(UUID patternId, UUID userId) {
        Optional<Pattern> patternOptional = patternRepository.findById(patternId);
        if (patternOptional.isEmpty()) {
            throw new EntityNotFoundException("Pattern not found; patternId=" + patternId);
        }

        Pattern patternDeleted = patternOptional.get();
        if (!patternDeleted.getUser().getId().equals(userId)) {
            throw new AccessDeniedException(
                    "Sender ID doesn't match with user ID of that pattern; userId=" + patternDeleted.getUser().getId()
                            + "; senderId=" + userId
            );
        }

        modificationRepository.deleteAllByPatternId(patternId);
        patternRepository.delete(patternDeleted);
    }
}
