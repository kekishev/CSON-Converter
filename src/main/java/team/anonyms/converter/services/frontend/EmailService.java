package team.anonyms.converter.services.frontend;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import team.anonyms.converter.entities.User;
import team.anonyms.converter.entities.codes.EmailVerificationCode;
import team.anonyms.converter.entities.codes.PasswordResetVerificationCode;
import team.anonyms.converter.exceptions.email.EmailAlreadyVerifiedException;
import team.anonyms.converter.repositories.UserRepository;
import team.anonyms.converter.repositories.codes.EmailVerificationCodeRepository;
import team.anonyms.converter.repositories.codes.PasswordResetVerificationCodeRepository;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/*
TODO: validate whether user already has a verification code while sending code. If they have then return an exception.
 */
@Service
public class EmailService {
    // Symbols for generating random codes
    private static final String SYMBOLS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JavaMailSender mailSender;
    private final EmailVerificationCodeRepository emailVerificationCodeRepository;
    private final PasswordResetVerificationCodeRepository passwordResetVerificationCodeRepository;
    private final UserRepository userRepository;

    @Value("${app.mail.from}")
    private String emailFrom;

    public EmailService(
            JavaMailSender javaMailSender,
            EmailVerificationCodeRepository emailVerificationCodeRepository,
            PasswordResetVerificationCodeRepository passwordResetVerificationCodeRepository,
            UserRepository userRepository
    ) {
        this.mailSender = javaMailSender;
        this.emailVerificationCodeRepository = emailVerificationCodeRepository;
        this.passwordResetVerificationCodeRepository = passwordResetVerificationCodeRepository;
        this.userRepository = userRepository;
    }

    public void sendEmailVerificationCode(UUID userId) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isEmpty()) {
            throw new EntityNotFoundException("User not found; id=" + userId);
        }

        User user = userOptional.get();
        if (user.getIsVerified()) {
            throw new EmailAlreadyVerifiedException("Email already verified; userId=" + user.getId());
        }

        sendEmailVerificationCode(user);
    }

    public void sendEmailVerificationCode(User receiver) {
        Optional<EmailVerificationCode> emailVerificationCodeOptional =
                emailVerificationCodeRepository.findByUserId(receiver.getId());
        emailVerificationCodeOptional.ifPresent(emailVerificationCodeRepository::delete);

        String emailVerificationCode = generateVerificationCode();
        saveEmailVerificationCode(emailVerificationCode, receiver);

        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom(emailFrom);
        mailMessage.setTo(receiver.getEmail());
        mailMessage.setSubject("Verification code for CSON");
        mailMessage.setText("Your code is: " + emailVerificationCode);

        mailSender.send(mailMessage);
    }

    public void sendPasswordResetVerificationCode(String email) {
        Optional<User> userOptional = userRepository.findByEmail(email);
        if (userOptional.isEmpty()) {
            throw new EntityNotFoundException("User not found");
        }

        sendPasswordResetVerificationCode(userOptional.get());
    }

    public void sendPasswordResetVerificationCode(User receiver) {
        Optional<PasswordResetVerificationCode> passwordResetVerificationCodeOptional =
                passwordResetVerificationCodeRepository.findByUserId(receiver.getId());
        passwordResetVerificationCodeOptional.ifPresent(passwordResetVerificationCodeRepository::delete);

        String emailVerificationCode = generateVerificationCode();
        savePasswordResetVerificationCode(emailVerificationCode, receiver);

        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setFrom(emailFrom);
        mailMessage.setTo(receiver.getEmail());
        mailMessage.setSubject("Verification code for CSON");
        mailMessage.setText("Your code is: " + emailVerificationCode);

        mailSender.send(mailMessage);
    }

    private String generateVerificationCode() {
        StringBuilder code = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            code.append(SYMBOLS.charAt(RANDOM.nextInt(SYMBOLS.length())));
        }

        return code.toString();
    }

    private void saveEmailVerificationCode(String code, User user) {
        EmailVerificationCode emailVerificationCode = EmailVerificationCode.builder()
                .code(code)
                .expiration(Instant.now().plus(15, ChronoUnit.MINUTES))
                .user(user)
                .build();

        emailVerificationCodeRepository.save(emailVerificationCode);
    }

    private void savePasswordResetVerificationCode(String code, User user) {
        PasswordResetVerificationCode passwordResetVerificationCode = PasswordResetVerificationCode.builder()
                .code(code)
                .expiration(Instant.now().plus(15, ChronoUnit.MINUTES))
                .user(user)
                .build();

        passwordResetVerificationCodeRepository.save(passwordResetVerificationCode);
    }
}
