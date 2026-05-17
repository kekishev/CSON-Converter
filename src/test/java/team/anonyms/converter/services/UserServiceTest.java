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
import team.anonyms.converter.dto.service.user.UserServiceDto;
import team.anonyms.converter.dto.service.user.UserToRegisterServiceDto;
import team.anonyms.converter.dto.service.user.UserToUpdateServiceDto;
import team.anonyms.converter.entities.User;
import team.anonyms.converter.exceptions.email.EmailAlreadyExistsException;
import team.anonyms.converter.mappers.UserMapper;
import team.anonyms.converter.repositories.ModificationRepository;
import team.anonyms.converter.repositories.PatternRepository;
import team.anonyms.converter.repositories.UserRepository;
import team.anonyms.converter.repositories.codes.EmailVerificationCodeRepository;
import team.anonyms.converter.repositories.codes.PasswordResetVerificationCodeRepository;
import team.anonyms.converter.services.frontend.EmailService;
import team.anonyms.converter.services.frontend.JwtService;
import team.anonyms.converter.services.frontend.UserService;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private JwtService jwtService;
    @Mock
    private EmailService emailService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailVerificationCodeRepository emailVerificationCodeRepository;
    @Mock
    private PatternRepository patternRepository;
    @Mock
    private ModificationRepository modificationRepository;
    @Mock
    private PasswordResetVerificationCodeRepository passwordResetVerificationCodeRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void testRegister_Success() {
        UserToRegisterServiceDto dto = new UserToRegisterServiceDto("user", "test@gmail.com", "pass");
        UUID userId = UUID.randomUUID();

        User mockUser = Mockito.mock(User.class);
        Mockito.when(mockUser.getId()).thenReturn(userId);
        Mockito.when(mockUser.getUsername()).thenReturn("user");
        Mockito.when(mockUser.getEmail()).thenReturn("test@gmail.com");

        Mockito.when(userRepository.findByEmail(dto.email())).thenReturn(Optional.empty());
        Mockito.when(userMapper.userToRegisterServiceDtoToEntity(dto)).thenReturn(mockUser);
        Mockito.when(passwordEncoder.encode(dto.password())).thenReturn("encoded_pass");
        Mockito.when(userRepository.save(mockUser)).thenReturn(mockUser);
        Mockito.when(jwtService.generate(userId)).thenReturn("jwt.token.here");

        AuthenticationServiceDto result = userService.register(dto);

        assertTrue(result.result().success());
        assertEquals("jwt.token.here", result.jwtToken());
    }

    @Test
    void testRegister_ThrowsEmailExistsException() {
        UserToRegisterServiceDto dto = new UserToRegisterServiceDto("user", "exist@gmail.com", "pass");

        User mockUser = Mockito.mock(User.class);
        Mockito.when(userRepository.findByEmail(dto.email())).thenReturn(Optional.of(mockUser));

        assertThrows(EmailAlreadyExistsException.class, () -> userService.register(dto));
    }

    @Test
    void testUpdateUser_Success() {
        UUID userId = UUID.randomUUID();
        UserToUpdateServiceDto updateDto = new UserToUpdateServiceDto(
                "new_user",
                "newpass"
        );

        User mockUser = Mockito.mock(User.class);
        UserServiceDto responseDto = new UserServiceDto(
                userId,
                "new_user",
                "new@gmail.com",
                true
        );

        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        Mockito.when(passwordEncoder.encode("newpass")).thenReturn("encoded_newpass");
        Mockito.when(userMapper.userToServiceDto(mockUser)).thenReturn(responseDto);

        UserServiceDto result = userService.updateUser(updateDto, userId);

        assertNotNull(result);
        assertEquals("new_user", result.username());

        Mockito.verify(mockUser).setUsername("new_user");
        Mockito.verify(mockUser).setPassword("encoded_newpass");

        Mockito.verify(userRepository).save(mockUser);
    }

    @Test
    void testUpdateUser_ThrowsEntityNotFound() {
        UUID userId = UUID.randomUUID();
        UserToUpdateServiceDto updateDto = new UserToUpdateServiceDto(
                "name",
                "МЕГАНАИКРУТЕЙШИЙСИГМАПАССВОРД"
        );

        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> userService.updateUser(updateDto, userId));
    }

    @Test
    void testUpdateEmail_Success() {
        UUID userId = UUID.randomUUID();
        String newEmail = "new_email@gmail.com";

        User mockUser = Mockito.mock(User.class);
        UserServiceDto responseDto = new UserServiceDto(
                userId,
                "username",
                newEmail,
                false
        );

        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        Mockito.when(userRepository.findByEmail(newEmail)).thenReturn(Optional.empty());
        Mockito.when(userMapper.userToServiceDto(mockUser)).thenReturn(responseDto);

        UserServiceDto result = userService.updateEmail(newEmail, userId);

        assertNotNull(result);
        assertEquals(newEmail, result.email());
        assertEquals(false, result.isVerified());

        Mockito.verify(mockUser).setEmail(newEmail);
        Mockito.verify(mockUser).setIsVerified(false);
        Mockito.verify(userRepository).save(mockUser);
        Mockito.verify(emailService).sendEmailVerificationCode(mockUser);
    }

    @Test
    void testUpdateEmail_ThrowsEmailAlreadyExists() {
        UUID userId = UUID.randomUUID();
        String takenEmail = "taken_email@gmail.com";

        User mockUser = Mockito.mock(User.class);
        User anotherUser = Mockito.mock(User.class);

        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));
        Mockito.when(userRepository.findByEmail(takenEmail)).thenReturn(Optional.of(anotherUser));

        EmailAlreadyExistsException exception = assertThrows(
                EmailAlreadyExistsException.class,
                () -> userService.updateEmail(takenEmail, userId)
        );

        assertTrue(exception.getMessage().contains("Email already exists"));

        Mockito.verify(userRepository, Mockito.never()).save(any());
        Mockito.verify(emailService, Mockito.never()).sendEmailVerificationCode(any(User.class));
    }

    @Test
    void testUpdateEmail_ThrowsEntityNotFound() {
        UUID userId = UUID.randomUUID();
        String newEmail = "new_email@gmail.com";

        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.empty());

        EntityNotFoundException exception = assertThrows(
                EntityNotFoundException.class,
                () -> userService.updateEmail(newEmail, userId)
        );

        assertTrue(exception.getMessage().contains("User not found"));

        Mockito.verify(userRepository, Mockito.never()).save(any());
    }

    @Test
    void testDeleteUser_Success() {
        UUID userId = UUID.randomUUID();

        User mockUser = Mockito.mock(User.class);

        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        userService.deleteUser(userId);

        Mockito.verify(modificationRepository).deleteAllByUserId(userId);
        Mockito.verify(patternRepository).deleteAllByUserId(userId);
        Mockito.verify(emailVerificationCodeRepository).deleteByUserId(userId);
        Mockito.verify(passwordResetVerificationCodeRepository).deleteByUserId(userId);
        Mockito.verify(userRepository).delete(mockUser);
    }

    @Test
    void testDeleteUser_ThrowsEntityNotFound() {
        UUID userId = UUID.randomUUID();
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> userService.deleteUser(userId));
    }
}