package com.jamestaeil.hrerp.platform.api;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.jamestaeil.hrerp.platform.account.SessionPrincipal;
import com.jamestaeil.hrerp.platform.authorization.AuthorizationAdminService;
import com.jamestaeil.hrerp.platform.authorization.AuthorizationAdminService.PermissionView;
import com.jamestaeil.hrerp.platform.authorization.AuthorizationAdminService.RoleAssignment;
import com.jamestaeil.hrerp.platform.authorization.AuthorizationAdminService.RoleView;
import com.jamestaeil.hrerp.platform.authorization.AuthorizationAdminService.ScopeAssignment;
import com.jamestaeil.hrerp.platform.authorization.OrganizationScopeType;
import com.jamestaeil.hrerp.platform.authorization.PermissionCode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/platform")
class AuthorizationController {
	private final AuthorizationAdminService authorization;

	AuthorizationController(AuthorizationAdminService authorization) {
		this.authorization = authorization;
	}

	@PostMapping("/roles")
	ResponseEntity<RoleView> createRole(@AuthenticationPrincipal SessionPrincipal actor,
			@Valid @RequestBody CreateRoleRequest request) {
		RoleView role = authorization.createRole(actor.accountId(), request.roleCode(), request.roleName(),
			request.permissions());
		return ResponseEntity.created(URI.create("/api/platform/roles/" + role.id())).body(role);
	}

	@GetMapping("/roles")
	List<RoleView> roles(@AuthenticationPrincipal SessionPrincipal actor,
			@RequestParam(defaultValue = "0") long afterId,
			@RequestParam(defaultValue = "50") int limit) {
		return authorization.roles(actor.accountId(), afterId, limit);
	}

	@PatchMapping("/roles/{roleId}")
	RoleView updateRole(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable long roleId,
			@Valid @RequestBody UpdateRoleRequest request) {
		return authorization.updateRole(actor.accountId(), roleId, request.roleName(), request.active(),
			request.permissions());
	}

	@GetMapping("/permissions")
	List<PermissionView> permissions(@AuthenticationPrincipal SessionPrincipal actor) {
		return authorization.permissions(actor.accountId());
	}

	@PutMapping("/accounts/{accountId}/roles")
	ResponseEntity<Void> replaceRoles(@AuthenticationPrincipal SessionPrincipal actor,
			@PathVariable long accountId, @Valid @RequestBody ReplaceRolesRequest request) {
		authorization.replaceAccountRoles(actor.accountId(), accountId, request.roles().stream()
			.map(item -> new RoleAssignment(item.roleId(), item.validFrom(), item.validTo())).toList());
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/accounts/{accountId}/organization-scopes")
	ResponseEntity<Void> replaceScopes(@AuthenticationPrincipal SessionPrincipal actor,
			@PathVariable long accountId, @Valid @RequestBody ReplaceScopesRequest request) {
		authorization.replaceOrganizationScopes(actor.accountId(), accountId, request.scopes().stream()
			.map(item -> new ScopeAssignment(item.scopeType(), item.organizationId())).toList());
		return ResponseEntity.noContent().build();
	}

	record CreateRoleRequest(
		@NotBlank @Size(max = 100) String roleCode,
		@NotBlank @Size(max = 100) String roleName,
		@NotNull @Size(max = 100) List<@NotNull PermissionCode> permissions) {}
	record UpdateRoleRequest(
		@Size(max = 100) String roleName,
		Boolean active,
		@Size(max = 100) List<@NotNull PermissionCode> permissions) {}
	record ReplaceRolesRequest(@NotNull @Size(max = 100) List<@Valid RoleAssignmentRequest> roles) {}
	record RoleAssignmentRequest(@Positive long roleId, @NotNull Instant validFrom, Instant validTo) {}
	record ReplaceScopesRequest(@NotNull @Size(max = 100) List<@Valid ScopeAssignmentRequest> scopes) {}
	record ScopeAssignmentRequest(@NotNull OrganizationScopeType scopeType, @Positive Long organizationId) {}
}
