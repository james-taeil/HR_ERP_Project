package com.jamestaeil.hrerp.hr.employee.api;

import com.jamestaeil.hrerp.hr.employee.application.EmployeeBulkService;
import com.jamestaeil.hrerp.hr.employee.application.EmployeeBulkService.Confirmation;
import com.jamestaeil.hrerp.hr.employee.application.EmployeeBulkService.Validation;
import java.io.IOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/hr/employees/bulk")
public class EmployeeBulkController {
    private static final MediaType XLSX = MediaType.parseMediaType(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final EmployeeBulkService service;
    public EmployeeBulkController(EmployeeBulkService service) { this.service = service; }

    @GetMapping("/template")
    ResponseEntity<Resource> template() throws IOException {
        Resource resource = new ClassPathResource("static/templates/employee-bulk-template.xlsx");
        return ResponseEntity.ok().contentType(XLSX)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=employee-bulk-template.xlsx")
            .contentLength(resource.contentLength()).body(resource);
    }
    @PostMapping(path = "/validations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Validation validate(@RequestPart("file") MultipartFile file) { return service.validate(file); }

    @PostMapping(path = "/confirmations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    Confirmation confirm(@RequestPart("validationToken") String token,
                         @RequestPart("file") MultipartFile file) { return service.confirm(token, file); }
}
