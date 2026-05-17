package team.anonyms.converter.controllers.frontend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import team.anonyms.converter.controllers.frontend.pagination.PaginationHandler;
import team.anonyms.converter.dto.controller.modification.ModificationControllerDto;
import team.anonyms.converter.mappers.ModificationMapper;
import team.anonyms.converter.services.frontend.ModificationService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/modifications")
@SuppressWarnings(value = {"DataFlowIssue"})
public class ModificationController {
    private static final Logger logger = LoggerFactory.getLogger(ModificationController.class);

    private final ModificationService modificationService;
    private final ModificationMapper modificationMapper;
    private final PaginationHandler<ModificationControllerDto> paginationHandler;

    public ModificationController(
            ModificationService modificationService,
            ModificationMapper modificationMapper,
            PaginationHandler<ModificationControllerDto> paginationHandler
    ) {
        this.modificationService = modificationService;
        this.modificationMapper = modificationMapper;
        this.paginationHandler = paginationHandler;
    }

    @GetMapping("/{patternId}/{limit}/{offset}")
    public ResponseEntity<List<ModificationControllerDto>> getModificationsByPatternId(
            @PathVariable UUID patternId,
            @PathVariable int limit,
            @PathVariable int offset
    ) {
        logger.info("Called getAllModificationsByPatternId; id={}", patternId);

        UUID userId = (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<ModificationControllerDto> allModifications = modificationService
                .getAllModificationsByPatternId(patternId, userId)
                .stream()
                .map(modificationMapper::modificationServiceDtoToControllerDto)
                .toList();

        return ResponseEntity.ok(paginationHandler.makeSlice(allModifications, offset, limit));
    }

    @GetMapping("/{patternId}")
    public ResponseEntity<Long> getNumberOfAllModificationsByPatternId(
            @PathVariable UUID patternId
    ) {
        logger.info("Called getNumberOfModificationsByPatternId; patternId={}", patternId);

        UUID userId = (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        return ResponseEntity.ok(modificationService.getNumberOfAllModificationsByPatternId(patternId, userId));
    }
}
