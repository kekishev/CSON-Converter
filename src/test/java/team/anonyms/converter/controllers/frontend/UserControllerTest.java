package team.anonyms.converter.controllers.frontend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import team.anonyms.converter.controllers.GlobalExceptionHandler;
import team.anonyms.converter.dto.controller.authentication.AuthenticationControllerDto;
import team.anonyms.converter.dto.controller.authentication.LoginResultControllerDto;
import team.anonyms.converter.dto.controller.user.UserControllerDto;
import team.anonyms.converter.dto.controller.user.UserToRegisterControllerDto;
import team.anonyms.converter.dto.controller.user.UserToUpdateControllerDto;
import team.anonyms.converter.dto.service.authentication.AuthenticationServiceDto;
import team.anonyms.converter.dto.service.authentication.LoginResultServiceDto;
import team.anonyms.converter.dto.service.user.UserServiceDto;
import team.anonyms.converter.dto.service.user.UserToRegisterServiceDto;
import team.anonyms.converter.dto.service.user.UserToUpdateServiceDto;
import team.anonyms.converter.mappers.AuthenticationMapper;
import team.anonyms.converter.mappers.UserMapper;
import team.anonyms.converter.services.frontend.UserService;

import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@ContextConfiguration(classes = {UserController.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private UserService userService;
    @MockitoBean
    private UserMapper userMapper;

    @MockitoBean
    private AuthenticationMapper authenticationMapper;

    @Test
    void testRegister_Success() throws Exception {
        UUID fakeId = UUID.randomUUID();
        String generatedToken = "new.jwt.token";

        UserToRegisterControllerDto requestDto = new UserToRegisterControllerDto(
                "testuser",
                "test@mail.com",
                "password123"
        );

        UserToRegisterServiceDto mockRegisterServiceDto = new UserToRegisterServiceDto(
                "testuser",
                "test@mail.com",
                "password123"
        );

        LoginResultServiceDto mockServiceResult = new LoginResultServiceDto(
                true, fakeId, "testuser", "test@mail.com", false
        );

        LoginResultControllerDto loginResultControllerDto = new LoginResultControllerDto(
                true, fakeId, "testuser", "test@mail.com", false
        );

        AuthenticationControllerDto authenticationControllerDto = new AuthenticationControllerDto(
                loginResultControllerDto, generatedToken
        );

        Mockito.when(userMapper.userToRegisterControllerDtoToService(any(UserToRegisterControllerDto.class)))
                .thenReturn(mockRegisterServiceDto);

        Mockito.when(userService.register(any(UserToRegisterServiceDto.class)))
                .thenReturn(new AuthenticationServiceDto(mockServiceResult, generatedToken));

        Mockito.when(authenticationMapper.authenticationServiceDtoToControllerDto(any(AuthenticationServiceDto.class)))
                .thenReturn(authenticationControllerDto);

        mockMvc.perform(MockMvcRequestBuilders.post("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.username").value("testuser"))
                .andExpect(cookie().value("jwtToken", generatedToken))
                .andExpect(cookie().httpOnly("jwtToken", true))
                .andExpect(cookie().secure("jwtToken", true));
    }

    @Test
    void testUpdateUser_Success() throws Exception {
        UUID userId = UUID.randomUUID();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of())
        );

        UserToUpdateControllerDto requestDto = new UserToUpdateControllerDto("newname", "test password");

        UserToUpdateServiceDto serviceRequestDto = new UserToUpdateServiceDto(
                "newname",
                "test password"
        );

        UserServiceDto serviceResponseDto = new UserServiceDto(
                userId,
                "newname",
                "new@gmail.com",
                true
        );

        UserControllerDto responseDto = new UserControllerDto(
                userId,
                "newname",
                "new@gmail.com",
                true
        );

        Mockito.when(userMapper.userToUpdateControllerDtoToService(any(UserToUpdateControllerDto.class)))
                .thenReturn(serviceRequestDto);

        Mockito.when(userService.updateUser(any(UserToUpdateServiceDto.class), eq(userId)))
                .thenReturn(serviceResponseDto);

        Mockito.when(userMapper.userServiceDtoToControllerDto(any(UserServiceDto.class)))
                .thenReturn(responseDto);

        mockMvc.perform(MockMvcRequestBuilders.put("/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.username").value("newname"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.isVerified").value(true));
    }

    @Test
    void testUpdateEmail_Success() throws Exception {
        UUID userId = UUID.randomUUID();
        String newEmail = "new_test@gmail.com";

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of())
        );

        UserServiceDto serviceResponseDto = new UserServiceDto(
                userId,
                "username",
                newEmail,
                false
        );

        UserControllerDto responseDto = new UserControllerDto(
                userId,
                "username",
                newEmail,
                false
        );

        Mockito.when(userService.updateEmail(anyString(), eq(userId)))
                .thenReturn(serviceResponseDto);

        Mockito.when(userMapper.userServiceDtoToControllerDto(serviceResponseDto))
                .thenReturn(responseDto);

        mockMvc.perform(MockMvcRequestBuilders.put("/users/email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newEmail))
                .andExpect(status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.email").value(newEmail))
                .andExpect(MockMvcResultMatchers.jsonPath("$.isVerified").value(false));
    }

    @Test
    void testDeleteUser_Success() throws Exception {
        UUID userId = UUID.randomUUID();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userId, null, List.of())
        );

        Mockito.doNothing().when(userService).deleteUser(userId);

        mockMvc.perform(MockMvcRequestBuilders.delete("/users"))
                .andExpect(status().isNoContent());
    }
}