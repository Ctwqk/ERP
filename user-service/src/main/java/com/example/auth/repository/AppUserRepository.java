package com.example.auth.repository;

import com.example.auth.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AppUserRepository extends JpaRepository<AppUser, UUID> {
    Optional<AppUser> findByEmail(String email);

    @Query(value = """
            select * from app_user
             where (:keyword is null or lower(name) like lower(concat('%', :keyword, '%'))
                    or lower(email) like lower(concat('%', :keyword, '%')))
               and (:active is null or active = :active)
            """,
            countQuery = """
            select count(*) from app_user
             where (:keyword is null or lower(name) like lower(concat('%', :keyword, '%'))
                    or lower(email) like lower(concat('%', :keyword, '%')))
               and (:active is null or active = :active)
            """,
            nativeQuery = true)
    Page<AppUser> search(@Param("keyword") String keyword,
                         @Param("active") Boolean active,
                         Pageable pageable);

    @Modifying(clearAutomatically = true)
    @Query(value = """
            update app_user
               set name = coalesce(:name, name),
                   email = coalesce(:email, email),
                   password_hash = coalesce(:passwordHash, password_hash),
                   active = coalesce(:active, active),
                   updated_at = now()
             where id = :id
            """, nativeQuery = true)
    int patchUser(@Param("id") UUID id,
                  @Param("name") String name,
                  @Param("email") String email,
                  @Param("passwordHash") String passwordHash,
                  @Param("active") Boolean active);
}
