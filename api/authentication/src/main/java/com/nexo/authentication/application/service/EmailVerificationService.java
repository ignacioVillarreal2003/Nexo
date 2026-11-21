package com.nexo.authentication.application.service;

import com.nexo.authentication.domain.entity.AuditLog;
import com.nexo.authentication.domain.config.ApplicationProperties;
import com.nexo.authentication.domain.entity.EmailVerificationToken;
import com.nexo.authentication.domain.entity.User;
import com.nexo.authentication.domain.exception.BadRequestException;
import com.nexo.authentication.domain.exception.InvalidTokenException;
import com.nexo.authentication.domain.exception.ResourceNotFoundException;
import com.nexo.authentication.infrastructure.messaging.UserEventPublisher;
import com.nexo.authentication.infrastructure.repository.EmailVerificationTokenRepository;
import com.nexo.authentication.infrastructure.repository.UserRepository;
import com.nexo.authentication.infrastructure.service.EmailService;
import com.nexo.authentication.domain.util.AuditActions;
import com.nexo.authentication.domain.util.TokenHasher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationProperties properties;
    private final UserEventPublisher userEventPublisher;

    @Transactional
    public void sendVerificationToken(User user) {
        tokenRepository.invalidateAllByUserId(user.getId(), Instant.now());
        String rawToken = TokenHasher.generateToken();
        tokenRepository.save(EmailVerificationToken.create(
                user, TokenHasher.sha256Hex(rawToken),
                Instant.now().plus(properties.security().emailVerificationTokenTtl())));
        emailService.sendVerificationEmail(user, rawToken);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        EmailVerificationToken token = findUsableToken(rawToken);
        User user = token.getUser();
        user.verifyEmail();
        token.markUsed();
        auditService.success(AuditActions.EMAIL_VERIFIED, user.getId(), user.getEmail());
    }

    @Transactional
    public void resendVerification(String email) {
        userRepository.findByEmailIgnoreCase(email)
                .filter(user -> !user.isEmailVerified())
                .ifPresentOrElse(user -> {
                    sendVerificationToken(user);
                    auditService.success(AuditActions.VERIFICATION_RESENT, user.getId(), user.getEmail());
                }, () -> log.debug("Verification resend requested for unknown or verified email"));
    }

    @Transactional
    public void adminResendVerification(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (user.isEmailVerified()) {
            throw new BadRequestException("Email is already verified");
        }
        sendVerificationToken(user);
        auditService.record(null, null, AuditActions.VERIFICATION_RESENT, "User", user.getId().toString(),
                AuditLog.Result.SUCCESS, null);
    }

    @Transactional
    public void adminVerifyEmail(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (user.isEmailVerified()) {
            throw new BadRequestException("Email is already verified");
        }
        user.verifyEmail();
        auditService.record(null, null, AuditActions.EMAIL_VERIFIED, "User", user.getId().toString(),
                AuditLog.Result.SUCCESS, null);
    }

    @Transactional
    public void adminUnverifyEmail(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (!user.isEmailVerified()) {
            throw new BadRequestException("Email is already unverified");
        }
        user.unverifyEmail();
        auditService.record(null, null, AuditActions.EMAIL_UNVERIFIED_BY_ADMIN, "User", user.getId().toString(),
                AuditLog.Result.SUCCESS, null);
    }

    private EmailVerificationToken findUsableToken(String rawToken) {
        EmailVerificationToken token = tokenRepository.findByTokenHash(TokenHasher.sha256Hex(rawToken))
                .orElseThrow(() -> new InvalidTokenException("Invalid or expired token"));
        if (!token.isUsable()) {
            throw new InvalidTokenException("Invalid or expired token");
        }
        return token;
    }
}
