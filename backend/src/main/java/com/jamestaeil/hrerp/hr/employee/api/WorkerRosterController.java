package com.jamestaeil.hrerp.hr.employee.api;

import com.jamestaeil.hrerp.hr.employee.application.WorkerRosterService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hr/employees")
public class WorkerRosterController {
    private final WorkerRosterService service;
    public WorkerRosterController(WorkerRosterService service) { this.service = service; }

    @GetMapping(value = "/{employeeId}/worker-roster.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> download(@PathVariable long employeeId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
            .cacheControl(CacheControl.noStore())
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=worker-roster-" + employeeId + ".pdf")
            .body(service.generate(employeeId));
    }
}
