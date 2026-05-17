package team.anonyms.converter.controllers.frontend;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import team.anonyms.converter.controllers.GlobalExceptionHandler;
import team.anonyms.converter.dto.controller.authentication.AuthenticationControllerDto;
import team.anonyms.converter.dto.controller.authentication.CredentialsControllerDto;
import team.anonyms.converter.dto.controller.authentication.LoginResultControllerDto;
import team.anonyms.converter.dto.controller.authentication.PasswordResetControllerDto;
import team.anonyms.converter.dto.service.authentication.AuthenticationServiceDto;
import team.anonyms.converter.dto.service.authentication.CredentialsServiceDto;
import team.anonyms.converter.dto.service.authentication.LoginResultServiceDto;
import team.anonyms.converter.dto.service.authentication.PasswordResetServiceDto;
import team.anonyms.converter.mappers.AuthenticationMapper;
import team.anonyms.converter.services.frontend.AuthenticationService;
import team.anonyms.converter.services.frontend.EmailService;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthenticationController.class)
@ContextConfiguration(classes = {AuthenticationController.class, GlobalExceptionHandler.class})
class AuthenticationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthenticationService authenticationService;
    @MockitoBean
    private EmailService emailService;
    @MockitoBean
    private AuthenticationMapper authenticationMapper;

    @Test
    void testLogin_Success() throws Exception {
        UUID fakeUserId = UUID.randomUUID();
        String fakeUsername = "testuser";
        String fakeEmail = "test@mail.com";
        String generatedToken = "valid.jwt.token";

        CredentialsControllerDto requestDto = new CredentialsControllerDto(
                fakeEmail, "password123"
        );

        CredentialsServiceDto mockServiceCredentials = new CredentialsServiceDto(
                fakeEmail, "password123"
        );

        LoginResultServiceDto mockServiceResult = new LoginResultServiceDto(
                true, fakeUserId, fakeUsername, fakeEmail, false
        );

        LoginResultControllerDto loginResultControllerDto = new LoginResultControllerDto(
                true, fakeUserId, fakeUsername, fakeEmail, false
        );

        AuthenticationServiceDto authenticationServiceDto = new AuthenticationServiceDto(
                mockServiceResult, generatedToken
        );

        AuthenticationControllerDto authenticationControllerDto = new AuthenticationControllerDto(
                loginResultControllerDto, generatedToken
        );

        Mockito.when(authenticationMapper.credentialsControllerDtoToService(any(CredentialsControllerDto.class)))
                .thenReturn(mockServiceCredentials);

        Mockito.when(authenticationService.login(any(CredentialsServiceDto.class), eq(null)))
                .thenReturn(authenticationServiceDto);

        Mockito.when(authenticationMapper.authenticationServiceDtoToControllerDto(any(AuthenticationServiceDto.class)))
                .thenReturn(authenticationControllerDto);

        mockMvc.perform(MockMvcRequestBuilders.post("/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.username").value(fakeUsername))
                .andExpect(cookie().value("jwtToken", generatedToken))
                .andExpect(cookie().httpOnly("jwtToken", true))
                .andExpect(cookie().secure("jwtToken", true));
    }

    @Test
    void testLogin_WithExistingCookie_Success() throws Exception {
        UUID fakeUserId = UUID.randomUUID();
        String fakeUsername = "testuser";
        String fakeEmail = "test@mail.com";
        String existingToken = "existing.jwt.token";

        CredentialsControllerDto requestDto = new CredentialsControllerDto(null, null);
        CredentialsServiceDto mockServiceCredentials = new CredentialsServiceDto(null, null);

        LoginResultServiceDto mockServiceResult = new LoginResultServiceDto(
                true,  fakeUserId, fakeUsername, fakeEmail, false
        );

        LoginResultControllerDto responseDto = new LoginResultControllerDto(
                true, fakeUserId, fakeUsername, fakeEmail, false
        );

        Mockito.when(authenticationMapper.credentialsControllerDtoToService(any(CredentialsControllerDto.class)))
                .thenReturn(mockServiceCredentials);

        AuthenticationServiceDto authenticationServiceDto = new AuthenticationServiceDto(mockServiceResult, existingToken);
        AuthenticationControllerDto authenticationControllerDto = new AuthenticationControllerDto(responseDto, existingToken);

        Mockito.when(authenticationService.login(any(CredentialsServiceDto.class), eq(existingToken)))
                .thenReturn(authenticationServiceDto);

        Mockito.when(authenticationMapper.authenticationServiceDtoToControllerDto(any(AuthenticationServiceDto.class)))
                .thenReturn(authenticationControllerDto);

        mockMvc.perform(MockMvcRequestBuilders.post("/auth")
                        .cookie(new Cookie("jwtToken", existingToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(true))
                .andExpect(cookie().value("jwtToken", existingToken));
    }

    @Test
    void testLogin_BadCredentials() throws Exception {
        CredentialsControllerDto requestDto = new CredentialsControllerDto("wrong@mail.com", "wrongpass");
        CredentialsServiceDto mockServiceCredentials = new CredentialsServiceDto("wrong@mail.com", "wrongpass");

        Mockito.when(authenticationMapper.credentialsControllerDtoToService(any(CredentialsControllerDto.class)))
                .thenReturn(mockServiceCredentials);

        Mockito.when(authenticationService.login(any(CredentialsServiceDto.class), eq(null)))
                .thenThrow(new javax.security.auth.login.CredentialException("Invalid credentials"));

        mockMvc.perform(MockMvcRequestBuilders.post("/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testLogout_Success() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/auth"))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("jwtToken", 0))
                .andExpect(cookie().value("jwtToken", ""));
    }

    @Test
    void testVerifyEmail_Success() throws Exception {
        java.util.UUID userId = java.util.UUID.randomUUID();
        String verificationCode = "123456";

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, java.util.List.of())
        );

        Mockito.when(authenticationService.verifyEmail(eq(userId), anyString())).thenReturn(true);

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/email/verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verificationCode))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.content().string("true"));
    }

    @Test
    void testSendPasswordResetVerificationCode_Success() throws Exception {
        String email = "test@gmail.com";

        Mockito.doNothing().when(emailService).sendPasswordResetVerificationCode(anyString());

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(email))
                .andExpect(status().isNoContent());
    }

    @Test
    void testVerifyPasswordReset_Success() throws Exception {
        PasswordResetControllerDto requestDto =
                new PasswordResetControllerDto("test@gmail.com", "123456", "newPassword");

        PasswordResetServiceDto serviceDto =
                new PasswordResetServiceDto("test@gmail.com", "123456", "newPassword");

        Mockito.when(authenticationMapper.passwordResetControllerDtoToService(any(PasswordResetControllerDto.class)))
                .thenReturn(serviceDto);

        Mockito.when(authenticationService.verifyPasswordReset(any(PasswordResetServiceDto.class)))
                .thenReturn(true);

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/password/verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.content().string("true"));
    }
}