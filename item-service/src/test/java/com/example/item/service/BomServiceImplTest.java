package com.example.item.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BomServiceImplTest {

    @Mock BomRepository boms;
    @Mock ItemRepository items;
    @Mock UomRepository uoms;

    BomServiceImpl service;
    Uom ea;
    Item product;
    Item steel;
    Item bolt;

    @BeforeEach
    void setUp() {
        service = new BomServiceImpl(boms, items, uoms);
        ea = uom("EA");
        product = item("FG-1");
        steel = item("STEEL");
        bolt = item("BOLT");
        lenient().when(boms.save(any(Bom.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
        lenient().when(boms.saveAndFlush(any(Bom.class))).thenAnswer(inv -> withId(inv.getArgument(0)));
    }

    private Uom uom(String code) {
        Uom u = new Uom();
        u.setId(UUID.randomUUID());
        u.setCode(code);
        u.setName(code);
        return u;
    }

    private Item item(String sku) {
        Item i = new Item();
        i.setId(UUID.randomUUID());
        i.setSkuCode(sku);
        i.setBaseUom(ea);
        i.setActive(true);
        lenient().when(items.findById(i.getId())).thenReturn(Optional.of(i));
        return i;
    }

    private static Bom withId(Bom b) {
        if (b.getId() == null) {
            b.setId(UUID.randomUUID());
        }
        b.getLines().forEach(l -> {
            if (l.getId() == null) {
                l.setId(UUID.randomUUID());
            }
        });
        return b;
    }

    private static BomLineDto line(Item component, String qty, String scrap) {
        return new BomLineDto(null, null, component.getId(), new BigDecimal(qty), null,
                scrap == null ? null : new BigDecimal(scrap), null);
    }

    private BomDto dto(BomStatus status, BomLineDto... lines) {
        BomDto d = new BomDto();
        d.setProductItemId(product.getId());
        d.setStatus(status);
        d.setLines(List.of(lines));
        return d;
    }

    private Bom stored(BomStatus status) {
        Bom b = new Bom();
        b.setId(UUID.randomUUID());
        b.setProductItem(product);
        b.setRevision("A");
        b.setStatus(status);
        BomLine l = new BomLine();
        l.setId(UUID.randomUUID());
        l.setLineNo(10);
        l.setComponentItem(steel);
        l.setQtyPer(new BigDecimal("2"));
        l.setUom(ea);
        l.setScrapRate(BigDecimal.ZERO);
        b.addLine(l);
        when(boms.findById(b.getId())).thenReturn(Optional.of(b));
        return b;
    }

    private static void assertStatus(Throwable t, HttpStatus status) {
        assertThat(t).isInstanceOfSatisfying(ResponseStatusException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(status));
    }

    @Test
    void createStartsAsDraftAndNumbersLinesInTens() {
        BomDto created = service.createBom(dto(null, line(steel, "2.5", "0.1"), line(bolt, "4", null)));

        assertThat(created.getStatus()).isEqualTo(BomStatus.DRAFT);
        assertThat(created.getRevision()).isEqualTo("A");
        assertThat(created.getLines()).extracting(BomLineDto::lineNo).containsExactly(10, 20);
        assertThat(created.getLines().get(1).uomId()).isEqualTo(ea.getId());   // component's base UOM
        assertThat(created.getLines().get(1).scrapRate()).isEqualByComparingTo("0");
    }

    @Test
    void createRejectsBadInput() {
        BomDto noProduct = new BomDto();
        assertThatThrownBy(() -> service.createBom(noProduct)).satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));

        BomDto unknownProduct = new BomDto();
        unknownProduct.setProductItemId(UUID.randomUUID());
        assertThatThrownBy(() -> service.createBom(unknownProduct))
                .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> service.createBom(dto(BomStatus.OBSOLETE)))
                .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.createBom(dto(null, line(product, "1", null))))
                .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));            // own product as component
        assertThatThrownBy(() -> service.createBom(dto(null, line(steel, "0", null))))
                .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));            // non-positive quantity
        assertThatThrownBy(() -> service.createBom(dto(null, line(steel, "1", "1"))))
                .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));            // scrap must be < 1
        assertThatThrownBy(() -> service.createBom(dto(null, line(steel, "1", null), line(steel, "2", null))))
                .satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));               // duplicate component
    }

    @Test
    void creatingAsActiveRequiresLinesAndNoOtherActiveBom() {
        assertThatThrownBy(() -> service.createBom(dto(BomStatus.ACTIVE)))
                .satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));

        when(boms.existsByProductItemIdAndRevisionAndStatus(product.getId(), "A", BomStatus.ACTIVE)).thenReturn(true);
        assertThatThrownBy(() -> service.createBom(dto(BomStatus.ACTIVE, line(steel, "1", null))))
                .satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));

        when(boms.existsByProductItemIdAndRevisionAndStatus(product.getId(), "A", BomStatus.ACTIVE)).thenReturn(false);
        assertThat(service.createBom(dto(BomStatus.ACTIVE, line(steel, "1", null))).getStatus())
                .isEqualTo(BomStatus.ACTIVE);
    }

    @Test
    void draftCanBeActivatedThenRetiredButNeverReopened() {
        Bom bom = stored(BomStatus.DRAFT);
        BomDto activate = new BomDto();
        activate.setId(bom.getId());
        activate.setStatus(BomStatus.ACTIVE);

        assertThat(service.updateBom(activate).getStatus()).isEqualTo(BomStatus.ACTIVE);

        BomDto backToDraft = new BomDto();
        backToDraft.setId(bom.getId());
        backToDraft.setStatus(BomStatus.DRAFT);
        assertThatThrownBy(() -> service.updateBom(backToDraft)).satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));

        BomDto retire = new BomDto();
        retire.setId(bom.getId());
        retire.setStatus(BomStatus.OBSOLETE);
        assertThat(service.updateBom(retire).getStatus()).isEqualTo(BomStatus.OBSOLETE);

        assertThatThrownBy(() -> service.updateBom(activate)).satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
    }

    @Test
    void onlyDraftBomsAreEditable() {
        Bom active = stored(BomStatus.ACTIVE);
        UUID lineId = active.getLines().get(0).getId();

        assertThatThrownBy(() -> service.addLine(active.getId(), line(bolt, "1", null)))
                .satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.removeLine(active.getId(), lineId))
                .satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        BomDto rev = new BomDto();
        rev.setId(active.getId());
        rev.setRevision("B");
        assertThatThrownBy(() -> service.updateBom(rev)).satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.deleteBom(active.getId())).satisfies(t -> assertStatus(t, HttpStatus.CONFLICT));
    }

    @Test
    void linesCanBeAddedAndRemovedWhileDraft() {
        Bom draft = stored(BomStatus.DRAFT);

        BomLineDto added = service.addLine(draft.getId(), line(bolt, "6", "0.05"));
        assertThat(added.lineNo()).isEqualTo(20);
        assertThat(draft.getLines()).hasSize(2);

        service.removeLine(draft.getId(), draft.getLines().get(0).getId());
        assertThat(draft.getLines()).hasSize(1);
        assertThatThrownBy(() -> service.removeLine(draft.getId(), UUID.randomUUID()))
                .satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
    }

    @Test
    void requirementsApplyScrapAsFractionOfIssuedMaterial() {
        Bom bom = stored(BomStatus.ACTIVE);
        bom.getLines().get(0).setQtyPer(new BigDecimal("2.5"));
        bom.getLines().get(0).setScrapRate(new BigDecimal("0.2"));

        List<BomRequirementDto> req = service.requirements(bom.getId(), new BigDecimal("10"));

        assertThat(req).singleElement().satisfies(r -> {
            assertThat(r.componentSku()).isEqualTo("STEEL");
            assertThat(r.netQuantity()).isEqualByComparingTo("25");
            assertThat(r.grossQuantity()).isEqualByComparingTo("31.25");   // 25 / (1 - 0.2)
        });
        assertThatThrownBy(() -> service.requirements(bom.getId(), BigDecimal.ZERO))
                .satisfies(t -> assertStatus(t, HttpStatus.BAD_REQUEST));
    }

    @Test
    void grossQuantityRoundsToSixDecimalsAndHandlesNoScrap() {
        assertThat(BomServiceImpl.grossQuantity(BigDecimal.ONE, new BigDecimal("0.3"), BigDecimal.ONE))
                .isEqualByComparingTo("1.428571");
        assertThat(BomServiceImpl.grossQuantity(new BigDecimal("3"), null, new BigDecimal("7")))
                .isEqualByComparingTo("21");
    }

    @Test
    void unknownBomIsNotFound() {
        UUID id = UUID.randomUUID();
        when(boms.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBomById(id)).satisfies(t -> assertStatus(t, HttpStatus.NOT_FOUND));
    }
}
