package com.mobifone.vdi.repository;

import com.mobifone.vdi.entity.VirtualDesktop;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VirtualDesktopRepository extends JpaRepository<VirtualDesktop, String> {

    // ADDED: tìm DC của 1 project (có thể thêm filter region nếu bạn cần)
    Optional<VirtualDesktop> findFirstByProject_IdAndIsDomainControllerTrue(String projectId);

    // ADDED (nếu muốn lọc theo region)
    Optional<VirtualDesktop> findFirstByProject_IdAndRegionAndIsDomainControllerTrue(String projectId, String region);

    boolean existsByPortWinRmPublic(String portWinRmPublic);

    boolean existsByName(String name);

    List<VirtualDesktop> findAllByProject_Id(String projectId);
    List<VirtualDesktop> findAllByProject_IdAndUser_Id(String projectId, String userId);

    Optional<VirtualDesktop> findByIdInstance(String s);

    boolean existsByPortPublic(String portPublic);
    List<VirtualDesktop> findByJobId(String jobId);


    // ADMIN: tất cả VDI (+ optional projectId, search)
    @EntityGraph(attributePaths = {"project", "user"})
    @Query("""
    select vd from VirtualDesktop vd
    where ( :projectId is null or vd.project.id = :projectId )
      and ( :region is null or vd.region = :region )
      and ( :kw = '' or lower(vd.name)    like lower(concat('%', :kw, '%'))
                    or lower(vd.ipLocal)  like lower(concat('%', :kw, '%'))
                    or lower(vd.ipPublic) like lower(concat('%', :kw, '%')) )
""")
    Page<VirtualDesktop> searchAllVDIs(@Param("projectId") String projectId,
                                       @Param("kw") String kw,
                                       @Param("region") String region,
                                       Pageable pageable);

    // OWNER: chỉ các VDI thuộc những project mình làm chủ
    @EntityGraph(attributePaths = {"project", "user"})
    @Query("""
    select vd from VirtualDesktop vd
    where vd.project.id in :projectIds
      and ( :projectId is null or vd.project.id = :projectId )
      and ( :region is null or vd.region = :region )
      and ( :kw = '' or lower(vd.name)    like lower(concat('%', :kw, '%'))
                    or lower(vd.ipLocal)  like lower(concat('%', :kw, '%'))
                    or lower(vd.ipPublic) like lower(concat('%', :kw, '%')) )
""")
    Page<VirtualDesktop> searchVDIsInProjects(@Param("projectIds") Collection<String> projectIds,
                                              @Param("projectId") String projectId,
                                              @Param("kw") String kw,
                                              @Param("region") String region,
                                              Pageable pageable);

    // MEMBER: chỉ VDI gán cho chính mình
    @EntityGraph(attributePaths = {"project", "user"})
    @Query("""
    select vd from VirtualDesktop vd
    where vd.user.id = :uid
      and ( :projectId is null or vd.project.id = :projectId )
      and ( :region is null or vd.region = :region )
      and ( :kw = '' or lower(vd.name)    like lower(concat('%', :kw, '%'))
                    or lower(vd.ipLocal)  like lower(concat('%', :kw, '%'))
                    or lower(vd.ipPublic) like lower(concat('%', :kw, '%')) )
""")
    Page<VirtualDesktop> searchAssignedVDIs(@Param("uid") String uid,
                                            @Param("projectId") String projectId,
                                            @Param("kw") String kw,
                                            @Param("region") String region,
                                            Pageable pageable);
}
