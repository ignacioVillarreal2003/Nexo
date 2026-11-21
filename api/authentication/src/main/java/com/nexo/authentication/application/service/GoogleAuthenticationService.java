package com.nexo.authentication.application.service;

import com.nexo.authentication.domain.entity.AuditLog;
import com.nexo.authentication.domain.dto.request.GoogleLoginRequest;
import com.nexo.authentication.domain.dto.response.AuthenticationResponse;
import com.nexo.authentication.domain.entity.OAuthAccount;
import com.nexo.authentication.domain.entity.User;
import com.nexo.authentication.domain.entity.UserSession;
import com.nexo.authentication.domain.entity.enums.AuthenticationProvider;
import com.nexo.authentication.domain.exception.AccountDisabledException;
import com.nexo.authentication.domain.exception.AccountLockedException;
import com.nexo.authentication.domain.exception.DuplicateResourceException;
import com.nexo.authentication.domain.exception.InvalidTokenException;
import com.nexo.authentication.domain.exception.OperationNotAllowedException;
import com.nexo.authentication.domain.exception.ResourceNotFoundException;
import com.nexo.authentication.application.mapper.UserMapper;
import com.nexo.authentication.infrastructure.observability.AuthenticationMetrics;
import com.nexo.authentication.infrastructure.repository.OAuthAccountRepository;
import com.nexo.authentication.infrastructure.repository.UserRepository;
import com.nexo.authentication.infrastructure.security.oauth2.GoogleTokenVerifier;
import com.nexo.authentication.infrastructure.security.oauth2.GoogleUserInfo;
import com.nexo.authentication.infrastructure.service.OneTimeCodeService;
import com.nexo.authentication.domain.util.AuditActions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleAuthenticationService {

    private final GoogleTokenVerifier googleTokenVerifier;
    private final UserRepository userRepository;
    private final OAuthAccountRepository oauthAccountRepository;
    private final TokenService tokenService;
    private final LoginAttemptService loginAttemptService;
    private final AuditService auditService;
    private final UserMapper userMapper;
    private final OneTimeCodeService oneTimeCodeService;
    private final AuthenticationMetrics authenticationMetrics;
    private final UserProvisioner userProvisioner;

    @Transactional
    public AuthenticationResponse login(GoogleLoginRequest request, String ip, String userAgent) {
        GoogleUserInfo info = googleTokenVerifier.verify(request.idToken());
        User user = provisionUser(info);

        assertLoginAllowed(user, ip, userAgent);

        loginAttemptService.onSuccessfulLogin(user);
        loginAttemptService.record(user.getEmail(), ip, userAgent, true, null);

        UserSession session = tokenService.createSession(user, ip, userAgent, request.deviceName(), request.rememberMe());
        TokenService.IssuedTokens tokens = tokenService.issue(user, session, ip, userAgent);

        auditService.success(AuditActions.LOGIN_GOOGLE, user.getId(), user.getEmail());
        authenticationMetrics.loginSucceeded(AuthenticationMetrics.METHOD_GOOGLE);
        User withAuthorities = userRepository.findByIdWithAuthorities(user.getId()).orElse(user);
        return AuthenticationResponse.of(tokens.accessToken(), tokens.rawRefreshToken(), tokens.expiresInSeconds(),
                userMapper.toResponse(withAuthorities));
    }

    @Transactional
    public User provisionUser(GoogleUserInfo info) {
        if (!info.emailVerified()) {
            throw new InvalidTokenException("Google account email is not verified");
        }

        OAuthAccount account = oauthAccountRepository
                .findByProviderAndProviderUserId(AuthenticationProvider.GOOGLE, info.subject())
                .orElse(null);

        if (account != null) {
            refreshProviderData(account, info);
            return account.getUser();
        }

        User user = userRepository.findByEmailIgnoreCase(info.email())
                .orElseGet(() -> registerNewGoogleUser(info));

        oauthAccountRepository.save(OAuthAccount.create(
                user, AuthenticationProvider.GOOGLE, info.subject(), info.email(),
                (info.firstName() + " " + info.lastName()).trim()));

        return user;
    }

    @Transactional
    public AuthenticationResponse exchangeOneTimeCode(String code, String ip, String userAgent) {
        UUID userId = oneTimeCodeService.consume(code);
        User user = userRepository.findByIdWithAuthorities(userId)
                .orElseThrow(() -> new InvalidTokenException("User behind the authorization code no longer exists"));

        assertLoginAllowed(user, ip, userAgent);

        loginAttemptService.onSuccessfulLogin(user);
        UserSession session = tokenService.createSession(user, ip, userAgent, null, false);
        TokenService.IssuedTokens tokens = tokenService.issue(user, session, ip, userAgent);
        auditService.success(AuditActions.LOGIN_GOOGLE, user.getId(), user.getEmail());
        authenticationMetrics.loginSucceeded(AuthenticationMetrics.METHOD_GOOGLE);
        return AuthenticationResponse.of(tokens.accessToken(), tokens.rawRefreshToken(), tokens.expiresInSeconds(),
                userMapper.toResponse(user));
    }

    @Transactional
    public void link(UUID userId, String idToken) {
        GoogleUserInfo info = googleTokenVerifier.verify(idToken);
        if (!info.emailVerified()) {
            throw new InvalidTokenException("Google account email is not verified");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (oauthAccountRepository.existsByUserIdAndProvider(userId, AuthenticationProvider.GOOGLE)) {
            throw new DuplicateResourceException("A Google account is already linked to this user");
        }
        oauthAccountRepository.findByProviderAndProviderUserId(AuthenticationProvider.GOOGLE, info.subject())
                .ifPresent(existing -> {
                    throw new DuplicateResourceException("This Google account is linked to another user");
                });

        oauthAccountRepository.save(OAuthAccount.create(
                user, AuthenticationProvider.GOOGLE, info.subject(), info.email(),
                (info.firstName() + " " + info.lastName()).trim()));

        auditService.success(AuditActions.GOOGLE_LINKED, userId, user.getEmail());
    }

    @Transactional
    public void unlink(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        OAuthAccount account = oauthAccountRepository.findByUserIdAndProvider(userId, AuthenticationProvider.GOOGLE)
                .orElseThrow(() -> new ResourceNotFoundException("No Google account is linked to this user"));

        if (user.getPassword() == null) {
            throw new OperationNotAllowedException(
                    "Set a password before unlinking Google, otherwise you would lose access to the account");
        }
        oauthAccountRepository.delete(account);
        auditService.record(null, null, AuditActions.GOOGLE_UNLINKED, "User", userId.toString(), AuditLog.Result.SUCCESS,
                null);
    }

    private void assertLoginAllowed(User user, String ip, String userAgent) {
        if (user.isLocked()) {
            recordFailedLogin(user, ip, userAgent, "ACCOUNT_LOCKED");
            throw new AccountLockedException("Account is temporarily locked. Try again later.");
        }
        if (!user.isActive()) {
            recordFailedLogin(user, ip, userAgent, "ACCOUNT_DISABLED");
            throw new AccountDisabledException();
        }
    }

    private void recordFailedLogin(User user, String ip, String userAgent, String reason) {
        loginAttemptService.record(user.getEmail(), ip, userAgent, false, reason);
        auditService.record(user.getId(), user.getEmail(), AuditActions.LOGIN_FAILED, null, null, AuditLog.Result.FAILURE,
                Map.of("reason", reason, "ip", String.valueOf(ip)));
        authenticationMetrics.loginFailed(AuthenticationMetrics.METHOD_GOOGLE, reason);
    }

    private User registerNewGoogleUser(GoogleUserInfo info) {
        User user = userProvisioner.provision(new UserProvisioner.NewAccount(info.firstName(), info.lastName(),
                info.email(), null, AuthenticationProvider.GOOGLE, true, true, userProvisioner.defaultRoles(), null));
        auditService.success(AuditActions.REGISTER, user.getId(), user.getEmail());
        authenticationMetrics.userRegistered(AuthenticationMetrics.METHOD_GOOGLE);
        return user;
    }

    private void refreshProviderData(OAuthAccount account, GoogleUserInfo info) {
        account.refreshProviderData(info.email(), (info.firstName() + " " + info.lastName()).trim());
    }
}
