package com.school.supervision.modules.importexport;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExcelWorkbookService {

    public byte[] buildTemplate(String sheetName,
                                List<String> headers,
                                List<List<String>> sampleRows,
                                List<String> notes) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet dataSheet = workbook.createSheet(sheetName);
            writeHeader(dataSheet, headers);
            writeRows(dataSheet, sampleRows, 1);
            autosize(dataSheet, headers.size());
            if (notes != null && !notes.isEmpty()) {
                Sheet notesSheet = workbook.createSheet("notes");
                for (int i = 0; i < notes.size(); i++) {
                    Row row = notesSheet.createRow(i);
                    row.createCell(0).setCellValue(notes.get(i));
                }
                notesSheet.autoSizeColumn(0);
            }
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not generate template", e);
        }
    }

    public byte[] buildExport(String sheetName,
                              List<String> headers,
                              List<List<String>> rows) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            writeHeader(sheet, headers);
            writeRows(sheet, rows, 1);
            autosize(sheet, headers.size());
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not generate export workbook", e);
        }
    }

    public List<Map<String, String>> parseRows(MultipartFile file) {
        try (InputStream in = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(in)) {
            if (workbook.getNumberOfSheets() == 0) {
                return List.of();
            }
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) {
                return List.of();
            }
            List<String> headers = new ArrayList<>();
            int last = header.getLastCellNum();
            for (int c = 0; c < last; c++) {
                headers.add(cellText(header.getCell(c)).trim());
            }
            List<Map<String, String>> result = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isEmpty(row, headers.size())) {
                    continue;
                }
                Map<String, String> values = new LinkedHashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    String key = headers.get(c);
                    if (key == null || key.isBlank()) continue;
                    values.put(key, cellText(row.getCell(c)).trim());
                }
                values.put("__rowNum", String.valueOf(r + 1));
                result.add(values);
            }
            return result;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read workbook");
        }
    }

    private void writeHeader(Sheet sheet, List<String> headers) {
        Row row = sheet.createRow(0);
        CellStyle style = sheet.getWorkbook().createCellStyle();
        var font = sheet.getWorkbook().createFont();
        font.setBold(true);
        style.setFont(font);
        for (int i = 0; i < headers.size(); i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers.get(i));
            cell.setCellStyle(style);
        }
    }

    private void writeRows(Sheet sheet, List<List<String>> rows, int startRow) {
        int rowIdx = startRow;
        for (List<String> values : rows) {
            Row row = sheet.createRow(rowIdx++);
            for (int i = 0; i < values.size(); i++) {
                row.createCell(i).setCellValue(values.get(i) == null ? "" : values.get(i));
            }
        }
    }

    private void autosize(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private boolean isEmpty(Row row, int columns) {
        for (int i = 0; i < columns; i++) {
            if (!cellText(row.getCell(i)).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String cellText(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                double n = cell.getNumericCellValue();
                if (Math.rint(n) == n) yield String.valueOf((long) n);
                yield String.valueOf(n);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }
}
