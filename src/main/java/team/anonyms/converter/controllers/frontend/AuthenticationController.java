package team.anonyms.converter.controllers.frontend;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import team.anonyms.converter.dto.controller.authentication.AuthenticationControllerDto;
import team.anonyms.converter.dto.controller.authentication.CredentialsControllerDto;
import team.anonyms.converter.dto.controller.authentication.LoginResultControllerDto;
import team.anonyms.converter.dto.controller.authentication.PasswordResetControllerDto;
import team.anonyms.converter.mappers.AuthenticationMapper;
import team.anonyms.converter.services.frontend.AuthenticationService;
import team.anonyms.converter.services.frontend.EmailService;

import javax.security.auth.login.CredentialException;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@SuppressWarnings(value = {"DataFlowIssue"})
public class AuthenticationController {
    private static final Logger logger = LoggerFactory.getLogger(AuthenticationController.class);

    private final AuthenticationService authenticationService;
    private final EmailService emailService;
    private final AuthenticationMapper authenticationMapper;

    public AuthenticationController(
            AuthenticationService authenticationService,
            EmailService emailService,
            AuthenticationMapper authenticationMapper
    ) {
        this.authenticationService = authenticationService;
        this.emailService = emailService;
        this.authenticationMapper = authenticationMapper;
    }

    /**
     * Creates new cookie with {@code jwtToken}.
     *
     * @param jwtToken JWT token.
     * @param maxAgeSeconds the expiration time of {@link ResponseCookie}.
     */
    public static ResponseCookie createJwtCookie(String jwtToken, int maxAgeSeconds) {
        return ResponseCookie.from("jwtToken", jwtToken)
                .path("/")
                .maxAge(maxAgeSeconds)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .build();
    }

    /*
     * TODO: carry logic of refreshing session to new different API endpoint like /me
     */
    @PostMapping
    public ResponseEntity<LoginResultControllerDto> login(
            @RequestBody CredentialsControllerDto credentials,
            @CookieValue(value = "jwtToken", required = false) String jwtToken,
            HttpServletResponse response
    ) throws CredentialException {
        logger.info("Called login");

        AuthenticationControllerDto result = authenticationMapper.authenticationServiceDtoToControllerDto(
                authenticationService.login(
                        authenticationMapper.credentialsControllerDtoToService(credentials),
                        jwtToken
                )
        );

        response.addHeader(HttpHeaders.SET_COOKIE, createJwtCookie(result.jwtToken(), 14400).toString());
        return ResponseEntity.ok(result.result());
    }

    @PostMapping("/email/resending")
    public ResponseEntity<Void> sendEmailVerificationCode() {
        logger.info("Called sendEmailVerificationCode");

        UUID userId = (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        emailService.sendEmailVerificationCode(userId);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/email/verification")
    public ResponseEntity<Boolean> verifyEmail(@RequestBody String verificationCode) {
        logger.info("Called verifyEmail");

        UUID userId = (UUID) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        return ResponseEntity.ok(authenticationService.verifyEmail(userId, verificationCode));
    }

    @PostMapping("/password/reset")
    public ResponseEntity<Void> sendPasswordResetVerificationCode(@RequestBody String email) {
        logger.info("Called sendVerificationCodeForPasswordReset");

        emailService.sendPasswordResetVerificationCode(email);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/password/verification")
    public ResponseEntity<Boolean> verifyPasswordReset(
            @RequestBody PasswordResetControllerDto passwordResetControllerDto
    ) {
        logger.info("Called verifyPasswordReset");

        return ResponseEntity.ok(authenticationService.verifyPasswordReset(
                authenticationMapper.passwordResetControllerDtoToService(passwordResetControllerDto))
        );
    }

    @DeleteMapping
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        logger.info("Called logout");

        response.addHeader(HttpHeaders.SET_COOKIE, createJwtCookie("", 0).toString());
        return ResponseEntity.noContent().build();
    }
}
