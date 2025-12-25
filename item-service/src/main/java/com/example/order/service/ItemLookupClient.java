package com.example.order.service;

import java.util.List;

public interface ItemLookupClient {
    void assertItemsExist(List<ItemReference> items);
}


