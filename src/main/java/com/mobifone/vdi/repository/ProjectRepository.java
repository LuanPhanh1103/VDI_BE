package com.mobifone.vdi.repository;

import com.mobifone.vdi.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, String> {
    boolean existsByName(String name);
    List<Project> findAllByOwner_Id(String ownerId);

//    @Query("""
//           select distinct p from Project p
//             left join fetch p.owner o
//             left join fetch o.virtualDesktops ovd
//             left join fetch p.members m
//             left join fetch m.user mu
//             left join fetch mu.virtualDesktops muvd
//           where p.id = :id
//           """)
//    Optional<Project> findDetailById(@Param("id") String id);
//
//    // findAll với EntityGraph để tránh N+1 khi map owner/members.user
//    @EntityGraph(attributePaths = {"owner", "members", "members.user"})
//    Page<Project> findAllBy(Pageable pageable);
//
//    // search theo name hoặc description (không phân biệt hoa/thường)
//    @EntityGraph(attributePaths = {"owner", "members", "members.user"})
//    Page<Project> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
//            String nameLike, String descLike, Pageable pageable
//    );

    // Kiểm tra user có làm Owner của ít nhất 1 project không
    boolean existsByOwner_Id(String ownerId);

    // Lấy danh sách id project mà user là Owner
    @Query("select p.id from Project p where p.owner.id = :ownerId and p.isDeleted = 0")
    List<String> findIdsByOwner(@Param("ownerId") String ownerId);

    // Admin: list/search tất cả (đã có sẵn)
    @EntityGraph(attributePaths = {"owner", "members", "members.user"})
    Page<Project> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = {"owner", "members", "members.user"})
    Page<Project> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
            String nameLike, String descLike, Pageable pageable
    );

    // Owner: chỉ các project do mình làm chủ + search
    @EntityGraph(attributePaths = {"owner", "members", "members.user"})
    @Query("""
        select p from Project p
        where p.owner.id = :ownerId
          and ( :kw = '' or lower(p.name) like lower(concat('%', :kw, '%'))
                        or lower(p.description) like lower(concat('%', :kw, '%')) )
    """)
    Page<Project> findOwnedProjects(@Param("ownerId") String ownerId,
                                    @Param("kw") String kw,
                                    Pageable pageable);

    // Chi tiết theo id (giữ nguyên)
    @Query("""
           select distinct p from Project p
             left join fetch p.owner o
             left join fetch o.virtualDesktops ovd
             left join fetch p.members m
             left join fetch m.user mu
             left join fetch mu.virtualDesktops muvd
           where p.id = :id
           """)
    Optional<Project> findDetailById(@Param("id") String id);
}
