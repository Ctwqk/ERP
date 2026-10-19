package com.example.order.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderItemRequest;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

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
            row.createCell(4).setCellValue(doc1 + ", " + doc2 + ", not-a-uuid");
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
    void skipsIncompleteAndEmptyRows() throws Exception {
        byte[] file;
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("header");
            Row noQuantity = sheet.createRow(1);
            noQuantity.createCell(1).setCellValue("A");
            noQuantity.createCell(3).setCellValue(5);
            Row noReference = sheet.createRow(2);
            noReference.createCell(2).setCellValue(1);
            noReference.createCell(3).setCellValue(5);
            sheet.createRow(3); // empty row
            Row good = sheet.createRow(4);
            good.createCell(1).setCellValue("OK");
            good.createCell(2).setCellValue(2);
            good.createCell(3).setCellValue(7);
            wb.write(out);
            file = out.toByteArray();
        }

        CreateOrderRequest request = ReadXlsx.readOrder(new ByteArrayInputStream(file));

        assertThat(request.items()).extracting(OrderItemRequest::sku).containsExactly("OK");
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
}
