package com.example.order.integration;

import java.util.List;

import com.example.order.dto.ItemReference;

public interface ItemLookupClient {
    void assertItemsExist(List<ItemReference> items);
}


