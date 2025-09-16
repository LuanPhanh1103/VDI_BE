package com.mobifone.vdi.repository;

import com.mobifone.vdi.entity.VirtualDesktop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VirtualDesktopRepository extends JpaRepository<VirtualDesktop, String> {
    boolean existsByName(String name);
    List<VirtualDesktop> findAllByUserId(String id);
    List<VirtualDesktop> findAllByProject_Id(String projectId);
    List<VirtualDesktop> findAllByProject_Owner_Id(String ownerId);
    List<VirtualDesktop> findAllByProject_IdAndUser_Id(String projectId, String userId);

    Optional<VirtualDesktop> findByIdInstance(String s);

    boolean existsByPortPublic(String portPublic);
    List<VirtualDesktop> findByJobId(String jobId);


    // ADMIN: tất cả VDI (+ optional projectId, search)
    @EntityGraph(attributePaths = {"project", "user"})
    @Query("""
        select vd from VirtualDesktop vd
        where ( :projectId is null or vd.project.id = :projectId )
          and ( :kw = '' or lower(vd.name)    like lower(concat('%', :kw, '%'))
                        or lower(vd.ipLocal)  like lower(concat('%', :kw, '%'))
                        or lower(vd.ipPublic) like lower(concat('%', :kw, '%')) )
    """)
    Page<VirtualDesktop> searchAllVDIs(@Param("projectId") String projectId,
                                       @Param("kw") String kw,
                                       Pageable pageable);

    // OWNER: chỉ các VDI thuộc những project mình làm chủ
    @EntityGraph(attributePaths = {"project", "user"})
    @Query("""
        select vd from VirtualDesktop vd
        where vd.project.id in :projectIds
          and ( :projectId is null or vd.project.id = :projectId )
          and ( :kw = '' or lower(vd.name)    like lower(concat('%', :kw, '%'))
                        or lower(vd.ipLocal)  like lower(concat('%', :kw, '%'))
                        or lower(vd.ipPublic) like lower(concat('%', :kw, '%')) )
    """)
    Page<VirtualDesktop> searchVDIsInProjects(@Param("projectIds") Collection<String> projectIds,
                                              @Param("projectId") String projectId,
                                              @Param("kw") String kw,
                                              Pageable pageable);

    // MEMBER: chỉ VDI gán cho chính mình
    @EntityGraph(attributePaths = {"project", "user"})
    @Query("""
        select vd from VirtualDesktop vd
        where vd.user.id = :uid
          and ( :projectId is null or vd.project.id = :projectId )
          and ( :kw = '' or lower(vd.name)    like lower(concat('%', :kw, '%'))
                        or lower(vd.ipLocal)  like lower(concat('%', :kw, '%'))
                        or lower(vd.ipPublic) like lower(concat('%', :kw, '%')) )
    """)
    Page<VirtualDesktop> searchAssignedVDIs(@Param("uid") String uid,
                                            @Param("projectId") String projectId,
                                            @Param("kw") String kw,
                                            Pageable pageable);
}
