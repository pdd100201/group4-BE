package com.onlinelearning.service;

import com.onlinelearning.dto.question.QuestionRequest;
import com.onlinelearning.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class QuestionImportService {
    private final ExpertQuestionService questions;
    private static final List<String> HEADERS = List.of("content", "type", "optionA", "optionB", "optionC", "optionD", "correct", "explanation", "active");
    public record RowError(int row, String message) {}
    public record ImportResult(int imported, List<RowError> errors) {}

    public byte[] template(Long courseId, String email) {
        questions.owned(courseId, email, false);
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("Questions"); Row header = sheet.createRow(0);
            for (int i=0; i<HEADERS.size(); i++) header.createCell(i).setCellValue(HEADERS.get(i));
            String[] sample = {"Java uses which keyword to define a class?", "SINGLE_CHOICE", "class", "table", "function", "module", "A", "The class keyword defines a Java class.", "true"};
            Row row = sheet.createRow(1); for (int i=0; i<sample.length; i++) row.createCell(i).setCellValue(sample[i]);
            sheet.createFreezePane(0,1); for (int i=0; i<HEADERS.size(); i++) sheet.setColumnWidth(i, i == 0 || i == 7 ? 11000 : 5500);
            Sheet help = book.createSheet("Instructions");
            help.createRow(0).createCell(0).setCellValue("type: SINGLE_CHOICE or MULTIPLE_CHOICE. correct: A or A,C. active: true or false.");
            help.createRow(1).createCell(0).setCellValue("Four distinct options required. Max 500 question rows. Valid rows are imported together; invalid rows are reported.");
            help.setColumnWidth(0,30000); book.write(out); return out.toByteArray();
        } catch (IOException e) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Không tạo được file mẫu."); }
    }

    @Transactional
    public ImportResult importFile(Long courseId, String email, MultipartFile file) {
        questions.owned(courseId, email, true);
        if (file.isEmpty() || file.getSize() > 5L*1024*1024 || file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Chọn file XLSX tối đa 5 MB.");
        List<QuestionRequest> valid = new ArrayList<>(); List<RowError> errors = new ArrayList<>();
        try (var book = new XSSFWorkbook(file.getInputStream())) {
            if (book.getNumberOfSheets() == 0) throw invalid();
            Sheet sheet = book.getSheetAt(0); Row header = sheet.getRow(0);
            if (header == null || sheet.getLastRowNum() > 500) throw invalid();
            for (int i=0; i<HEADERS.size(); i++) if (!HEADERS.get(i).equals(text(header,i))) throw invalid();
            for (int r=1; r<=sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r); if (row == null) continue;
                List<String> cells = new ArrayList<>(); for (int c=0; c<HEADERS.size(); c++) cells.add(text(row,c));
                if (cells.stream().allMatch(String::isBlank)) continue;
                try {
                    String answer = cells.get(6).toUpperCase(Locale.ROOT).replace(" ", "");
                    if (!answer.matches("[A-D](,[A-D])*")) throw new IllegalArgumentException("Đáp án đúng phải là A-D, nhiều đáp án cách nhau bằng dấu phẩy.");
                    Set<String> correct = new HashSet<>(Arrays.asList(answer.split(",")));
                    if (correct.size() != answer.split(",").length) throw new IllegalArgumentException("Đáp án đúng bị trùng.");
                    if (!Set.of("true","false").contains(cells.get(8).toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("active phải là true hoặc false.");
                    List<QuestionRequest.Option> options = new ArrayList<>();
                    for (int i=0; i<4; i++) options.add(new QuestionRequest.Option(cells.get(i+2), correct.contains(String.valueOf((char)('A'+i)))));
                    var request = new QuestionRequest(cells.get(0), cells.get(1), cells.get(7), Boolean.parseBoolean(cells.get(8)), null, options);
                    questions.validate(request); valid.add(request);
                } catch (ApiException e) { errors.add(new RowError(r+1, e.getMessage())); }
                catch (IllegalArgumentException e) { errors.add(new RowError(r+1, e.getMessage())); }
            }
        } catch (ApiException e) { throw e; }
        catch (Exception e) { throw invalid(); }
        for (var request : valid) questions.create(courseId, email, request);
        return new ImportResult(valid.size(), errors);
    }
    private String text(Row row, int column) {
        Cell cell = row.getCell(column); if (cell == null) return "";
        if (cell.getCellType() == CellType.FORMULA) throw invalid();
        return new DataFormatter(Locale.ROOT).formatCellValue(cell).trim();
    }
    private ApiException invalid() { return new ApiException(HttpStatus.BAD_REQUEST, "File không đúng mẫu XLSX, chứa công thức hoặc vượt 500 dòng."); }
}
