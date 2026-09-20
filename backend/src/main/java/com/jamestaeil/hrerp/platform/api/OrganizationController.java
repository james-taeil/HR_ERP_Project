package com.jamestaeil.hrerp.platform.api;

import java.net.URI;
import java.time.LocalDate;
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

import com.jamestaeil.hrerp.platform.account.SessionPrincipal;
import com.jamestaeil.hrerp.platform.organization.OrganizationService;
import com.jamestaeil.hrerp.platform.organization.OrganizationService.Company;
import com.jamestaeil.hrerp.platform.organization.OrganizationService.DepartmentVersion;
import com.jamestaeil.hrerp.platform.organization.OrganizationService.Workplace;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/platform")
class OrganizationController {
	private final OrganizationService service;

	OrganizationController(OrganizationService service) { this.service = service; }

	@PostMapping("/companies")
	ResponseEntity<Company> createCompany(@AuthenticationPrincipal SessionPrincipal actor,
			@Valid @RequestBody CompanyRequest request) {
		Company created = service.createCompany(actor.accountId(), request.name(), request.activeFrom(),
			request.activeTo());
		return ResponseEntity.created(URI.create("/api/platform/companies/" + created.id())).body(created);
	}

	@GetMapping("/companies")
	Company company(@AuthenticationPrincipal SessionPrincipal actor) { return service.company(actor.accountId()); }

	@PostMapping("/workplaces")
	ResponseEntity<Workplace> createWorkplace(@AuthenticationPrincipal SessionPrincipal actor,
			@Valid @RequestBody WorkplaceRequest request) {
		Workplace created = service.createWorkplace(actor.accountId(), request.companyId(), request.name(),
			request.registrationNumber(), request.address(), request.industry(), request.openedOn(), request.activeTo());
		return ResponseEntity.created(URI.create("/api/platform/workplaces/" + created.id())).body(created);
	}

	@GetMapping("/workplaces")
	List<Workplace> workplaces(@AuthenticationPrincipal SessionPrincipal actor,
			@RequestParam(defaultValue = "0") long afterId, @RequestParam(defaultValue = "50") int limit) {
		return service.workplaces(actor.accountId(), afterId, limit);
	}

	@PatchMapping("/workplaces/{id}")
	Workplace updateWorkplace(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable long id,
			@Valid @RequestBody WorkplacePatch request) {
		return service.updateWorkplace(actor.accountId(), id, request.name(), request.registrationNumber(),
			request.address(), request.industry(), request.activeTo());
	}

	@PostMapping("/departments")
	ResponseEntity<DepartmentVersion> createDepartment(@AuthenticationPrincipal SessionPrincipal actor,
			@Valid @RequestBody DepartmentRequest request) {
		DepartmentVersion created = service.createDepartment(actor.accountId(), request.code(), request.name(),
			request.workplaceId(), request.parentId(), request.effectiveFrom(), request.capacity());
		return ResponseEntity.created(URI.create("/api/platform/departments/" + created.departmentId()))
			.body(created);
	}

	@PostMapping("/departments/{id}/versions")
	ResponseEntity<DepartmentVersion> addVersion(@AuthenticationPrincipal SessionPrincipal actor,
			@PathVariable long id, @Valid @RequestBody DepartmentVersionRequest request) {
		DepartmentVersion created = service.addVersion(actor.accountId(), id, request.name(),
			request.workplaceId(), request.parentId(), request.effectiveFrom(), request.effectiveTo(),
			request.capacity());
		return ResponseEntity.created(URI.create("/api/platform/departments/" + id + "/versions/" + created.version()))
			.body(created);
	}

	@PatchMapping("/departments/{id}/closure")
	ResponseEntity<Void> close(@AuthenticationPrincipal SessionPrincipal actor, @PathVariable long id,
			@Valid @RequestBody CloseRequest request) {
		service.closeDepartment(actor.accountId(), id, request.effectiveTo());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/departments/tree")
	List<DepartmentVersion> tree(@AuthenticationPrincipal SessionPrincipal actor,
			@RequestParam(required = false) LocalDate asOf) {
		return service.tree(actor.accountId(), asOf);
	}

	record CompanyRequest(@NotBlank @Size(max = 200) String name, @NotNull LocalDate activeFrom,
		LocalDate activeTo) {}
	record WorkplaceRequest(@Positive long companyId, @NotBlank @Size(max = 200) String name,
		@NotBlank String registrationNumber, @NotBlank @Size(max = 500) String address,
		@NotBlank @Size(max = 200) String industry, @NotNull LocalDate openedOn, LocalDate activeTo) {}
	record WorkplacePatch(@Size(max = 200) String name, String registrationNumber,
		@Size(max = 500) String address, @Size(max = 200) String industry, LocalDate activeTo) {}
	record DepartmentRequest(@NotBlank String code, @NotBlank @Size(max = 200) String name,
		@Positive long workplaceId, @Positive Long parentId, @NotNull LocalDate effectiveFrom,
		@Min(0) int capacity) {}
	record DepartmentVersionRequest(@NotBlank @Size(max = 200) String name, @Positive long workplaceId,
		@Positive Long parentId, @NotNull LocalDate effectiveFrom, LocalDate effectiveTo,
		@Min(0) int capacity) {}
	record CloseRequest(@NotNull LocalDate effectiveTo) {}
}
