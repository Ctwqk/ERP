package com.example.order.utils;

import com.example.order.dto.CreateOrderRequest;
import com.example.order.dto.OrderItemRequest;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * Simple Excel reader for creating an order from a sheet.
 * Expected header row (first row):
 * item_id | sku_code | quantity | unit_price_cents | document_ids (comma
 * separated)
 * name | item_type | base_uom_code | description (optional)
 * Only one of item_id / sku_code is required per row; quantity and
 * unit_price_cents are required.
 */
public class ReadXlsx {

    public static CreateOrderRequest readOrder(InputStream in) throws Exception {
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("No sheet found");
            }
            // assume row 0 is header
            List<OrderItemRequest> items = new ArrayList<>();
            Set<UUID> documentIds = new HashSet<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null)
                    continue;
                OrderItemRequest item = toItem(row);
                if (item != null) {
                    items.add(item);
                    collectDocIds(row, documentIds);
                }
            }
            return new CreateOrderRequest(items, new ArrayList<>(documentIds));
        }
    }

    /**
     * Extended read: returns both CreateOrderRequest and per-row meta (name,
     * item_type, base_uom_code, description).
     */
    public static ExcelOrderData readOrderWithMeta(InputStream in) throws Exception {
        try (Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                throw new IllegalArgumentException("No sheet found");
            }
            List<OrderItemRequest> items = new ArrayList<>();
            Set<UUID> documentIds = new HashSet<>();
            List<ExcelOrderRow> rows = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null)
                    continue;
                ExcelOrderRow parsed = toItemWithMeta(row);
                if (parsed != null) {
                    items.add(parsed.itemRequest());
                    rows.add(parsed);
                    collectDocIds(row, documentIds);
                }
            }
            return new ExcelOrderData(new CreateOrderRequest(items, new ArrayList<>(documentIds)), rows);
        }
    }

    private static OrderItemRequest toItem(Row row) {
        String itemIdStr = readString(row.getCell(0));
        String skuCode = readString(row.getCell(1));
        Integer quantity = readInt(row.getCell(2));
        Long unitPriceCents = readLong(row.getCell(3));

        if ((isBlank(itemIdStr) && isBlank(skuCode)) || quantity == null || unitPriceCents == null) {
            // skip invalid or empty lines
            return null;
        }
        UUID itemId = parseUuid(itemIdStr);
        return new OrderItemRequest(itemId, skuCode, quantity, unitPriceCents);
    }

    private static ExcelOrderRow toItemWithMeta(Row row) {
        OrderItemRequest req = toItem(row);
        if (req == null) {
            return null;
        }
        String name = readString(row.getCell(5));
        String itemType = readString(row.getCell(6));
        String baseUomCode = readString(row.getCell(7));
        String description = readString(row.getCell(8));
        return new ExcelOrderRow(req, name, itemType, baseUomCode, description);
    }

    private static void collectDocIds(Row row, Set<UUID> docIds) {
        String docIdStr = readString(row.getCell(4));
        if (isBlank(docIdStr))
            return;
        String[] parts = docIdStr.split(",");
        for (String p : parts) {
            UUID id = parseUuid(p.trim());
            if (id != null) {
                docIds.add(id);
            }
        }
    }

    private static String readString(Cell cell) {
        if (cell == null)
            return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> null;
        };
    }

    private static Integer readInt(Cell cell) {
        if (cell == null)
            return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> (int) cell.getNumericCellValue();
            case STRING -> {
                String s = cell.getStringCellValue();
                if (isBlank(s))
                    yield null;
                yield Integer.parseInt(s.trim());
            }
            default -> null;
        };
    }

    private static Long readLong(Cell cell) {
        if (cell == null)
            return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> (long) cell.getNumericCellValue();
            case STRING -> {
                String s = cell.getStringCellValue();
                if (isBlank(s))
                    yield null;
                yield Long.parseLong(s.trim());
            }
            default -> null;
        };
    }

    private static UUID parseUuid(String s) {
        if (isBlank(s))
            return null;
        try {
            return UUID.fromString(s.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public record ExcelOrderRow(
            OrderItemRequest itemRequest,
            String name,
            String itemType,
            String baseUomCode,
            String description) {
    }

    public record ExcelOrderData(
            CreateOrderRequest orderRequest,
            List<ExcelOrderRow> rows) {
    }
}
