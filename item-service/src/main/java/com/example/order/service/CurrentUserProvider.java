package com.example.order.service;

public interface CurrentUserProvider {
    /** Stable id of the authenticated caller: the user's UUID ({@code uid}), falling back to the token subject. */
    String getCurrentUserId();
}
