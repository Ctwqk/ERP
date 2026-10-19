package com.example.task.service;

import java.util.UUID;

public interface CurrentUserProvider {
    /** The authenticated caller's user id (the {@code uid} claim of the access token). */
    UUID getCurrentUserId();
}
