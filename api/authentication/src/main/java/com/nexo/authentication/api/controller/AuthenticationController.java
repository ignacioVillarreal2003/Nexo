package com.nexo.authentication.api.controller;

import com.nexo.authentication.domain.dto.request.ChangePasswordRequest;
import com.nexo.authentication.domain.dto.request.DeleteAccountRequest;
import com.nexo.authentication.domain.dto.request.ForgotPasswordRequest;
import com.nexo.authentication.domain.dto.request.GoogleLinkRequest;
import com.nexo.authentication.domain.dto.request.GoogleLoginRequest;
import com.nexo.authentication.domain.dto.request.LoginRequest;
import com.nexo.authentication.domain.dto.request.RefreshTokenRequest;
import com.nexo.authentication.domain.dto.request.RegisterRequest;
import com.nexo.authentication.domain.dto.request.ResendVerificationRequest;
import com.nexo.authentication.domain.dto.request.ResetPasswordRequest;
import com.nexo.authentication.domain.dto.request.VerifyEmailRequest;
import com.nexo.authentication.domain.dto.response.ApiResponse;
import com.nexo.authentication.domain.dto.response.AuthenticationResponse;
import com.nexo.authentication.domain.dto.response.TokenResponse;
import com.nexo.authentication.domain.dto.response.UserResponse;
import com.nexo.authentication.application.security.CustomUserDetails;
import com.nexo.authentication.application.service.AuthenticationService;
import com.nexo.authentication.application.service.EmailVerificationService;
import com.nexo.authentication.application.service.GoogleAuthenticationService;
import com.nexo.authentication.application.service.PasswordService;
import com.nexo.authentication.application.service.ServiceClientService;
import com.nexo.authentication.api.util.HttpRequestUtilities;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/authentication")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Registration, login, tokens and account lifecycle")
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final GoogleAuthenticationService googleAuthenticationService;
    private final PasswordService passwordService;
    private final EmailVerificationService emailVerificationService;
    private final ServiceClientService serviceClientService;

    @PostMapping(value = "/token", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "OAuth2 client_credentials token grant (machine-to-machine)",
            description = "Standard RFC 6749 form-encoded request for registered service clients. "
                    + "Returns a plain OAuth2 token response (no API envelope) so OAuth2 client "
                    + "libraries work out of the box.")
    public ResponseEntity<TokenResponse> token(@RequestParam("grant_type") String grantType,
                                               @RequestParam("client_id") String clientId,
                                               @RequestParam("client_secret") String clientSecret) {
        return ResponseEntity.ok(serviceClientService.issueToken(grantType, clientId, clientSecret));
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new account",
            description = "Creates a local account and sends an email verification link.")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse user = authenticationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(user, "Account created. Please verify your email address."));
    }

    @PostMapping("/login")
    @Operation(summary = "Login with email and password")
    public ResponseEntity<ApiResponse<AuthenticationResponse>> login(@Valid @RequestBody LoginRequest request,
                                                           HttpServletRequest http) {
        AuthenticationResponse response = authenticationService.login(request,
                HttpRequestUtilities.clientIp(http), HttpRequestUtilities.userAgent(http));
        return ResponseEntity.ok(ApiResponse.ok(response, "Login successful"));
    }

    @PostMapping("/google")
    @Operation(summary = "Login with a Google ID token",
            description = "Verifies the Google ID token, registers or links the account automatically "
                    + "and returns a first-party token pair.")
    public ResponseEntity<ApiResponse<AuthenticationResponse>> googleLogin(@Valid @RequestBody GoogleLoginRequest request,
                                                                 HttpServletRequest http) {
        AuthenticationResponse response = googleAuthenticationService.login(request,
                HttpRequestUtilities.clientIp(http), HttpRequestUtilities.userAgent(http));
        return ResponseEntity.ok(ApiResponse.ok(response, "Login successful"));
    }

    @PostMapping("/oauth2/exchange")
    @Operation(summary = "Exchange a browser OAuth2 one-time code for tokens")
    public ResponseEntity<ApiResponse<AuthenticationResponse>> exchangeOAuth2Code(@RequestBody Map<String, String> body,
                                                                        HttpServletRequest http) {
        AuthenticationResponse response = googleAuthenticationService.exchangeOneTimeCode(body.getOrDefault("code", ""),
                HttpRequestUtilities.clientIp(http), HttpRequestUtilities.userAgent(http));
        return ResponseEntity.ok(ApiResponse.ok(response, "Login successful"));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the refresh token",
            description = "Returns a new access/refresh token pair. Reusing an already-rotated "
                    + "refresh token revokes the whole session.")
    public ResponseEntity<ApiResponse<AuthenticationResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                                             HttpServletRequest http) {
        AuthenticationResponse response = authenticationService.refresh(request.refreshToken(),
                HttpRequestUtilities.clientIp(http), HttpRequestUtilities.userAgent(http));
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Logout the current session",
            description = "Blacklists the presented access token and revokes the refresh token session.")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestBody(required = false) RefreshTokenRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authenticationService.logout(authorization, request != null ? request.refreshToken() : null);
        return ResponseEntity.ok(ApiResponse.message("Logged out"));
    }

    @PostMapping("/logout-all")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Logout everywhere", description = "Revokes every session and refresh token of the user.")
    public ResponseEntity<ApiResponse<Void>> logoutAll(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authenticationService.logoutAll(principal.getId(), authorization);
        return ResponseEntity.ok(ApiResponse.message("All sessions revoked"));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password reset email",
            description = "Always returns 200 to prevent account enumeration.")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordService.forgotPassword(request.email());
        return ResponseEntity.ok(ApiResponse.message(
                "If the email exists, a password reset link has been sent"));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset the password with an emailed token",
            description = "Also revokes every active session of the account.")
    public ResponseEntity<ApiResponse<Void>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(ApiResponse.message("Password has been reset"));
    }

    @PostMapping("/change-password")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Change the current user's password",
            description = "Revokes every other session; the current one stays alive.")
    public ResponseEntity<ApiResponse<Void>> changePassword(@AuthenticationPrincipal CustomUserDetails principal,
                                                            @Valid @RequestBody ChangePasswordRequest request) {
        passwordService.changePassword(principal.getId(), principal.getSessionId(), request);
        return ResponseEntity.ok(ApiResponse.message("Password changed"));
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify an email address with an emailed token")
    public ResponseEntity<ApiResponse<Void>> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        emailVerificationService.verifyEmail(request.token());
        return ResponseEntity.ok(ApiResponse.message("Email verified"));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend the verification email",
            description = "Always returns 200 to prevent account enumeration.")
    public ResponseEntity<ApiResponse<Void>> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        emailVerificationService.resendVerification(request.email());
        return ResponseEntity.ok(ApiResponse.message(
                "If the email exists and is unverified, a new verification link has been sent"));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get the authenticated user's profile")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(ApiResponse.ok(authenticationService.me(principal.getId())));
    }

    @DeleteMapping("/account")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete the authenticated user's account",
            description = "Soft-deletes the account and revokes all sessions. Local accounts must "
                    + "confirm with their password.")
    public ResponseEntity<ApiResponse<Void>> deleteAccount(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestBody(required = false) DeleteAccountRequest request,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        authenticationService.deleteAccount(principal.getId(),
                request != null ? request : new DeleteAccountRequest(null), authorization);
        return ResponseEntity.ok(ApiResponse.message("Account deleted"));
    }

    @PostMapping("/google/link")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Link a Google account to the authenticated user")
    public ResponseEntity<ApiResponse<Void>> linkGoogle(@AuthenticationPrincipal CustomUserDetails principal,
                                                        @Valid @RequestBody GoogleLinkRequest request) {
        googleAuthenticationService.link(principal.getId(), request.idToken());
        return ResponseEntity.ok(ApiResponse.message("Google account linked"));
    }

    @DeleteMapping("/google/unlink")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Unlink the Google account",
            description = "Requires the account to have a password so access is not lost.")
    public ResponseEntity<ApiResponse<Void>> unlinkGoogle(@AuthenticationPrincipal CustomUserDetails principal) {
        googleAuthenticationService.unlink(principal.getId());
        return ResponseEntity.ok(ApiResponse.message("Google account unlinked"));
    }
}
