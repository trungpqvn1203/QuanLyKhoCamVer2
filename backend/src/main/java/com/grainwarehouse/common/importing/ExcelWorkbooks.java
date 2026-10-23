package com.grainwarehouse.common.importing;

import com.grainwarehouse.common.exception.InvalidRequestException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.NumberToTextConverter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phần dính tới Apache POI: sinh file mẫu .xlsx và đọc file upload thành ImportRow.
 * Cấu trúc file mẫu:
 * - "Dữ liệu": dòng 1 tiêu đề, người dùng điền từ dòng 2. Cột có lựa chọn cố định có dropdown.
 * - "Hướng dẫn": giải thích từng cột + một dòng ví dụ (không đặt ví dụ ở sheet dữ liệu để khỏi bị nhập nhầm).
 * - "_meta" (ẩn hẳn): ô A1 = mã mẫu; các cột sau chứa danh sách giá trị cho dropdown.
 */
final class ExcelWorkbooks {

    static final String DATA_SHEET = "Dữ liệu";
    static final String GUIDE_SHEET = "Hướng dẫn";
    static final String META_SHEET = "_meta";

    private static final String FILE_ERROR_FIELD = "file";

    private ExcelWorkbooks() {
    }

    static byte[] template(ImportDefinition<?, ?> definition, List<ImportColumn> columns, int maxRows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet data = workbook.createSheet(DATA_SHEET);
            Sheet guide = workbook.createSheet(GUIDE_SHEET);
            Sheet meta = workbook.createSheet(META_SHEET);
            // VERY_HIDDEN: không hiện cả trong menu Unhide của Excel → người dùng không vô tình sửa mã mẫu
            workbook.setSheetVisibility(workbook.getSheetIndex(meta), SheetVisibility.VERY_HIDDEN);
            meta.createRow(0).createCell(0).setCellValue(definition.templateId());

            CellStyle headerStyle = headerStyle(workbook);
            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));

            Row header = data.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                ImportColumn column = columns.get(i);
                Cell cell = header.createCell(i);
                cell.setCellValue(column.headerText());
                cell.setCellStyle(headerStyle);
                data.setColumnWidth(i, Math.max(column.headerText().length() + 6, 18) * 256);
                if (column.text()) {
                    // Ô mới gõ trong cột này sẽ là Text: "0901234567" giữ nguyên số 0 đầu
                    data.setDefaultColumnStyle(i, textStyle);
                }
                if (!column.options().isEmpty()) {
                    addDropdown(data, meta, i, column, maxRows);
                }
            }
            data.createFreezePane(0, 1);

            writeGuide(guide, headerStyle, definition, columns, maxRows);
            workbook.setActiveSheet(0);
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            // Ghi vào bộ nhớ (ByteArrayOutputStream) — chỉ lỗi khi có bug, không phải lỗi người dùng
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Dropdown lấy danh sách từ sheet _meta thay vì liệt kê thẳng trong ô: Excel giới hạn danh sách liệt kê thẳng
     * 255 ký tự — vài chục tên danh mục là vượt.
     */
    private static void addDropdown(Sheet data, Sheet meta, int columnIndex, ImportColumn column, int maxRows) {
        int metaColumn = columnIndex + 1;
        List<String> options = column.options();
        for (int i = 0; i < options.size(); i++) {
            Row row = meta.getRow(i) == null ? meta.createRow(i) : meta.getRow(i);
            row.createCell(metaColumn).setCellValue(options.get(i));
        }
        String letter = CellReference.convertNumToColString(metaColumn);
        String range = "'" + META_SHEET + "'!$" + letter + "$1:$" + letter + "$" + options.size();

        DataValidationHelper helper = data.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createFormulaListConstraint(range);
        DataValidation validation = helper.createValidation(constraint,
                new CellRangeAddressList(1, maxRows, columnIndex, columnIndex));
        validation.setShowErrorBox(true);
        validation.createErrorBox("Giá trị không hợp lệ", "Chọn " + column.header() + " trong danh sách");
        // XSSF: true mới HIỆN mũi tên dropdown (tên hàm ngược nghĩa, xem javadoc POI)
        validation.setSuppressDropDownArrow(true);
        data.addValidationData(validation);
    }

    private static void writeGuide(Sheet guide, CellStyle headerStyle, ImportDefinition<?, ?> definition,
                                   List<ImportColumn> columns, int maxRows) {
        int r = 0;
        guide.createRow(r++).createCell(0).setCellValue("Mẫu nhập " + definition.entityLabel());
        for (String line : List.of(
                "• Điền dữ liệu vào sheet '" + DATA_SHEET + "', bắt đầu từ dòng 2. Không đổi tên sheet, tên cột, thứ tự cột.",
                "• Cột có dấu * là bắt buộc. Mã do hệ thống tự sinh nên mẫu không có cột mã.",
                "• Tối đa " + maxRows + " dòng mỗi file. Có một dòng lỗi thì hệ thống không nhập dòng nào.",
                "• Số thập phân dùng dấu chấm hoặc định dạng số của Excel, không gõ đơn vị (\"25\", không phải \"25kg\")."
        )) {
            guide.createRow(r++).createCell(0).setCellValue(line);
        }
        r++;

        Row header = guide.createRow(r++);
        String[] titles = {"Cột", "Bắt buộc", "Giá trị cho phép", "Ghi chú"};
        for (int i = 0; i < titles.length; i++) {
            header.createCell(i).setCellValue(titles[i]);
            header.getCell(i).setCellStyle(headerStyle);
        }
        for (ImportColumn column : columns) {
            Row row = guide.createRow(r++);
            row.createCell(0).setCellValue(column.header());
            row.createCell(1).setCellValue(column.required() ? "Có" : "Không");
            row.createCell(2).setCellValue(column.options().isEmpty() ? "" : String.join(", ", column.options()));
            row.createCell(3).setCellValue(column.note() == null ? "" : column.note());
        }
        r++;

        guide.createRow(r++).createCell(0).setCellValue("Ví dụ một dòng hợp lệ:");
        Row exampleHeader = guide.createRow(r++);
        Row example = guide.createRow(r);
        List<String> values = definition.example();
        for (int i = 0; i < columns.size(); i++) {
            exampleHeader.createCell(i).setCellValue(columns.get(i).headerText());
            exampleHeader.getCell(i).setCellStyle(headerStyle);
            example.createCell(i).setCellValue(i < values.size() ? values.get(i) : "");
        }
        guide.setColumnWidth(0, 30 * 256);
        guide.setColumnWidth(1, 12 * 256);
        guide.setColumnWidth(2, 30 * 256);
        guide.setColumnWidth(3, 60 * 256);
    }

    private static CellStyle headerStyle(Workbook workbook) {
        Font bold = workbook.createFont();
        bold.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(bold);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    /**
     * Đọc sheet dữ liệu. Lỗi ở mức FILE (không phải mẫu, sai cột, rỗng, quá nhiều dòng) → InvalidRequestException
     * field "file": không thể kiểm tra từng dòng khi chính cấu trúc file đã sai.
     * Dòng trống hoàn toàn bị bỏ qua (Excel hay giữ lại dòng đã xóa nội dung ở cuối sheet).
     */
    static List<ImportRow> read(InputStream input, ImportDefinition<?, ?> definition, List<ImportColumn> columns,
                                int maxRows) {
        String wrongTemplate = "File không phải mẫu nhập " + definition.entityLabel()
                + ". Hãy bấm 'Tải file mẫu' và điền vào file đó";
        // Apache POI mặc định đã chặn "zip bomb" (file .xlsx giải nén ra lớn bất thường) bằng tỉ lệ nén tối thiểu
        try (Workbook workbook = new XSSFWorkbook(input)) {
            Sheet meta = workbook.getSheet(META_SHEET);
            Sheet data = workbook.getSheet(DATA_SHEET);
            if (meta == null || data == null || !definition.templateId().equals(text(meta, 0, 0))) {
                throw new InvalidRequestException(FILE_ERROR_FIELD, wrongTemplate);
            }
            checkHeader(data, columns);

            List<ImportRow> rows = new ArrayList<>();
            for (int r = 1; r <= data.getLastRowNum(); r++) {
                Map<String, Object> values = readValues(data.getRow(r), columns);
                if (values.isEmpty()) {
                    continue;
                }
                if (rows.size() == maxRows) {
                    throw new InvalidRequestException(FILE_ERROR_FIELD,
                            "File có quá " + maxRows + " dòng dữ liệu. Hãy chia thành nhiều file");
                }
                // r bắt đầu từ 0 trong POI; người dùng thấy dòng tiêu đề là 1
                rows.add(new ImportRow(r + 1, columns, values));
            }
            if (rows.isEmpty()) {
                throw new InvalidRequestException(FILE_ERROR_FIELD,
                        "File chưa có dòng dữ liệu nào (điền từ dòng 2 của sheet '" + DATA_SHEET + "')");
            }
            return rows;
        } catch (InvalidRequestException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            // Không phải .xlsx (file .xls, .csv đổi đuôi, file hỏng...) — POI ném nhiều loại exception khác nhau
            throw new InvalidRequestException(FILE_ERROR_FIELD,
                    "Không đọc được file. Chỉ nhận file Excel .xlsx tải từ nút 'Tải file mẫu'");
        }
    }

    private static void checkHeader(Sheet data, List<ImportColumn> columns) {
        Row header = data.getRow(0);
        for (int i = 0; i < columns.size(); i++) {
            String expected = columns.get(i).headerText();
            Object actual = header == null ? null : cellValue(header.getCell(i));
            if (!expected.equals(actual)) {
                throw new InvalidRequestException(FILE_ERROR_FIELD, "Cột " + CellReference.convertNumToColString(i)
                        + " phải có tiêu đề '" + expected + "'. Đừng đổi tên hay thứ tự cột — hãy tải lại file mẫu");
            }
        }
    }

    private static Map<String, Object> readValues(Row row, List<ImportColumn> columns) {
        Map<String, Object> values = new HashMap<>();
        if (row == null) {
            return values;
        }
        for (int i = 0; i < columns.size(); i++) {
            Object value = cellValue(row.getCell(i));
            if (value != null) {
                values.put(columns.get(i).key(), value);
            }
        }
        return values;
    }

    private static String text(Sheet sheet, int rowIndex, int columnIndex) {
        Row row = sheet.getRow(rowIndex);
        return row == null ? null : (cellValue(row.getCell(columnIndex)) instanceof String s ? s : null);
    }

    /** Ô → String (đã cắt khoảng trắng, rỗng = null) hoặc BigDecimal. Ô công thức lấy kết quả Excel đã tính sẵn. */
    private static Object cellValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case STRING -> {
                //   (khoảng trắng không ngắt) hay đi kèm khi dán từ web — strip() không coi nó là khoảng trắng
                String value = cell.getStringCellValue().replace(' ', ' ').strip();
                yield value.isEmpty() ? null : value;
            }
            // Excel lưu số dạng double; NumberToTextConverter cho đúng chuỗi Excel hiển thị (15 chữ số có nghĩa:
            // ô =0.1+0.2 → "0.3", không phải 0.30000000000000004) rồi mới đổi sang BigDecimal
            case NUMERIC -> new BigDecimal(NumberToTextConverter.toText(cell.getNumericCellValue()));
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }
}
