package com.jamestaeil.hrerp.hr.employee.application;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jamestaeil.hrerp.hr.employee.domain.DepartmentCode;
import com.jamestaeil.hrerp.hr.employee.domain.Employee;
import com.jamestaeil.hrerp.hr.employee.domain.EmployeeNumber;
import com.jamestaeil.hrerp.hr.employee.domain.ForeignWorkerDetails;
import com.jamestaeil.hrerp.hr.employee.infrastructure.EmployeeEntity;
import com.jamestaeil.hrerp.hr.employee.infrastructure.EmployeeEventEntity;
import com.jamestaeil.hrerp.hr.employee.infrastructure.EmployeeEventRepository;
import com.jamestaeil.hrerp.hr.employee.infrastructure.EmployeeRepository;
import com.jamestaeil.hrerp.hr.employee.infrastructure.ForeignWorkerProfileEntity;
import com.jamestaeil.hrerp.hr.employee.infrastructure.ForeignWorkerProfileRepository;
import com.jamestaeil.hrerp.hr.employee.infrastructure.OnboardingChecklistEntity;
import com.jamestaeil.hrerp.hr.employee.infrastructure.OnboardingChecklistRepository;
import com.jamestaeil.hrerp.platform.port.AuthorizationChecker;
import com.jamestaeil.hrerp.platform.port.OrganizationReader;
import com.jamestaeil.hrerp.platform.port.SensitiveValueCipher;

@Service
public class RegisterEmployeeService {

	private final AuthorizationChecker authorizationChecker;
	private final OrganizationReader organizationReader;
	private final SensitiveValueCipher cipher;
	private final EmployeeNumberGenerator numberGenerator;
	private final EmployeeRepository employeeRepository;
	private final OnboardingChecklistRepository checklistRepository;
	private final ForeignWorkerProfileRepository foreignWorkerRepository;
	private final EmployeeEventRepository eventRepository;
	private final JdbcClient jdbcClient;

	public RegisterEmployeeService(AuthorizationChecker authorizationChecker, OrganizationReader organizationReader,
		SensitiveValueCipher cipher, EmployeeNumberGenerator numberGenerator, EmployeeRepository employeeRepository,
		OnboardingChecklistRepository checklistRepository, ForeignWorkerProfileRepository foreignWorkerRepository,
		EmployeeEventRepository eventRepository, JdbcClient jdbcClient) {
		this.authorizationChecker = authorizationChecker;
		this.organizationReader = organizationReader;
		this.cipher = cipher;
		this.numberGenerator = numberGenerator;
		this.employeeRepository = employeeRepository;
		this.checklistRepository = checklistRepository;
		this.foreignWorkerRepository = foreignWorkerRepository;
		this.eventRepository = eventRepository;
		this.jdbcClient = jdbcClient;
	}

	@Transactional
	public RegisterEmployeeResult register(RegisterEmployeeCommand command) {
		validateIdempotencyKey(command.idempotencyKey());
		authorizationChecker.checkCanRegisterEmployee();
		DepartmentCode departmentCode = organizationReader.requireActiveDepartment(
			command.workplaceId(), command.departmentId());

		RegistrationClaim claim = claim(command.idempotencyKey());
		if (claim.employeeId() != null) {
			return new RegisterEmployeeResult(claim.employeeId(), claim.employeeNumber());
		}

		EmployeeNumber employeeNumber = numberGenerator.generate(command.hireDate(), departmentCode);
		ForeignWorkerDetails foreignDetails = command.foreignWorker() ? encryptForeignDetails(command) : null;
		Employee employee = new Employee(employeeNumber, command.name(), command.birthDate(), command.phone(),
			command.hireDate(), command.employmentType(), command.workplaceId(), command.departmentId(),
			command.position(), command.probationEndDate(), foreignDetails);

		EmployeeEntity saved = employeeRepository.saveAndFlush(new EmployeeEntity(employee));
		long employeeId = saved.id();
		checklistRepository.save(new OnboardingChecklistEntity(employeeId));
		if (foreignDetails != null) {
			foreignWorkerRepository.save(new ForeignWorkerProfileEntity(employeeId, foreignDetails));
		}
		eventRepository.save(new EmployeeEventEntity(employeeId));
		completeClaim(command.idempotencyKey(), employeeId, employeeNumber.value());
		return new RegisterEmployeeResult(employeeId, employeeNumber.value());
	}

	private RegistrationClaim claim(String key) {
		jdbcClient.sql("INSERT IGNORE INTO employee_registration_requests (idempotency_key) VALUES (:key)")
			.param("key", key)
			.update();
		List<RegistrationClaim> rows = jdbcClient.sql("""
			SELECT employee_id, employee_number
			FROM employee_registration_requests
			WHERE idempotency_key = :key
			FOR UPDATE
			""")
			.param("key", key)
			.query(RegisterEmployeeService::mapClaim)
			.list();
		if (rows.size() != 1) throw new IllegalStateException("Registration request claim failed");
		return rows.getFirst();
	}

	private void completeClaim(String key, long employeeId, String employeeNumber) {
		int updated = jdbcClient.sql("""
			UPDATE employee_registration_requests
			SET employee_id = :employeeId, employee_number = :employeeNumber, completed_at = CURRENT_TIMESTAMP(6)
			WHERE idempotency_key = :key AND employee_id IS NULL
			""")
			.param("employeeId", employeeId)
			.param("employeeNumber", employeeNumber)
			.param("key", key)
			.update();
		if (updated != 1) throw new IllegalStateException("Registration result could not be recorded");
	}

	private ForeignWorkerDetails encryptForeignDetails(RegisterEmployeeCommand command) {
		String digits = command.alienRegistrationNumber() == null
			? "" : command.alienRegistrationNumber().replaceAll("[^0-9]", "");
		if (digits.length() != 13) throw new IllegalArgumentException("Registration number must contain 13 digits");
		return new ForeignWorkerDetails(command.nationality(), command.visaType(), command.stayFrom(), command.stayUntil(),
			cipher.encrypt(command.alienRegistrationNumber()), "******-*******");
	}

	private static RegistrationClaim mapClaim(ResultSet resultSet, int rowNumber) throws SQLException {
		long employeeId = resultSet.getLong("employee_id");
		return new RegistrationClaim(resultSet.wasNull() ? null : employeeId, resultSet.getString("employee_number"));
	}

	private static void validateIdempotencyKey(String key) {
		if (key == null || key.isBlank() || key.length() > 100) {
			throw new IllegalArgumentException("A valid idempotency key is required");
		}
	}

	private record RegistrationClaim(Long employeeId, String employeeNumber) {}
}
