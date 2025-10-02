package com.mobifone.vdi.repository;

import com.mobifone.vdi.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByUsername(String username);

    @Query("""
        select distinct u
        from User u
        left join fetch u.roles r
        left join fetch r.permissions
        where u.username = :username
    """)
    Optional<User> findByUsernameWithRolesAndPermissions(@Param("username") String username);

    // ADMIN: tất cả user (kèm roles)
    @EntityGraph(attributePaths = {"roles"})
    @Query("select u from User u")
    Page<User> findAllWithRoles(Pageable pageable);

    @EntityGraph(attributePaths = {"roles"})
    @Query("""
        select u from User u
        where lower(u.firstName) like lower(concat('%', :kw, '%'))
           or lower(u.lastName)  like lower(concat('%', :kw, '%'))
           or lower(u.username)  like lower(concat('%', :kw, '%'))
           or lower(u.email)     like lower(concat('%', :kw, '%'))
    """)
    Page<User> searchAllUsers(@Param("kw") String kw, Pageable pageable);

    // OWNER: chỉ những user thuộc các project mình làm chủ (bao gồm chính Owner và mọi member)
    @EntityGraph(attributePaths = {"roles"})
    @Query("""
        select distinct u from User u
        where u.id = :ownerId
           or exists (
                select 1 from UserProject up
                where up.user.id = u.id
                  and up.project.owner.id = :ownerId
           )
    """)
    Page<User> findUsersInOwnerScope(@Param("ownerId") String ownerId, Pageable pageable);

    @EntityGraph(attributePaths = {"roles"})
    @Query("""
        select distinct u from User u
        where ( u.id = :ownerId
               or exists (
                    select 1 from UserProject up
                    where up.user.id = u.id
                      and up.project.owner.id = :ownerId
               )
        )
        and (
             :kw = '' or
             lower(u.firstName) like lower(concat('%', :kw, '%')) or
             lower(u.lastName)  like lower(concat('%', :kw, '%')) or
             lower(u.username)  like lower(concat('%', :kw, '%')) or
             lower(u.email)     like lower(concat('%', :kw, '%'))
        )
    """)
    Page<User> searchUsersInOwnerScope(@Param("ownerId") String ownerId,
                                       @Param("kw") String kw,
                                       Pageable pageable);
}
