package com.example.item.service;

import com.example.item.domain.Bom;
import com.example.item.domain.Bom.BomStatus;
import com.example.item.domain.BomLine;
import com.example.item.domain.Item;
import com.example.item.domain.Uom;
import com.example.item.dto.BomDto;
import com.example.item.dto.BomLineDto;
import com.example.item.dto.BomRequirementDto;
import com.example.item.repository.BomRepository;
import com.example.item.repository.ItemRepository;
import com.example.item.repository.UomRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * BOM rules:
 * <ul>
 *   <li>a BOM starts as DRAFT; only DRAFT BOMs can have lines added/removed or their revision changed;</li>
 *   <li>DRAFT -> ACTIVE needs at least one line and no other ACTIVE BOM for the same product and revision;</li>
 *   <li>ACTIVE -> OBSOLETE and DRAFT -> OBSOLETE are allowed, nothing ever leaves OBSOLETE;</li>
 *   <li>a product cannot be a component of its own BOM; each component may appear once per BOM.</li>
 * </ul>
 */
@Service
@Transactional
public class BomServiceImpl implements BomService {

    static final int QTY_SCALE = 6;

    private final BomRepository bomRepository;
    private final ItemRepository itemRepository;
    private final UomRepository uomRepository;

    public BomServiceImpl(BomRepository bomRepository, ItemRepository itemRepository, UomRepository uomRepository) {
        this.bomRepository = bomRepository;
        this.itemRepository = itemRepository;
        this.uomRepository = uomRepository;
    }

    @Override
    public BomDto createBom(BomDto dto) {
        if (dto.getProductItemId() == null) {
            throw bad("Product item ID is required");
        }
        Item product = itemRepository.findById(dto.getProductItemId()).orElseThrow(() -> bad("Product item not found"));
        String revision = dto.getRevision() == null || dto.getRevision().isBlank() ? "A" : dto.getRevision().trim();
        BomStatus status = dto.getStatus() == null ? BomStatus.DRAFT : dto.getStatus();
        if (status == BomStatus.OBSOLETE) {
            throw bad("A new BOM cannot start as OBSOLETE");
        }

        Bom bom = new Bom();
        bom.setProductItem(product);
        bom.setRevision(revision);
        bom.setStatus(BomStatus.DRAFT);
        if (dto.getEffectiveFrom() != null) {
            bom.setEffectiveFrom(dto.getEffectiveFrom());
        }
        bom.setEffectiveTo(dto.getEffectiveTo());
        assertEffectiveRange(bom);
        bom.setNote(dto.getNote());
        if (dto.getLines() != null) {
            dto.getLines().forEach(line -> addLineTo(bom, line));
        }
        if (status == BomStatus.ACTIVE) {
            activate(bom);
        }
        return new BomDto(bomRepository.save(bom));
    }

    @Override
    @Transactional(readOnly = true)
    public BomDto getBomById(UUID id) {
        return new BomDto(find(id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<BomDto> getAllBoms() {
        return bomRepository.findAll().stream().map(BomDto::new).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BomDto> searchBoms(UUID productItemId, String revision, BomStatus status, Pageable pageable) {
        return bomRepository.search(productItemId,
                revision == null || revision.isBlank() ? null : revision.trim(),
                status,
                pageable).map(BomDto::new);
    }

    @Override
    public BomDto updateBom(BomDto dto) {
        if (dto.getId() == null) {
            throw bad("BOM ID is required");
        }
        Bom bom = find(dto.getId());

        if (dto.getRevision() != null && !dto.getRevision().equals(bom.getRevision())) {
            assertDraft(bom, "change the revision of");
            bom.setRevision(dto.getRevision().trim());
        }
        if (dto.getEffectiveFrom() != null) {
            bom.setEffectiveFrom(dto.getEffectiveFrom());
        }
        if (dto.getEffectiveTo() != null) {
            bom.setEffectiveTo(dto.getEffectiveTo());
        }
        assertEffectiveRange(bom);
        if (dto.getNote() != null) {
            bom.setNote(dto.getNote());
        }
        if (dto.getStatus() != null && dto.getStatus() != bom.getStatus()) {
            transition(bom, dto.getStatus());
        }
        return new BomDto(bomRepository.saveAndFlush(bom));
    }

    @Override
    public void deleteBom(UUID id) {
        Bom bom = find(id);
        if (bom.getStatus() == BomStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An ACTIVE BOM cannot be deleted; make it OBSOLETE");
        }
        bomRepository.delete(bom);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BomLineDto> getBomLinesByBomId(UUID id) {
        return find(id).getLines().stream().map(BomLineDto::new).collect(Collectors.toList());
    }

    @Override
    public BomLineDto addLine(UUID bomId, BomLineDto line) {
        Bom bom = find(bomId);
        assertDraft(bom, "add lines to");
        BomLine added = addLineTo(bom, line);
        bomRepository.saveAndFlush(bom);
        return new BomLineDto(added);
    }

    @Override
    public void removeLine(UUID bomId, UUID lineId) {
        Bom bom = find(bomId);
        assertDraft(bom, "remove lines from");
        BomLine line = bom.getLines().stream().filter(l -> lineId.equals(l.getId())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOM line not found"));
        bom.removeLine(line);
        bomRepository.saveAndFlush(bom);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BomRequirementDto> requirements(UUID bomId, BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw bad("quantity must be positive");
        }
        return find(bomId).getLines().stream().map(line -> {
            BigDecimal net = line.getQtyPer().multiply(quantity).setScale(QTY_SCALE, RoundingMode.HALF_UP);
            BigDecimal gross = grossQuantity(line.getQtyPer(), line.getScrapRate(), quantity);
            return new BomRequirementDto(line.getLineNo(), line.getComponentItem().getId(),
                    line.getComponentItem().getSkuCode(), line.getUom().getId(), net, line.getScrapRate(), gross);
        }).collect(Collectors.toList());
    }

    /** {@code qtyPer * quantity / (1 - scrapRate)}: the scrap rate is the fraction of issued material that is lost. */
    static BigDecimal grossQuantity(BigDecimal qtyPer, BigDecimal scrapRate, BigDecimal quantity) {
        BigDecimal yield = BigDecimal.ONE.subtract(scrapRate == null ? BigDecimal.ZERO : scrapRate);
        return qtyPer.multiply(quantity).divide(yield, QTY_SCALE, RoundingMode.HALF_UP);
    }

    // ----- helpers ---------------------------------------------------------------------------------------------

    private BomLine addLineTo(Bom bom, BomLineDto dto) {
        if (dto == null || dto.componentItemId() == null) {
            throw bad("componentItemId is required for every line");
        }
        if (dto.qtyPer() == null || dto.qtyPer().signum() <= 0) {
            throw bad("qtyPer must be positive");
        }
        BigDecimal scrap = dto.scrapRate() == null ? BigDecimal.ZERO : dto.scrapRate();
        if (scrap.signum() < 0 || scrap.compareTo(BigDecimal.ONE) >= 0) {
            throw bad("scrapRate must be >= 0 and < 1");
        }
        if (dto.componentItemId().equals(bom.getProductItem().getId())) {
            throw bad("A product cannot be a component of its own BOM");
        }
        if (bom.getLines().stream().anyMatch(l -> l.getComponentItem().getId().equals(dto.componentItemId()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Component already present in this BOM");
        }
        Item component = itemRepository.findById(dto.componentItemId())
                .orElseThrow(() -> bad("Component item not found: " + dto.componentItemId()));
        Uom uom = dto.uomId() == null ? component.getBaseUom()
                : uomRepository.findById(dto.uomId()).orElseThrow(() -> bad("UOM not found"));
        int lineNo = dto.lineNo() != null ? dto.lineNo() : nextLineNo(bom);
        if (lineNo <= 0 || bom.getLines().stream().anyMatch(l -> l.getLineNo() == lineNo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Line number " + lineNo + " is invalid or taken");
        }

        BomLine line = new BomLine();
        line.setLineNo(lineNo);
        line.setComponentItem(component);
        line.setQtyPer(dto.qtyPer());
        line.setUom(uom);
        line.setScrapRate(scrap);
        line.setNote(dto.note());
        bom.addLine(line);
        return line;
    }

    private static int nextLineNo(Bom bom) {
        return bom.getLines().stream().mapToInt(BomLine::getLineNo).max().orElse(0) + 10;
    }

    private void transition(Bom bom, BomStatus target) {
        switch (target) {
            case ACTIVE -> {
                if (bom.getStatus() != BomStatus.DRAFT) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "Only a DRAFT BOM can be activated (current: " + bom.getStatus() + ")");
                }
                activate(bom);
            }
            case OBSOLETE -> {
                if (bom.getStatus() == BomStatus.OBSOLETE) {
                    return;
                }
                bom.setStatus(BomStatus.OBSOLETE);
            }
            case DRAFT -> throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A BOM cannot go back to DRAFT; create a new revision instead");
        }
    }

    private void activate(Bom bom) {
        if (bom.getLines().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A BOM needs at least one line to be activated");
        }
        if (bomRepository.existsByProductItemIdAndRevisionAndStatus(bom.getProductItem().getId(), bom.getRevision(),
                BomStatus.ACTIVE)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An ACTIVE BOM already exists for this product and revision " + bom.getRevision());
        }
        bom.setStatus(BomStatus.ACTIVE);
    }

    private static void assertDraft(Bom bom, String action) {
        if (bom.getStatus() != BomStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot " + action + " a " + bom.getStatus() + " BOM; only DRAFT BOMs are editable");
        }
    }

    private static void assertEffectiveRange(Bom bom) {
        if (bom.getEffectiveTo() != null && bom.getEffectiveFrom() != null
                && !bom.getEffectiveTo().isAfter(bom.getEffectiveFrom())) {
            throw bad("effectiveTo must be after effectiveFrom");
        }
    }

    private Bom find(UUID id) {
        return bomRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "BOM not found"));
    }

    private static ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
