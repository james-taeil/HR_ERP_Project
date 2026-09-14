package com.jamestaeil.hrerp.hr.record.api;

import java.net.URI;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.jamestaeil.hrerp.hr.record.domain.*;
import com.jamestaeil.hrerp.hr.record.application.RecordService;
import com.jamestaeil.hrerp.hr.record.application.RecordViews.*;

@RestController
@RequestMapping("/api/hr/employees/{employeeId}")
public class RecordController {
    private final RecordService service;
    public RecordController(RecordService service) { this.service = service; }

    public record Mutation<T extends RecordData>(
        @NotNull @Pattern(regexp = "[A-Za-z0-9_-]{1,100}") String idempotencyKey,
        Long version, @NotNull T data) {}

    @GetMapping("/record")
    public Card card(@PathVariable long employeeId) { return service.card(employeeId); }

    @GetMapping("/{section:family-members|educations|careers|certifications}")
    public List<Entry> list(@PathVariable long employeeId, @PathVariable String section) {
        return service.list(employeeId, RecordKind.fromPath(section));
    }

    @PostMapping("/family-members")
    public ResponseEntity<Saved> createFamily(@PathVariable long employeeId,
            @Valid @RequestBody Mutation<RecordData.Family> request) {
        return create(employeeId, "family-members", request);
    }
    @PutMapping("/family-members/{recordId}")
    public Saved updateFamily(@PathVariable long employeeId, @PathVariable long recordId,
            @Valid @RequestBody Mutation<RecordData.Family> request) { return update(employeeId, recordId, request); }

    @PostMapping("/educations")
    public ResponseEntity<Saved> createEducation(@PathVariable long employeeId,
            @Valid @RequestBody Mutation<RecordData.Education> request) { return create(employeeId, "educations", request); }
    @PutMapping("/educations/{recordId}")
    public Saved updateEducation(@PathVariable long employeeId, @PathVariable long recordId,
            @Valid @RequestBody Mutation<RecordData.Education> request) { return update(employeeId, recordId, request); }

    @PostMapping("/careers")
    public ResponseEntity<Saved> createCareer(@PathVariable long employeeId,
            @Valid @RequestBody Mutation<RecordData.Career> request) { return create(employeeId, "careers", request); }
    @PutMapping("/careers/{recordId}")
    public Saved updateCareer(@PathVariable long employeeId, @PathVariable long recordId,
            @Valid @RequestBody Mutation<RecordData.Career> request) { return update(employeeId, recordId, request); }

    @PostMapping("/certifications")
    public ResponseEntity<Saved> createCertification(@PathVariable long employeeId,
            @Valid @RequestBody Mutation<RecordData.Certification> request) { return create(employeeId, "certifications", request); }
    @PutMapping("/certifications/{recordId}")
    public Saved updateCertification(@PathVariable long employeeId, @PathVariable long recordId,
            @Valid @RequestBody Mutation<RecordData.Certification> request) { return update(employeeId, recordId, request); }

    private ResponseEntity<Saved> create(long employeeId, String section, Mutation<?> request) {
        Saved saved = service.save(employeeId, null, request.idempotencyKey(), request.version(), request.data());
        return ResponseEntity.created(URI.create("/api/hr/employees/" + employeeId + "/" + section))
            .body(saved);
    }
    private Saved update(long employeeId, long recordId, Mutation<?> request) {
        return service.save(employeeId, recordId, request.idempotencyKey(), request.version(), request.data());
    }
}
