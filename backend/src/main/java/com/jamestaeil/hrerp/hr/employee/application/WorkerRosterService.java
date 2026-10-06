package com.jamestaeil.hrerp.hr.employee.application;

import com.jamestaeil.hrerp.hr.employee.domain.Gender;
import com.jamestaeil.hrerp.hr.record.application.RecordNotFoundException;
import com.jamestaeil.hrerp.platform.port.AuthorizationChecker;
import com.jamestaeil.hrerp.platform.port.CurrentActorProvider;
import com.jamestaeil.hrerp.platform.port.RecordAudit;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.awt.Color;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkerRosterService {
    private static final float LEFT = 40f;
    private static final float WIDTH = PDRectangle.A4.getWidth() - 80f;
    private final JdbcTemplate jdbc;
    private final CurrentActorProvider actors;
    private final AuthorizationChecker authorization;
    private final RecordAudit audit;

    public WorkerRosterService(JdbcTemplate jdbc, CurrentActorProvider actors,
            AuthorizationChecker authorization, RecordAudit audit) {
        this.jdbc = jdbc; this.actors = actors; this.authorization = authorization; this.audit = audit;
    }

    @Transactional
    public byte[] generate(long employeeId) {
        if (employeeId <= 0) throw new IllegalArgumentException();
        long actor = actors.requireActorId();
        authorization.checkCanReadRecord(actor, employeeId);
        Roster roster = load(employeeId);
        List<String> missing = new ArrayList<>();
        if (roster.gender() == Gender.UNSPECIFIED) missing.add("gender");
        if (roster.address() == null || roster.address().isBlank()) missing.add("address");
        if (!missing.isEmpty()) throw new WorkerRosterIncompleteException(missing);
        byte[] pdf = render(roster);
        audit.viewed(actor, employeeId, "WORKER_ROSTER_PDF");
        return pdf;
    }

    private Roster load(long employeeId) {
        List<Roster> rows = jdbc.query("""
            SELECT e.employee_name, e.gender, e.birth_date, e.address, e.phone, e.position_name, e.hire_date,
                   c.contract_start, c.contract_end, t.termination_date, t.reason_code,
                   COALESCE((SELECT GROUP_CONCAT(CONCAT(DATE_FORMAT(r.start_date, '%Y-%m-%d'), ' ~ ',
                       COALESCE(DATE_FORMAT(r.end_date, '%Y-%m-%d'), '현재'), ' ', r.institution, ' ', r.job_name)
                       ORDER BY r.start_date SEPARATOR '\n') FROM careers r WHERE r.employee_id=e.id), '') AS career_history
            FROM employees e
            LEFT JOIN employment_contracts c ON c.id=(SELECT c2.id FROM employment_contracts c2
                WHERE c2.employee_id=e.id ORDER BY c2.contract_start DESC, c2.id DESC LIMIT 1)
            LEFT JOIN terminations t ON t.employee_id=e.id
            WHERE e.id=?
            """, (rs, n) -> new Roster(rs.getString("employee_name"), Gender.valueOf(rs.getString("gender")),
                rs.getObject("birth_date", LocalDate.class), rs.getString("address"), rs.getString("phone"),
                rs.getString("position_name"), rs.getObject("hire_date", LocalDate.class),
                rs.getObject("contract_start", LocalDate.class), rs.getObject("contract_end", LocalDate.class),
                rs.getObject("termination_date", LocalDate.class), rs.getString("reason_code"),
                rs.getString("career_history")), employeeId);
        if (rows.isEmpty()) throw new RecordNotFoundException();
        return rows.getFirst();
    }

    private byte[] render(Roster r) {
        try (PDDocument document = new PDDocument();
             InputStream fontStream = new ClassPathResource("fonts/NanumGothic-Regular.ttf").getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType0Font font = PDType0Font.load(document, fontStream, true);
            PDPage page = new PDPage(PDRectangle.A4); document.addPage(page);
            try (PDPageContentStream out = new PDPageContentStream(document, page)) {
                text(out, font, 19, LEFT, 792, "근로자 명부");
                text(out, font, 8, LEFT, 774, "근로기준법 시행규칙 별지 제16호서식 (2024. 7. 29. 개정 기준)");
                float y = 750;
                y = row(out, font, y, 34, "성명", r.name(), "성별", r.gender() == Gender.MALE ? "남" : "여");
                y = row(out, font, y, 34, "생년월일", value(r.birthDate()), "전화번호", r.phone());
                y = wideRow(out, font, y, 42, "주소", r.address());
                y = wideRow(out, font, y, 36, "종사 업무", r.job());
                y = row(out, font, y, 42, "고용일", value(r.hireDate()), "계약기간", period(r.contractStart(), r.contractEnd()));
                y = row(out, font, y, 42, "퇴직·해고일", value(r.terminationDate()), "사유", value(r.terminationReason()));
                y = wideRow(out, font, y, 118, "경력·이력", value(r.careerHistory()));
                wideRow(out, font, y, 82, "그 밖에 필요한 사항", "사원 기본정보와 확정된 인사 이력을 기준으로 생성");
                text(out, font, 7, LEFT, 45, "210 mm x 297 mm / 전자문서");
            }
            document.save(output); return output.toByteArray();
        } catch (IOException failure) { throw new IllegalStateException("Worker roster PDF generation failed", failure); }
    }

    private static float row(PDPageContentStream out, PDType0Font font, float top, float height,
            String label1, String value1, String label2, String value2) throws IOException {
        float[] widths = {82, 176, 82, WIDTH - 340};
        String[] values = {label1, value1, label2, value2};
        float x = LEFT;
        for (int i = 0; i < values.length; i++) { cell(out, font, x, top, widths[i], height, values[i], i % 2 == 0); x += widths[i]; }
        return top - height;
    }

    private static float wideRow(PDPageContentStream out, PDType0Font font, float top, float height,
            String label, String value) throws IOException {
        cell(out, font, LEFT, top, 110, height, label, true);
        cell(out, font, LEFT + 110, top, WIDTH - 110, height, value, false);
        return top - height;
    }

    private static void cell(PDPageContentStream out, PDType0Font font, float x, float top,
            float width, float height, String value, boolean label) throws IOException {
        out.saveGraphicsState();
        out.setNonStrokingColor(label ? new Color(235, 239, 244) : Color.WHITE);
        out.addRect(x, top - height, width, height); out.fill();
        out.setStrokingColor(new Color(70, 70, 70)); out.setLineWidth(.6f); out.addRect(x, top - height, width, height); out.stroke();
        out.restoreGraphicsState();
        float size = label ? 9 : 8.5f;
        List<String> lines = wrap(font, size, value(value), width - 12);
        float y = top - 13;
        for (String line : lines) {
            if (y < top - height + 7) break;
            text(out, font, size, x + 6, y, line); y -= 12;
        }
    }

    private static List<String> wrap(PDType0Font font, float size, String value, float maxWidth) throws IOException {
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        for (String paragraph : value.split("\\R", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (!line.isEmpty() && font.getStringWidth(candidate) / 1000f * size > maxWidth) {
                    lines.add(line.toString()); line = new StringBuilder(word);
                } else line = new StringBuilder(candidate);
            }
            lines.add(line.toString());
        }
        return lines;
    }

    private static void text(PDPageContentStream out, PDType0Font font, float size, float x, float y, String value)
            throws IOException {
        out.setNonStrokingColor(Color.BLACK);
        out.beginText(); out.setFont(font, size); out.newLineAtOffset(x, y); out.showText(value(value)); out.endText();
    }
    private static String value(Object value) { return value == null ? "" : value.toString(); }
    private static String period(LocalDate start, LocalDate end) { return start == null ? "" : start + " ~ " + end; }

    private record Roster(String name, Gender gender, LocalDate birthDate, String address, String phone,
                          String job, LocalDate hireDate, LocalDate contractStart, LocalDate contractEnd,
                          LocalDate terminationDate, String terminationReason, String careerHistory) {}
}
