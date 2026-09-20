package com.jamestaeil.hrerp.platform.api;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;
import com.jamestaeil.hrerp.platform.account.SessionPrincipal;
import com.jamestaeil.hrerp.platform.reference.ReferenceService;
import com.jamestaeil.hrerp.platform.reference.ReferenceService.AnnualSetting;
import com.jamestaeil.hrerp.platform.reference.ReferenceService.Code;
import com.jamestaeil.hrerp.platform.reference.ReferenceService.CodeGroup;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/platform")
class ReferenceController {
	private final ReferenceService service;
	ReferenceController(ReferenceService service) { this.service = service; }

	@PostMapping("/code-groups")
	ResponseEntity<CodeGroup> createGroup(@AuthenticationPrincipal SessionPrincipal actor,
			@Valid @RequestBody GroupRequest request) {
		CodeGroup created = service.createGroup(actor.accountId(), request.code(), request.name());
		return ResponseEntity.created(URI.create("/api/platform/code-groups/" + created.id())).body(created);
	}
	@GetMapping("/code-groups")
	List<CodeGroup> groups(@AuthenticationPrincipal SessionPrincipal actor,
			@RequestParam(defaultValue = "0") long afterId, @RequestParam(defaultValue = "50") int limit,
			@RequestParam(defaultValue = "false") boolean includeInactive) {
		return service.groups(actor.accountId(), afterId, limit, includeInactive);
	}
	@PatchMapping("/code-groups/{id}")
	CodeGroup changeGroup(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable long id,
			@Valid @RequestBody GroupPatch request) {
		return service.changeGroup(actor.accountId(), id, request.name(), request.active());
	}
	@PostMapping("/codes")
	ResponseEntity<Code> createCode(@AuthenticationPrincipal SessionPrincipal actor,
			@Valid @RequestBody CodeRequest request) {
		Code created = service.createCode(actor.accountId(), request.groupId(), request.code(), request.name());
		return ResponseEntity.created(URI.create("/api/platform/codes/" + created.id())).body(created);
	}
	@GetMapping("/codes")
	List<Code> codes(@AuthenticationPrincipal SessionPrincipal actor, @RequestParam long groupId,
			@RequestParam(defaultValue = "0") long afterId, @RequestParam(defaultValue = "50") int limit,
			@RequestParam(defaultValue = "false") boolean includeInactive) {
		return service.codes(actor.accountId(), groupId, afterId, limit, includeInactive);
	}
	@PatchMapping("/codes/{id}")
	Code changeCode(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable long id,
			@Valid @RequestBody CodePatch request) {
		return service.changeCode(actor.accountId(), id, request.name(), request.active());
	}
	@PostMapping("/annual-settings")
	ResponseEntity<AnnualSetting> addSetting(@AuthenticationPrincipal SessionPrincipal actor,
			@Valid @RequestBody AnnualSettingRequest request) {
		AnnualSetting created = service.addSetting(actor.accountId(), request.type(), request.year(),
			request.scopeType(), request.scopeId(), request.value(), request.sourceReference());
		return ResponseEntity.created(URI.create("/api/platform/annual-settings/" + created.id())).body(created);
	}
	@GetMapping("/annual-settings")
	AnnualSetting setting(@AuthenticationPrincipal SessionPrincipal actor, @RequestParam String type,
			@RequestParam int year, @RequestParam String scopeType, @RequestParam long scopeId) {
		return service.currentSetting(actor.accountId(), type, year, scopeType, scopeId);
	}
	@GetMapping("/annual-settings/history")
	List<AnnualSetting> history(@AuthenticationPrincipal SessionPrincipal actor, @RequestParam String type,
			@RequestParam int year, @RequestParam String scopeType, @RequestParam long scopeId,
			@RequestParam(defaultValue = "0") long afterVersion, @RequestParam(defaultValue = "50") int limit) {
		return service.settingHistory(actor.accountId(), type, year, scopeType, scopeId, afterVersion, limit);
	}

	record GroupRequest(@NotBlank @Size(max = 50) String code, @NotBlank @Size(max = 100) String name) {}
	record GroupPatch(@Size(max = 100) String name, Boolean active) {}
	record CodeRequest(@Positive long groupId, @NotBlank @Size(max = 50) String code,
		@NotBlank @Size(max = 100) String name) {}
	record CodePatch(@Size(max = 100) String name, Boolean active) {}
	record AnnualSettingRequest(@NotBlank @Size(max = 100) String type,
		@Min(2000) @Max(9999) int year, @NotBlank @Size(max = 30) String scopeType,
		@Positive long scopeId, @NotNull JsonNode value, @NotBlank @Size(max = 500) String sourceReference) {}
}
