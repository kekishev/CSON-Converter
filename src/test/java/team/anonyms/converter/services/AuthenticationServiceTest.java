package team.anonyms.converter.services;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import team.anonyms.converter.dto.service.authentication.AuthenticationServiceDto;
import team.anonyms.converter.dto.service.authentication.CredentialsServiceDto;
import team.anonyms.converter.entities.User;
import team.anonyms.converter.repositories.UserRepository;
import team.anonyms.converter.services.frontend.AuthenticationService;
import team.anonyms.converter.services.frontend.JwtService;
import team.anonyms.converter.dto.service.authentication.PasswordResetServiceDto;
import team.anonyms.converter.entities.codes.EmailVerificationCode;
import team.anonyms.converter.entities.codes.PasswordResetVerificationCode;
import team.anonyms.converter.repositories.codes.EmailVerificationCodeRepository;
import team.anonyms.converter.repositories.codes.PasswordResetVerificationCodeRepository;

import java.time.Instant;
import javax.security.auth.login.CredentialException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

// Update needed
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthenticationService authenticationService;

    @Mock
    private EmailVerificationCodeRepository emailVerificationCodeRepository;

    @Mock
    private PasswordResetVerificationCodeRepository passwordResetVerificationCodeRepository;

    @Test
    void testLogin_MissingCredentials_ThrowsCredentialException() {
        CredentialsServiceDto emptyCredentials = new CredentialsServiceDto(null, null);

        assertThrows(CredentialException.class, () -> {
            // Теперь передаем вторым аргументом null (токен)
            authenticationService.login(emptyCredentials, null);
        });
    }

    @Test
    void testLogin_NonexistentEmail_ThrowsEntityNotFoundException() {
        String email = "test@gmail.com";
        String password = "test_password";
        CredentialsServiceDto credentials = new CredentialsServiceDto(email, password);

        Mockito.when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> authenticationService.login(credentials, null)
        );

        assertTrue(exception.getMessage().contains("User not found"));
    }

    @Test
    void testLogin_NonexistentId_ThrowsEntityNotFoundException() {
        String fakeToken = "fake.jwt.token";
        UUID fakeId = UUID.randomUUID();
        CredentialsServiceDto credentials = new CredentialsServiceDto(null, null);

        Mockito.when(jwtService.isValid(fakeToken)).thenReturn(true);
        Mockito.when(jwtService.extractUserId(fakeToken)).thenReturn(fakeId.toString());
        Mockito.when(userRepository.findById(fakeId)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> authenticationService.login(credentials, fakeToken)
        );

        assertTrue(exception.getMessage().contains("User not found; id=" + fakeId));
    }

    @Test
    void testLogin_WithJwtToken_Success() throws CredentialException {
        String token = "valid.jwt.token";
        UUID userId = UUID.randomUUID();
        CredentialsServiceDto credentials = new CredentialsServiceDto(null, null);

        User mockUser = Mockito.mock(User.class);
        mockUser.setId(userId);
        mockUser.setUsername("testuser");
        mockUser.setEmail("test@gmail.com");

        Mockito.when(jwtService.isValid(token)).thenReturn(true);
        Mockito.when(jwtService.extractUserId(token)).thenReturn(userId.toString());
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        AuthenticationServiceDto result = authenticationService.login(credentials, token);

        assertTrue(result.result().success());
        assertEquals(userId, result.result().userId());
        assertEquals(token, result.jwtToken());
    }

    @Test
    void testLogin_WithEmail_CorrectPassword() throws CredentialException {
        String email = "test@gmail.com";
        String rawPassword = "correct_password";
        String encodedPassword = "encoded_password";
        String newToken = "newly.generated.token";
        UUID userId = UUID.randomUUID();

        CredentialsServiceDto credentials = new CredentialsServiceDto(email, rawPassword);

        User mockUser = Mockito.mock(User.class);
        Mockito.when(mockUser.getId()).thenReturn(userId);
        Mockito.when(mockUser.getUsername()).thenReturn("testuser");
        Mockito.when(mockUser.getEmail()).thenReturn(email);

        Mockito.when(mockUser.getPassword()).thenReturn(encodedPassword);

        Mockito.when(userRepository.findByEmail(email)).thenReturn(Optional.of(mockUser));

        Mockito.when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);
        Mockito.when(jwtService.generate(userId)).thenReturn(newToken);

        AuthenticationServiceDto result = authenticationService.login(credentials, null);

        assertTrue(result.result().success());
        assertEquals(userId, result.result().userId());
        assertEquals(newToken, result.jwtToken());
    }

    @Test
    void testLogin_WithEmail_WrongPassword() throws CredentialException {
        String email = "test@gmail.com";
        String rawPassword = "wrong_password";
        String encodedPassword = "encoded_password";

        CredentialsServiceDto credentials = new CredentialsServiceDto(email, rawPassword);

        User mockUser = Mockito.mock(User.class);

        Mockito.when(mockUser.getPassword()).thenReturn(encodedPassword);

        Mockito.when(userRepository.findByEmail(email)).thenReturn(Optional.of(mockUser));

        Mockito.when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(false);

        AuthenticationServiceDto result = authenticationService.login(credentials, null);

        assertFalse(result.result().success());
        assertNull(result.jwtToken());
    }

    @Test
    void testIsVerified_Success() {
        UUID userId = UUID.randomUUID();
        User mockUser = Mockito.mock(User.class);

        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        Mockito.when(mockUser.getIsVerified()).thenReturn(true);

        Boolean result = authenticationService.isVerified(userId);

        assertTrue(result);
    }

    @Test
    void testIsVerified_UserNotFound_ThrowsException() {
        UUID userId = UUID.randomUUID();
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> authenticationService.isVerified(userId));
    }

    @Test
    void testVerifyEmail_Success() {
        UUID userId = UUID.randomUUID();
        String code = "123456";

        User mockUser = Mockito.mock(User.class);
        EmailVerificationCode mockCode = Mockito.mock(EmailVerificationCode.class);

        Mockito.when(emailVerificationCodeRepository.findByUserId(userId)).thenReturn(Optional.of(mockCode));
        Mockito.when(mockCode.getCode()).thenReturn(code);
        Mockito.when(mockCode.getExpiration()).thenReturn(Instant.now().plusSeconds(3600));
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        Boolean result = authenticationService.verifyEmail(userId, code);

        assertTrue(result);
        Mockito.verify(mockUser).setIsVerified(true);
        Mockito.verify(userRepository).save(mockUser);
        Mockito.verify(emailVerificationCodeRepository).delete(mockCode);
    }

    @Test
    void testVerifyEmail_CodeNotFound_ThrowsException() {
        UUID userId = UUID.randomUUID();

        Mockito.when(emailVerificationCodeRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> authenticationService.verifyEmail(userId, "123456"));
    }

    @Test
    void testVerifyEmail_WrongCode_ReturnsFalse() {
        UUID userId = UUID.randomUUID();
        EmailVerificationCode mockCode = Mockito.mock(EmailVerificationCode.class);

        Mockito.when(emailVerificationCodeRepository.findByUserId(userId)).thenReturn(Optional.of(mockCode));
        Mockito.when(mockCode.getCode()).thenReturn("654321");

        Boolean result = authenticationService.verifyEmail(userId, "123456");

        assertFalse(result);
        Mockito.verify(userRepository, Mockito.never()).save(any());
    }

    @Test
    void testVerifyPasswordReset_Success() {
        PasswordResetServiceDto dto = new PasswordResetServiceDto("test@gmail.com", "123456", "newPassword");
        UUID userId = UUID.randomUUID();

        User mockUser = Mockito.mock(User.class);
        PasswordResetVerificationCode mockCode = Mockito.mock(PasswordResetVerificationCode.class);

        Mockito.when(userRepository.findByEmail(dto.email())).thenReturn(Optional.of(mockUser));
        Mockito.when(mockUser.getId()).thenReturn(userId);
        Mockito.when(passwordResetVerificationCodeRepository.findByUserId(userId)).thenReturn(Optional.of(mockCode));
        Mockito.when(mockCode.getCode()).thenReturn(dto.verificationCode());
        Mockito.when(mockCode.getExpiration()).thenReturn(Instant.now().plusSeconds(3600));
        Mockito.when(passwordEncoder.encode(dto.newPassword())).thenReturn("encodedNewPassword");

        Boolean result = authenticationService.verifyPasswordReset(dto);

        assertTrue(result);
        Mockito.verify(mockUser).setPassword("encodedNewPassword");
        Mockito.verify(userRepository).save(mockUser);
        Mockito.verify(passwordResetVerificationCodeRepository).delete(mockCode);
    }

    @Test
    void testVerifyPasswordReset_UserNotFound_ThrowsException() {
        PasswordResetServiceDto dto = new PasswordResetServiceDto("test@gmail.com", "123456", "newPassword");

        Mockito.when(userRepository.findByEmail(dto.email())).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> authenticationService.verifyPasswordReset(dto));
    }

    @Test
    void testVerifyPasswordReset_CodeNotFound_ThrowsException() {
        PasswordResetServiceDto dto = new PasswordResetServiceDto("test@gmail.com", "123456", "newPassword");
        UUID userId = UUID.randomUUID();
        User mockUser = Mockito.mock(User.class);

        Mockito.when(userRepository.findByEmail(dto.email())).thenReturn(Optional.of(mockUser));
        Mockito.when(mockUser.getId()).thenReturn(userId);
        Mockito.when(passwordResetVerificationCodeRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> authenticationService.verifyPasswordReset(dto));
    }

    @Test
    void testVerifyPasswordReset_WrongCode_ReturnsFalse() {
        PasswordResetServiceDto dto = new PasswordResetServiceDto("test@gmail.com", "123456", "newPassword");
        UUID userId = UUID.randomUUID();

        User mockUser = Mockito.mock(User.class);
        PasswordResetVerificationCode mockCode = Mockito.mock(PasswordResetVerificationCode.class);

        Mockito.when(userRepository.findByEmail(dto.email())).thenReturn(Optional.of(mockUser));
        Mockito.when(mockUser.getId()).thenReturn(userId);
        Mockito.when(passwordResetVerificationCodeRepository.findByUserId(userId)).thenReturn(Optional.of(mockCode));
        Mockito.when(mockCode.getCode()).thenReturn("wrongCode");

        Boolean result = authenticationService.verifyPasswordReset(dto);

        assertFalse(result);
        Mockito.verify(userRepository, Mockito.never()).save(any());
    }
}