package com.example.order.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Builds order spreadsheets in the layout read by {@link ReadXlsx}. */
public final class XlsxFixtures {

    private XlsxFixtures() {
    }

    /** Each row: {sku, quantity, unitPriceCents}. */
    public static byte[] orderBySku(Object[]... rows) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("order");
            Row header = sheet.createRow(0);
            String[] titles = {"item_id", "sku_code", "quantity", "unit_price_cents", "document_ids"};
            for (int i = 0; i < titles.length; i++) {
                header.createCell(i).setCellValue(titles[i]);
            }
            int r = 1;
            for (Object[] row : rows) {
                Row line = sheet.createRow(r++);
                line.createCell(1).setCellValue((String) row[0]);
                line.createCell(2).setCellValue(((Number) row[1]).doubleValue());
                line.createCell(3).setCellValue(((Number) row[2]).doubleValue());
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
