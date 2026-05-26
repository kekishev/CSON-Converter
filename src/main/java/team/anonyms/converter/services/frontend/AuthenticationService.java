package team.anonyms.converter.services.frontend;

import jakarta.persistence.EntityNotFoundException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import team.anonyms.converter.dto.service.authentication.AuthenticationServiceDto;
import team.anonyms.converter.dto.service.authentication.CredentialsServiceDto;
import team.anonyms.converter.dto.service.authentication.LoginResultServiceDto;
import team.anonyms.converter.dto.service.authentication.PasswordResetServiceDto;
import team.anonyms.converter.entities.User;
import team.anonyms.converter.entities.codes.EmailVerificationCode;
import team.anonyms.converter.entities.codes.PasswordResetVerificationCode;
import team.anonyms.converter.repositories.codes.EmailVerificationCodeRepository;
import team.anonyms.converter.repositories.UserRepository;
import team.anonyms.converter.repositories.codes.PasswordResetVerificationCodeRepository;

import javax.security.auth.login.CredentialException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthenticationService {
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final EmailVerificationCodeRepository emailVerificationCodeRepository;
    private final PasswordResetVerificationCodeRepository passwordResetVerificationCodeRepository;

    private final BCryptPasswordEncoder passwordEncoder;

    public AuthenticationService(
            JwtService jwtService,
            UserRepository userRepository,
            EmailVerificationCodeRepository emailVerificationCodeRepository,
            PasswordResetVerificationCodeRepository passwordResetVerificationCodeRepository,
            BCryptPasswordEncoder passwordEncoder
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.emailVerificationCodeRepository = emailVerificationCodeRepository;
        this.passwordResetVerificationCodeRepository = passwordResetVerificationCodeRepository;

        this.passwordEncoder = passwordEncoder;
    }

    public Boolean isVerified(UUID userId) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            throw new EntityNotFoundException("User not found; id=" + userId);
        }

        return userOptional.get().getIsVerified();
    }

    /**
     * @param credentials login credentials.
     * @param jwtToken JWT token extracted from cookie.
     *
     * @throws CredentialException if {@code jwtToken} is null and
     * either {@code credentials.email()} or {@code credentials.password()} is null.
     */
    public AuthenticationServiceDto login(
            CredentialsServiceDto credentials,
            @Nullable String jwtToken
    ) throws CredentialException {
        if (jwtToken == null && (credentials.email() == null || credentials.password() == null)) {
            throw new CredentialException("Login credentials are missing");
        }

        if (jwtToken != null && jwtService.isValid(jwtToken))  {
            return loginByJwtToken(jwtToken);
        }

        return loginByCredentials(credentials, jwtToken);
    }

    public Boolean verifyEmail(UUID userId, String emailVerificationCode) {
        Optional<EmailVerificationCode> emailVerificationCodeOptional =
                emailVerificationCodeRepository.findByUserId(userId);
        if (emailVerificationCodeOptional.isEmpty()) {
            throw new EntityNotFoundException("Email verification code not found; userId=" + userId);
        }

        EmailVerificationCode actualEmailVerificationCode = emailVerificationCodeOptional.get();
        if (emailVerificationCode.equals(actualEmailVerificationCode.getCode())
                && actualEmailVerificationCode.getExpiration().isAfter(Instant.now())) {
            Optional<User> userOptional = userRepository.findById(userId);
            if (userOptional.isEmpty()) {
                throw new EntityNotFoundException("User not found; userId=" + userId);
            }

            User userVerified = userOptional.get();
            userVerified.setIsVerified(true);

            userRepository.save(userVerified);
            emailVerificationCodeRepository.delete(actualEmailVerificationCode);

            return true;
        }

        return false;
    }

    public Boolean verifyPasswordReset(PasswordResetServiceDto passwordResetServiceDto) {
        Optional<User> userOptional = userRepository.findByEmail(passwordResetServiceDto.email());
        if (userOptional.isEmpty()) {
            throw new EntityNotFoundException("User not found");
        }

        User user = userOptional.get();
        Optional<PasswordResetVerificationCode> passwordResetVerificationCodeOptional =
                passwordResetVerificationCodeRepository.findByUserId(user.getId());
        if (passwordResetVerificationCodeOptional.isEmpty()) {
            throw new EntityNotFoundException("Password reset verification code not found; userId=" + user.getId());
        }

        PasswordResetVerificationCode actualPasswordResetVerificationCode = passwordResetVerificationCodeOptional.get();
        if (passwordResetServiceDto.verificationCode().equals(actualPasswordResetVerificationCode.getCode())
                && actualPasswordResetVerificationCode.getExpiration().isAfter(Instant.now())) {
            user.setPassword(passwordEncoder.encode(passwordResetServiceDto.newPassword()));

            userRepository.save(user);
            passwordResetVerificationCodeRepository.delete(actualPasswordResetVerificationCode);

            return true;
        } else {
            return false;
        }
    }

    /**
     * Logs in a user via {@code jwtToken}.
     *
     * <p>
     *     For that this method finds a user by their ID which is extracted from {@code jwtToken}.
     * </p>
     *
     * @param jwtToken JWT token.
     *
     * @throws EntityNotFoundException if a user is not found.
     */
    private @NonNull AuthenticationServiceDto loginByJwtToken(@NonNull String jwtToken) {
        UUID userId = UUID.fromString(jwtService.extractUserId(jwtToken));

        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            throw new EntityNotFoundException("User not found; id=" + userId);
        }

        User user = userOptional.get();
        LoginResultServiceDto result = new LoginResultServiceDto(
                true,
                userId,
                user.getUsername(),
                user.getEmail(),
                user.getIsVerified()
        );

        return new AuthenticationServiceDto(result, jwtToken);
    }

    /**
     * Logs in a user via {@code credentials}.
     *
     * <p>
     *     For that this method finds a user by {@code credentials.email()} and compares passwords.
     * </p>
     *
     * @param credentials user's credentials.
     * @param jwtToken JWT token.
     *
     * @throws EntityNotFoundException if a user is not found.
     */
    private @NonNull AuthenticationServiceDto loginByCredentials(
            @NonNull CredentialsServiceDto credentials,
            @Nullable String jwtToken
    ) {
        String email = credentials.email();
        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isEmpty()) {
            throw new EntityNotFoundException("User not found");
        }

        User user = userOptional.get();
        if (passwordEncoder.matches(credentials.password(), user.getPassword())) {
            String newJwtToken = jwtService.generate(user.getId());
            LoginResultServiceDto result = new LoginResultServiceDto(
                    true,
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getIsVerified()
            );

            return new AuthenticationServiceDto(result, newJwtToken);
        }

        LoginResultServiceDto result = new LoginResultServiceDto(false, null, null, null, null);
        return new AuthenticationServiceDto(result, jwtToken);
    }
}
