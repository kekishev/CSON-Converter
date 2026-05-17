package team.anonyms.converter.controllers.frontend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import team.anonyms.converter.controllers.frontend.pagination.PaginationHandler;
import team.anonyms.converter.dto.controller.pattern.PatternControllerDto;
import team.anonyms.converter.dto.controller.pattern.PatternToCreateControllerDto;
import team.anonyms.converter.dto.controller.pattern.PatternToUpdateControllerDto;
import team.anonyms.converter.dto.service.pattern.PatternServiceDto;
import team.anonyms.converter.mappers.PatternMapper;
import team.anonyms.converter.services.frontend.PatternService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/patterns")
@SuppressWarnings(value = {"DataFlowIssue"})
public class PatternController {
    private static final Logger logger = LoggerFactory.getLogger(PatternController.class);

    private final PatternService patternService;
    private final PatternMapper patternMapper;
    private final PaginationHandler<PatternControllerDto> paginationHandler;

    public PatternController(
            PatternService patternService,
            PatternMapper patternMapper,
            PaginationHandler<PatternControllerDto> paginationHandler
    ) {
        this.patternService = patternService;
        this.patternMapper = patternMapper;
        this.paginationHandler = paginationHandler;
    }

    @GetMapping("/{limit}/{offset}")
    public ResponseEntity<List<PatternControllerDto>> getPatternsByUserId(
            @PathVariable int limit,
            @PathVariable int offset
    ) {
        logger.info("Called getPatternsByUserId");

        UUID userId = (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<PatternControllerDto> allPatterns = patternService.getAllPatternsByUserId(userId)
                .stream()
                .map(patternMapper::patternServiceDtoToControllerDto)
                .toList();

        return ResponseEntity.ok(paginationHandler.makeSlice(allPatterns, offset, limit));
    }

    @GetMapping
    public ResponseEntity<Long> getNumberOfAllPatternsByUserId() {
        logger.info("Called getNumberOfPatternsByUserId");

        UUID userId = (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        return ResponseEntity.ok(patternService.getNumberOfAllPatternsByUserId(userId));
    }

    @PostMapping
    public ResponseEntity<PatternControllerDto> createPattern(
            @RequestBody PatternToCreateControllerDto patternToCreate
    ) {
        logger.info("Called createPattern; patternToCreate={}", patternToCreate);

        UUID userId = (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        PatternServiceDto patternCreated = patternService.createPattern(
                patternMapper.patternToCreateControllerDtoToService(patternToCreate),
                userId
        );

        return ResponseEntity.status(201).body(patternMapper.patternServiceDtoToControllerDto(patternCreated));
    }

    @PutMapping
    public ResponseEntity<PatternControllerDto> updatePattern(
            @RequestBody PatternToUpdateControllerDto patternToUpdate
    ) {
        logger.info("Called updatePattern; patternToUpdateControllerDto={}", patternToUpdate);

        UUID userId = (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        PatternServiceDto patternUpdated = patternService.updatePattern(
                patternMapper.patternToUpdateControllerDtoToService(patternToUpdate),
                userId
        );

        return ResponseEntity.ok(patternMapper.patternServiceDtoToControllerDto(patternUpdated));
    }

    @DeleteMapping("/{patternId}")
    public ResponseEntity<Void> deletePattern(@PathVariable UUID patternId) {
        logger.info("Called deletePattern; id={}", patternId);

        UUID userId = (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        patternService.deletePattern(patternId, userId);

        return ResponseEntity.noContent().build();
    }
}
