package com.example.order.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderItemRequest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import java.util.function.Consumer;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FormulaError;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReadXlsxTest {

    @Test
    void readsOrderLinesBySku() throws Exception {
        byte[] file = XlsxFixtures.orderBySku(new Object[] {"BOLT-M8", 100, 25}, new Object[] {"NUT-M8", 50, 10});

        CreateOrderRequest request = ReadXlsx.readOrder(new ByteArrayInputStream(file));

        assertThat(request.items()).hasSize(2);
        OrderItemRequest first = request.items().get(0);
        assertThat(first.sku()).isEqualTo("BOLT-M8");
        assertThat(first.itemId()).isNull();
        assertThat(first.quantity()).isEqualTo(100);
        assertThat(first.unitPriceCents()).isEqualTo(25L);
        assertThat(request.items().get(1).sku()).isEqualTo("NUT-M8");
        assertThat(request.documentIds()).isEmpty();
    }

    @Test
    void supportsItemIdsDocumentIdsAndMetaColumns() throws Exception {
        UUID itemId = UUID.randomUUID();
        UUID doc1 = UUID.randomUUID();
        UUID doc2 = UUID.randomUUID();
        byte[] file;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("header");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(itemId.toString());
            row.createCell(2).setCellValue(3);
            row.createCell(3).setCellValue(1999);
            row.createCell(4).setCellValue(doc1 + ", " + doc2);
            row.createCell(5).setCellValue("Widget");
            row.createCell(6).setCellValue("FG");
            row.createCell(7).setCellValue("EA");
            wb.write(out);
            file = out.toByteArray();
        }

        ReadXlsx.ExcelOrderData data = ReadXlsx.readOrderWithMeta(new ByteArrayInputStream(file));

        assertThat(data.orderRequest().items()).singleElement().satisfies(i -> {
            assertThat(i.itemId()).isEqualTo(itemId);
            assertThat(i.quantity()).isEqualTo(3);
            assertThat(i.unitPriceCents()).isEqualTo(1999L);
        });
        assertThat(data.orderRequest().documentIds()).containsExactlyInAnyOrder(doc1, doc2);
        assertThat(data.rows()).singleElement().satisfies(r -> {
            assertThat(r.name()).isEqualTo("Widget");
            assertThat(r.itemType()).isEqualTo("FG");
            assertThat(r.baseUomCode()).isEqualTo("EA");
        });
    }

    @Test
    void skipsOnlyEmptyRowsAndPreservesFollowingItems() throws Exception {
        byte[] file;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("header");
            sheet.createRow(1); // physical empty row
            // row 2 is absent
            Row whitespace = sheet.createRow(3);
            whitespace.createCell(1).setCellValue("  ");
            whitespace.createCell(2).setCellValue(" ");
            whitespace.createCell(3).setCellValue("\t");
            sheet.createRow(4).createCell(0).setBlank();
            Row good = sheet.createRow(5);
            good.createCell(1).setCellValue("OK");
            good.createCell(2).setCellValue(2);
            good.createCell(3).setCellValue(7);
            wb.write(out);
            file = out.toByteArray();
        }

        CreateOrderRequest request = ReadXlsx.readOrder(new ByteArrayInputStream(file));
        ReadXlsx.ExcelOrderData data = ReadXlsx.readOrderWithMeta(new ByteArrayInputStream(file));

        assertThat(request.items()).containsExactly(new OrderItemRequest(null, "OK", 2, 7L));
        assertThat(data.orderRequest().items()).containsExactly(new OrderItemRequest(null, "OK", 2, 7L));
        assertThat(data.rows()).singleElement().satisfies(row ->
                assertThat(row.itemRequest()).isEqualTo(new OrderItemRequest(null, "OK", 2, 7L)));
    }

    @ParameterizedTest
    @CsvSource({
            "1.5, 25",
            "2147483648, 25",
            "1.0E30, 25",
            "2, 25.5",
            "2, 9.223372036854776E18",
            "2, 1.0E30"
    })
    void rejectsFractionalAndOverflowingNumericCells(double quantity, double unitPriceCents) {
        byte[] file = XlsxFixtures.orderBySku(new Object[] {"A", quantity, unitPriceCents});

        assertRejectedByBothReaders(file);
    }

    @ParameterizedTest
    @ValueSource(longs = {9007199254740991L, 9223372036854774784L})
    void preservesValidNumericIntegerBoundaries(long unitPriceCents) throws Exception {
        byte[] file = XlsxFixtures.orderBySku(new Object[] {"LIMIT", 2147483647, unitPriceCents});

        OrderItemRequest item = ReadXlsx.readOrder(new ByteArrayInputStream(file)).items().get(0);
        OrderItemRequest itemWithMeta = ReadXlsx.readOrderWithMeta(new ByteArrayInputStream(file))
                .orderRequest().items().get(0);

        assertThat(item).isEqualTo(new OrderItemRequest(null, "LIMIT", 2147483647, unitPriceCents));
        assertThat(itemWithMeta).isEqualTo(new OrderItemRequest(null, "LIMIT", 2147483647, unitPriceCents));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void rejectsIncompleteRowsInsteadOfImportingOnlyCompleteRows(int missingColumn) throws Exception {
        byte[] file = workbook(sheet -> {
            Row good = sheet.createRow(1);
            good.createCell(1).setCellValue("OK");
            good.createCell(2).setCellValue(2);
            good.createCell(3).setCellValue(7);
            Row incomplete = sheet.createRow(2);
            incomplete.createCell(1).setCellValue("MISSING");
            incomplete.createCell(2).setCellValue(3);
            incomplete.createCell(3).setCellValue(8);
            incomplete.removeCell(incomplete.getCell(missingColumn));
        });

        assertRejectedByBothReaders(file);
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 5, 6, 7, 8})
    void rejectsRowsContainingOnlyOptionalFields(int column) throws Exception {
        byte[] file = workbook(sheet -> sheet.createRow(1).createCell(column)
                .setCellValue("bb962c36-28b5-4467-8908-804cd19b742a"));

        assertRejectedByBothReaders(file);
    }

    @ParameterizedTest
    @EnumSource(value = CellType.class, names = {"BOOLEAN", "FORMULA", "ERROR"})
    void rejectsUnsupportedCellTypesInsteadOfTreatingTheRowAsEmpty(CellType type) throws Exception {
        byte[] file = workbook(sheet -> {
            Cell cell = sheet.createRow(1).createCell(5);
            switch (type) {
                case BOOLEAN -> cell.setCellValue(true);
                case FORMULA -> cell.setCellFormula("1+1");
                case ERROR -> cell.setCellErrorValue(FormulaError.DIV0.getCode());
                default -> throw new IllegalArgumentException("Unsupported test cell type");
            }
        });

        assertRejectedByBothReaders(file);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "1-1-1-1-1"})
    void rejectsInvalidExplicitItemIdsEvenWhenSkuIsPresent(String itemId) throws Exception {
        byte[] file = workbook(sheet -> {
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(itemId);
            row.createCell(1).setCellValue("VALID-SKU");
            row.createCell(2).setCellValue(1);
            row.createCell(3).setCellValue(25);
        });

        assertRejectedByBothReaders(file);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "1-1-1-1-1"})
    void rejectsInvalidExplicitDocumentIds(String documentId) throws Exception {
        byte[] file = workbook(sheet -> {
            Row row = sheet.createRow(1);
            row.createCell(1).setCellValue("VALID-SKU");
            row.createCell(2).setCellValue(1);
            row.createCell(3).setCellValue(25);
            row.createCell(4).setCellValue("bb962c36-28b5-4467-8908-804cd19b742a, " + documentId);
        });

        assertRejectedByBothReaders(file);
    }

    @ParameterizedTest
    @CsvSource({"0, BOOLEAN", "0, FORMULA", "0, ERROR", "4, BOOLEAN", "4, FORMULA", "4, ERROR"})
    void rejectsUnsupportedIdCellsOnOtherwiseValidRows(int column, CellType type) throws Exception {
        byte[] file = workbook(sheet -> {
            Row row = sheet.createRow(1);
            row.createCell(1).setCellValue("VALID-SKU");
            row.createCell(2).setCellValue(1);
            row.createCell(3).setCellValue(25);
            Cell cell = row.createCell(column);
            switch (type) {
                case BOOLEAN -> cell.setCellValue(true);
                case FORMULA -> cell.setCellFormula("1+1");
                case ERROR -> cell.setCellErrorValue(FormulaError.DIV0.getCode());
                default -> throw new IllegalArgumentException("Unsupported test cell type");
            }
        });

        assertRejectedByBothReaders(file);
    }

    @ParameterizedTest
    @ValueSource(doubles = {123.5, 9.223372036854776E18, 1.0E30})
    void rejectsNumericSkusThatWouldBeTruncatedOrSaturated(double sku) throws Exception {
        byte[] file = workbook(sheet -> {
            Row row = sheet.createRow(1);
            row.createCell(1).setCellValue(sku);
            row.createCell(2).setCellValue(1);
            row.createCell(3).setCellValue(25);
        });

        assertRejectedByBothReaders(file);
    }

    @Test
    void preservesIntegralNumericSkus() throws Exception {
        byte[] file = workbook(sheet -> {
            Row row = sheet.createRow(1);
            row.createCell(1).setCellValue(123);
            row.createCell(2).setCellValue(1);
            row.createCell(3).setCellValue(25);
        });

        OrderItemRequest expected = new OrderItemRequest(null, "123", 1, 25L);
        assertThat(ReadXlsx.readOrder(new ByteArrayInputStream(file)).items()).containsExactly(expected);
        assertThat(ReadXlsx.readOrderWithMeta(new ByteArrayInputStream(file)).orderRequest().items())
                .containsExactly(expected);
    }

    @Test
    void textCellsWithNumbersAreAccepted() throws Exception {
        byte[] file;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("header");
            Row row = sheet.createRow(1);
            row.createCell(1).setCellValue("TXT");
            row.createCell(2).setCellValue(" 4 ");
            row.createCell(3).setCellValue("250");
            wb.write(out);
            file = out.toByteArray();
        }

        OrderItemRequest item = ReadXlsx.readOrder(new ByteArrayInputStream(file)).items().get(0);

        assertThat(item.quantity()).isEqualTo(4);
        assertThat(item.unitPriceCents()).isEqualTo(250L);
    }

    @Test
    void nonSpreadsheetInputIsRejected() {
        assertThatThrownBy(() -> ReadXlsx.readOrder(new ByteArrayInputStream("plain text".getBytes())))
                .isInstanceOf(Exception.class);
    }

    private static void assertRejectedByBothReaders(byte[] file) {
        assertAll(
                () -> assertThatThrownBy(() -> ReadXlsx.readOrder(new ByteArrayInputStream(file)))
                        .isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(() -> ReadXlsx.readOrderWithMeta(new ByteArrayInputStream(file)))
                        .isInstanceOf(IllegalArgumentException.class));
    }

    private static byte[] workbook(Consumer<Sheet> populate) throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("header");
            populate.accept(sheet);
            wb.write(out);
            return out.toByteArray();
        }
    }
}
