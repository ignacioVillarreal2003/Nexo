package com.nexo.authentication.application.service;

import com.nexo.authentication.domain.entity.AuditLog;
import com.nexo.authentication.domain.dto.request.CreateUserRequest;
import com.nexo.authentication.domain.dto.request.PatchUserRequest;
import com.nexo.authentication.domain.dto.response.BulkOperationResponse;
import com.nexo.authentication.domain.dto.response.PageResponse;
import com.nexo.authentication.domain.dto.response.UserResponse;
import com.nexo.authentication.domain.entity.Permission;
import com.nexo.authentication.domain.entity.Role;
import com.nexo.authentication.domain.entity.User;
import com.nexo.authentication.domain.entity.enums.AuthenticationProvider;
import com.nexo.authentication.domain.entity.enums.Theme;
import com.nexo.authentication.domain.entity.enums.ThemeMode;
import com.nexo.authentication.domain.exception.BadRequestException;
import com.nexo.authentication.domain.exception.DuplicateResourceException;
import com.nexo.authentication.domain.exception.OperationNotAllowedException;
import com.nexo.authentication.domain.exception.ResourceNotFoundException;
import com.nexo.authentication.application.mapper.UserMapper;
import com.nexo.authentication.infrastructure.repository.PermissionRepository;
import com.nexo.authentication.infrastructure.repository.RoleRepository;
import com.nexo.authentication.infrastructure.repository.UserRepository;
import com.nexo.authentication.infrastructure.repository.specification.UserSpecifications;
import com.nexo.authentication.application.security.SecurityUtilities;
import com.nexo.authentication.infrastructure.messaging.UserEventPublisher;
import com.nexo.authentication.infrastructure.messaging.UserEventType;
import com.nexo.authentication.infrastructure.repository.OAuthAccountRepository;
import com.nexo.authentication.domain.util.AuditActions;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final Set<String> SUPPORTED_LOCALES = Set.of("es", "en");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final TokenService tokenService;
    private final AuditService auditService;
    private final UserMapper userMapper;
    private final OAuthAccountRepository oauthAccountRepository;
    private final UserEventPublisher userEventPublisher;
    private final UserProvisioner userProvisioner;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(String search,
                                           Boolean active,
                                           Boolean emailVerified,
                                           String role,
                                           Pageable pageable) {
        Specification<User> spec = UserSpecifications.search(search)
                .and(UserSpecifications.hasActive(active))
                .and(UserSpecifications.hasEmailVerified(emailVerified))
                .and(UserSpecifications.hasRole(role));
        return PageResponse.from(userRepository.findAll(spec, pageable), userMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID id) {
        return userMapper.toResponse(loadWithAuthorities(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new DuplicateResourceException("An account with this email already exists");
        }
        User user = userProvisioner.provision(new UserProvisioner.NewAccount(request.firstName(),
                request.lastName(), request.email(), request.password(), AuthenticationProvider.LOCAL,
                request.active() == null || request.active(), Boolean.TRUE.equals(request.emailVerified()),
                request.roles() == null || request.roles().isEmpty()
                        ? userProvisioner.defaultRoles() : resolveRoles(request.roles()),
                null));
        auditService.record(null, null, AuditActions.USER_CREATED, "User", user.getId().toString(),
                AuditLog.Result.SUCCESS, Map.of("email", user.getEmail()));
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse patch(UUID id, PatchUserRequest request) {
        User user = loadWithAuthorities(id);
        Map<String, Object> before = snapshot(user);
        if (StringUtils.hasText(request.email())) {
            applyEmailChange(user, request.email());
        }
        user.updateProfile(
                StringUtils.hasText(request.firstName()) ? request.firstName().trim() : null,
                StringUtils.hasText(request.lastName()) ? request.lastName().trim() : null);
        if (request.active() != null && canChangeActiveFlag(id)) {
            user.updateActiveStatus(request.active());
            if (!request.active()) {
                tokenService.revokeAllForUser(id);
            }
        }
        applyPreferences(user, request.theme(), request.themeMode(), request.locale(), request.timezone(),
                request.hiddenModules());
        if (request.avatarColor() != null) {
            user.getPreferences().updateAvatarColor(request.avatarColor());
        }
        auditService.record(null, null, AuditActions.USER_UPDATED, "User", id.toString(), AuditLog.Result.SUCCESS,
                Map.of("before", before));
        userEventPublisher.publish(UserEventType.USER_UPDATED, user);
        return userMapper.toResponse(user);
    }

    @Transactional
    public void delete(UUID id) {
        User user = load(id);
        UUID currentUserId = SecurityUtilities.getCurrentUserId().orElse(null);
        if (id.equals(currentUserId)) {
            throw new OperationNotAllowedException("Use DELETE /api/v1/authentication/account to delete your own account");
        }
        Map<String, Object> before = snapshot(user);
        tokenService.revokeAllForUser(id);
        String email = user.getEmail();
        oauthAccountRepository.deleteByUserId(id);
        user.softDelete(SecurityUtilities.getCurrentUserEmail().orElse("system"));
        userRepository.save(user);
        auditService.record(null, null, AuditActions.USER_DELETED, "User", id.toString(), AuditLog.Result.SUCCESS,
                Map.of("before", before));
        userEventPublisher.publish(UserEventType.USER_DELETED, id, email);
    }

    @Transactional
    public UserResponse updateRoles(UUID id, Set<String> roleNames) {
        User user = loadWithAuthorities(id);
        Set<String> oldRoleNames = user.getRoles().stream().map(Role::getName).collect(Collectors.toSet());
        user.updateRoles(resolveRoles(roleNames));
        auditService.record(null, null, AuditActions.USER_ROLES_UPDATED, "User", id.toString(), AuditLog.Result.SUCCESS,
                Map.of("before", Map.of("roles", oldRoleNames), "roles", List.copyOf(roleNames)));
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updatePermissions(UUID id, Set<String> permissionNames) {
        User user = loadWithAuthorities(id);
        Set<String> oldPermissionNames = user.getDirectPermissions().stream()
                .map(Permission::getName).collect(Collectors.toSet());
        user.updateDirectPermissions(resolvePermissions(permissionNames));
        auditService.record(null, null, AuditActions.USER_PERMISSIONS_UPDATED, "User", id.toString(),
                AuditLog.Result.SUCCESS,
                Map.of("before", Map.of("permissions", oldPermissionNames), "permissions", List.copyOf(permissionNames)));
        return userMapper.toResponse(user);
    }

    @Transactional
    public UserResponse updateStatus(UUID id, boolean active) {
        User user = loadWithAuthorities(id);
        UUID currentUserId = SecurityUtilities.getCurrentUserId().orElse(null);
        if (!active && id.equals(currentUserId)) {
            throw new OperationNotAllowedException("You cannot deactivate your own account");
        }
        boolean wasActive = user.isActive();
        user.updateActiveStatus(active);
        if (!active) {
            tokenService.revokeAllForUser(id);
        }
        auditService.record(null, null, AuditActions.USER_STATUS_UPDATED, "User", id.toString(), AuditLog.Result.SUCCESS,
                Map.of("before", Map.of("active", wasActive), "active", active));
        return userMapper.toResponse(user);
    }

    private Map<String, Object> snapshot(User user) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("firstName", user.getFirstName());
        fields.put("lastName", user.getLastName());
        fields.put("email", user.getEmail());
        fields.put("active", user.isActive());
        return fields;
    }

    @Transactional
    public BulkOperationResponse bulkDelete(List<UUID> ids) {
        return runBulk(ids, this::delete);
    }

    @Transactional
    public BulkOperationResponse bulkUpdateStatus(List<UUID> ids, boolean active) {
        return runBulk(ids, id -> updateStatus(id, active));
    }

    @Transactional
    public BulkOperationResponse bulkUpdateRoles(List<UUID> ids, Set<String> roleNames) {
        return runBulk(ids, id -> updateRoles(id, roleNames));
    }

    @Transactional
    public BulkOperationResponse bulkAddPermissions(List<UUID> ids, Set<String> permissionNames) {
        return runBulk(ids, id -> {
            User user = loadWithAuthorities(id);
            Set<String> merged = new HashSet<>(permissionNames);
            user.getDirectPermissions().forEach(p -> merged.add(p.getName()));
            updatePermissions(id, merged);
        });
    }

    private BulkOperationResponse runBulk(List<UUID> ids, Consumer<UUID> action) {
        int successCount = 0;
        List<BulkOperationResponse.Failure> failures = new ArrayList<>();
        for (UUID id : ids) {
            try {
                action.accept(id);
                successCount++;
            } catch (RuntimeException e) {
                failures.add(new BulkOperationResponse.Failure(id, e.getMessage()));
            }
        }
        return BulkOperationResponse.of(successCount, failures);
    }

    private void applyEmailChange(User user, String newEmail) {
        String normalized = newEmail.toLowerCase().trim();
        if (!normalized.equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmailIgnoreCase(normalized)) {
                throw new DuplicateResourceException("An account with this email already exists");
            }
            user.changeEmail(normalized);
            userEventPublisher.publish(UserEventType.USER_EMAIL_CHANGED, user.getId(), normalized);
        }
    }

    private Set<Role> resolveRoles(Set<String> names) {
        List<Role> roles = roleRepository.findByNameIn(names);
        if (roles.size() != names.size()) {
            Set<String> found = roles.stream().map(Role::getName).collect(Collectors.toSet());
            Set<String> missing = new HashSet<>(names);
            missing.removeAll(found);
            throw new BadRequestException("Unknown roles: " + String.join(", ", missing));
        }
        return new HashSet<>(roles);
    }

    private Set<Permission> resolvePermissions(Set<String> names) {
        List<Permission> permissions = permissionRepository.findByNameIn(names);
        if (permissions.size() != names.size()) {
            Set<String> found = permissions.stream().map(Permission::getName).collect(Collectors.toSet());
            Set<String> missing = new HashSet<>(names);
            missing.removeAll(found);
            throw new BadRequestException("Unknown permissions: " + String.join(", ", missing));
        }
        return new HashSet<>(permissions);
    }

    private boolean canChangeActiveFlag(UUID targetId) {
        boolean isSelf = targetId.equals(SecurityUtilities.getCurrentUserId().orElse(null));
        boolean hasUserUpdate = SecurityUtilities.currentUser()
                .map(user -> user.getAuthorities().stream()
                        .anyMatch(authority -> "USER_UPDATE".equals(authority.getAuthority())))
                .orElse(false);
        return hasUserUpdate && !isSelf;
    }


    private void applyPreferences(User user, Theme theme, ThemeMode themeMode, String locale, String timezone,
                                  List<String> hiddenModules) {
        if (StringUtils.hasText(locale) && !SUPPORTED_LOCALES.contains(locale)) {
            throw new BadRequestException("Unknown locale: " + locale);
        }
        if (StringUtils.hasText(timezone) && !ZoneId.getAvailableZoneIds().contains(timezone)) {
            throw new BadRequestException("Unknown timezone: " + timezone);
        }
        user.getPreferences().updateSettings(theme, themeMode,
                StringUtils.hasText(locale) ? locale : null,
                StringUtils.hasText(timezone) ? timezone : null);
        // Deduplicated so a client that sends the same slug twice cannot grow the
        // row unbounded, and ordered so the stored value is stable to compare.
        user.getPreferences().updateHiddenModules(hiddenModules == null ? null
                : hiddenModules.stream().distinct().sorted().toList());
    }

    private User load(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    private User loadWithAuthorities(UUID id) {
        return userRepository.findByIdWithAuthorities(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }
}
