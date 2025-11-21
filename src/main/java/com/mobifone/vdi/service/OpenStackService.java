package com.mobifone.vdi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobifone.vdi.common.Constants;
import com.mobifone.vdi.dto.request.InstanceRequest;
import com.mobifone.vdi.dto.request.NoVNCRequest;
import com.mobifone.vdi.dto.response.*;
import com.mobifone.vdi.entity.VirtualDesktop;
import com.mobifone.vdi.entity.enumeration.TaskStatus;
import com.mobifone.vdi.exception.AppException;
import com.mobifone.vdi.exception.ErrorCode;
import com.mobifone.vdi.webClient.ApiStrategy;
import com.mobifone.vdi.webClient.ApiStrategyFactory;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class OpenStackService {

    ApiStrategyFactory strategyFactory;
    ObjectMapper objectMapper;
    ProvisionPersistService provisionPersistService;

    VirtualDesktopService virtualDesktopService;

    // Parser phụ
    ObjectMapper om = new ObjectMapper();

    private ApiStrategy strategy() {
        return strategyFactory.getStrategy(Constants.OPENSTACK.NAME_SERVICE);
    }

    // =====================================================================
    // Provision with retry
    // =====================================================================
    public String provisionWithRetry(String mode, InstanceRequest req, String userId, String region) {
        int attempts = 0;
        while (true) {
            try {
                InstanceResponse resp = switch (mode.toLowerCase()) {
                    case "personal"     -> provisionPersonal(req, userId, region);
                    case "organization" -> provisionOrganization(req, userId, region);
                    case "add-resource" -> addResource(req, userId, region);
                    case "add-resource-for-personal" -> addResourceForPersonal(req, userId, region);
                    default -> throw new IllegalArgumentException("Unsupported mode: " + mode);
                };
                return resp.getTask_id();
            } catch (Exception e) {
                if (++attempts > 1) throw e;
                try { Thread.sleep(10_000L); } catch (InterruptedException ignored) {}
            }
        }
    }

    // =====================================================================
    // Đợi kết quả infra (nếu cần dùng chỗ khác)
    // =====================================================================
    @lombok.SneakyThrows
    public InfraView waitUntilInfraSuccessOrTimeout(String taskId, int timeoutMinutes) {
        long deadline = System.currentTimeMillis() + timeoutMinutes * 60_000L;
        while (System.currentTimeMillis() < deadline) {
            Optional<Map<String,Object>> v = provisionPersistService.view(taskId);
            if (v.isPresent()) {
                var m = v.get();
                String status = String.valueOf(m.get("status"));
                if ("SUCCESS".equals(status)) {
                    @SuppressWarnings("unchecked")
                    List<Map<String,Object>> instances = (List<Map<String,Object>>) m.get("instances");
                    return new InfraView(instances);
                }
                if ("FAILED".equals(status)) {
                    String err = String.valueOf(m.get("error_message"));
                    log.error("API_PROVISION_ERR: {}", err);
                    throw new AppException(ErrorCode.API_PROVISION_ERR);
                }
            }
            Thread.sleep(5_000L);
        }
        throw new AppException(ErrorCode.API_TIMEOUT);
    }

    @Getter
    public static class InfraView {
        private final List<Map<String,Object>> instances;
        public InfraView(List<Map<String,Object>> instances) { this.instances = instances; }
    }

    // =====================================================================
    // Destroy toàn infra/project (nếu dùng)
    // =====================================================================
    @PreAuthorize("hasRole('delete_project')")
    public InstanceResponse requestDestroyInfra(String ownerUserId, String projectId, String infraId, String region) {

        String taskId = UUID.randomUUID().toString();

        // store infraId đúng chuẩn
        provisionPersistService.createDestroyingProject(taskId, projectId, ownerUserId);

        // tạo URL
        String endpoint = UriComponentsBuilder
                .fromPath(Constants.OPENSTACK.ENDPOINT.DESTROY_INFRA)
                .buildAndExpand(infraId)
                .toUriString();

        String uri = UriComponentsBuilder.fromPath(endpoint)
                .queryParam("identifier", taskId)
                .queryParam("user_id", ownerUserId)
                .toUriString();

        // CHỈ cần gọi uri này, không gọi endpoint
        String raw = strategy().callApi(HttpMethod.DELETE, uri, null, region);

        return InstanceResponse.builder()
                .task_id(taskId)
                .status(TaskStatus.DELETING.name())
                .message("Destroy infra requested")
                .raw(raw)
                .build();
    }

    // =====================================================================
    // DELETE 1 instance
    // =====================================================================
    @PreAuthorize("hasRole('delete_openstack')")
    public InstanceResponse requestDeleteInstance(String idInstance, String userId, String region) {
        String infraId = virtualDesktopService.findByIdInstanceOpt(idInstance)
                .map(VirtualDesktop::getInfraId)
                .orElseThrow(() -> new AppException(ErrorCode.INSTANCE_NOT_FOUND_OR_NO_INFRA_ID));

        String taskId = UUID.randomUUID().toString();
        provisionPersistService.createDeleting(taskId, idInstance);

        String endpoint = UriComponentsBuilder.fromPath(Constants.OPENSTACK.ENDPOINT.DELETE_RESOURCE)
                .buildAndExpand(infraId, idInstance)
                .toUriString();

        String uriWithQuery = UriComponentsBuilder.fromPath(endpoint)
                .queryParam("identifier", taskId)
                .queryParam("user_id", userId)
                .build()
                .toUriString();

        String raw = strategy().callApi(HttpMethod.DELETE, uriWithQuery, null, region);

        return InstanceResponse.builder()
                .task_id(taskId)
                .status(TaskStatus.DELETING.name())
                .message("Resource is being deleted")
                .raw(raw)
                .build();
    }


    // =====================================================================
    // VOLUMES (lọc name bắt đầu bằng "bu_cloud") + phân trang
    // =====================================================================
    @PreAuthorize("hasRole('get_openstack_volumes')")
    public PagedResponse<VolumeSummary> getVolumesAsPermissions(String projectId, int page, int size, String region) {
        try {
            String endpoint = UriComponentsBuilder.fromPath(Constants.OPENSTACK.ENDPOINT.VOLUMES_V3)
                    .buildAndExpand(projectId)
                    .toUriString();

            String json = strategy().callApi(HttpMethod.GET, endpoint, null, region);

            JsonNode root = om.readTree(json);
            JsonNode arr = root.path("data").path("volumes");

            List<VolumeSummary> all = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode v : arr) {
                    String name = v.path("name").asText();
                    if (name != null && name.startsWith("bu_cloud")) {
                        all.add(VolumeSummary.builder()
                                .id(v.path("id").asText())
                                .name(name)
                                .build());
                    }
                }
            }

            int currentPage = Math.max(page, 1);
            int fromIndex = (currentPage - 1) * size;
            if (fromIndex >= all.size()) {
                return PagedResponse.<VolumeSummary>builder()
                        .data(List.of())
                        .page(currentPage)
                        .size(size)
                        .totalElements(all.size())
                        .totalPages((int) Math.ceil((double) all.size() / size))
                        .build();
            }
            int toIndex = Math.min(fromIndex + size, all.size());
            List<VolumeSummary> paged = all.subList(fromIndex, toIndex);

            return PagedResponse.<VolumeSummary>builder()
                    .data(paged)
                    .page(currentPage)
                    .size(size)
                    .totalElements(all.size())
                    .totalPages((int) Math.ceil((double) all.size() / size))
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse volumes response: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.API_VOLUME_ERR);
        }
    }

    // =====================================================================
    // IMAGES
    // =====================================================================
    @PreAuthorize("hasRole('get_openstack_images')")
    public List<ImagesResponse> getImagesAsPermissions(String region) {
        try {
            String endpoint = Constants.OPENSTACK.ENDPOINT.IMAGES;
            String responseJson = strategy().callApi(HttpMethod.GET, endpoint, null, region);

            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode imagesNode = root.path("images");

            List<ImagesResponse> result = new ArrayList<>();
            if (imagesNode.isArray()) {
                for (JsonNode image : imagesNode) {
                    String name = image.path("name").asText();
                    String id   = image.path("id").asText();
                    result.add(ImagesResponse.builder()
                            .name(name)
                            .imageId(id)
                            .build());
                }
            }
            return result;

        } catch (Exception e) {
            log.error("Failed to parse images response: {}", e.getMessage());
            throw new AppException(ErrorCode.API_IMAGES_ERR);
        }
    }

    // =====================================================================
    // FLAVORS + phân trang
    // =====================================================================
    @PreAuthorize("hasRole('get_openstack_flavors')")
    public PagedResponse<FlavorsResponse> getFlavorsAsPermissions(int page, int size, String region) {
        try {
            String endpoint = Constants.OPENSTACK.ENDPOINT.FLAVORS;
            String responseJson = strategy().callApi(HttpMethod.GET, endpoint, null, region);

            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode flavorsNode = root.path("data").path("flavors");

            List<FlavorsResponse> all = new ArrayList<>();
            if (flavorsNode.isArray()) {
                for (JsonNode f : flavorsNode) {
                    String name = f.path("name").asText();
                    String id   = f.path("id").asText();
                    String ram  = f.path("ram").asText();
                    String disk = f.path("disk").asText();
                    String cpu  = f.path("vcpus").asText();

                    all.add(FlavorsResponse.builder()
                            .name(name)
                            .flavorId(id)
                            .cpu(cpu)
                            .disk(disk)
                            .ram(ram)
                            .build());
                }
            }

            int currentPage = Math.max(page, 1);
            int fromIndex = (currentPage - 1) * size;
            if (fromIndex >= all.size()) {
                return PagedResponse.<FlavorsResponse>builder()
                        .data(List.of())
                        .page(currentPage)
                        .size(size)
                        .totalElements(all.size())
                        .totalPages((int) Math.ceil((double) all.size() / size))
                        .build();
            }
            int toIndex = Math.min(fromIndex + size, all.size());
            List<FlavorsResponse> paged = all.subList(fromIndex, toIndex);

            return PagedResponse.<FlavorsResponse>builder()
                    .data(paged)
                    .page(currentPage)
                    .size(size)
                    .totalElements(all.size())
                    .totalPages((int) Math.ceil((double) all.size() / size))
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse flavors response: {}", e.getMessage(), e);
            throw new AppException(ErrorCode.API_FLAVOR_ERR);
        }
    }

    // =====================================================================
    // PERSONAL / ORGANIZATION / ADD_RESOURCE
    // =====================================================================
    @PreAuthorize("hasRole('create_openstack_instance')")
    public InstanceResponse provisionPersonal(InstanceRequest req, String userId, String region) {
        String taskId = UUID.randomUUID().toString();
        provisionPersistService.createProvisioning(taskId, 1);

        var body = new java.util.HashMap<String, Object>();
        body.put("base_vol_id", req.getBase_vol_id());
        body.put("vol_size",    req.getVol_size());
        body.put("flavor_id",   req.getFlavor_id());
        body.put("vol_type",    req.getVol_type());

        String endpoint = UriComponentsBuilder.fromPath(Constants.OPENSTACK.ENDPOINT.PROVISION_PERSONAL)
                .queryParam("identifier", taskId)
                .queryParam("user_id", userId)
                .build()
                .toUriString();

        String raw = strategy().callApi(HttpMethod.POST, endpoint, body, region);

        return InstanceResponse.builder()
                .task_id(taskId)
                .status(TaskStatus.PROVISIONING.name())
                .message("Infra is being provisioned (personal)")
                .raw(raw)
                .build();
    }

    @PreAuthorize("hasRole('create_openstack_instance')")
    public InstanceResponse provisionOrganization(InstanceRequest req, String userId, String region) {
        String taskId = UUID.randomUUID().toString();
        provisionPersistService.createProvisioning(taskId, 1);

        var body = new java.util.HashMap<String, Object>();
        body.put("base_vol_id", req.getBase_vol_id());
        body.put("vol_size",    req.getVol_size());
        body.put("flavor_id",   req.getFlavor_id());
        body.put("vol_type",    req.getVol_type());
        body.put("count",       1);

        String endpoint = UriComponentsBuilder.fromPath(Constants.OPENSTACK.ENDPOINT.PROVISION_ORG)
                .queryParam("identifier", taskId)
                .queryParam("user_id", userId)
                .build()
                .toUriString();

        String raw = strategy().callApi(HttpMethod.POST, endpoint, body, region);

        return InstanceResponse.builder()
                .task_id(taskId)
                .status(TaskStatus.PROVISIONING.name())
                .message("Infra is being provisioned (organization)")
                .raw(raw)
                .build();
    }

    @PreAuthorize("hasRole('create_openstack_instance')")
    public InstanceResponse addResourceForPersonal(InstanceRequest req, String userId, String region) {
        String taskId = UUID.randomUUID().toString();
        int count = Math.max(1, Math.toIntExact(Optional.ofNullable(req.getCount()).orElse(1L)));
        provisionPersistService.createProvisioning(taskId, count);

        // ✅ LẤY infraId từ 1 VDI personal bất kỳ của user
        String infraId = virtualDesktopService
                .findAnyPersonalVDI(userId, region)
                .map(VirtualDesktop::getInfraId)
                .orElseThrow(() -> new AppException(ErrorCode.OPENSTACK_SERVICE_ADD_RESOURCE_PERSONAL));

        var body = new java.util.HashMap<String, Object>();
        body.put("base_vol_id", req.getBase_vol_id());
        body.put("vol_size",    req.getVol_size());
        body.put("flavor_id",   req.getFlavor_id());
        body.put("vol_type",    req.getVol_type());
        body.put("count",       count);

        // ✅ /vdi/add_resource/{infraId}/personal
        String endpoint = UriComponentsBuilder
                .fromPath(Constants.OPENSTACK.ENDPOINT.ADD_RESOURCE_PERSONAL)
                .buildAndExpand(infraId)
                .toUriString();

        // vẫn truyền identifier & user_id dạng query
        String uriWithQuery = UriComponentsBuilder
                .fromPath(endpoint)
                .queryParam("identifier", taskId)
                .queryParam("user_id", userId)
                .build()
                .toUriString();

        String raw = strategy().callApi(HttpMethod.POST, uriWithQuery, body, region);

        return InstanceResponse.builder()
                .task_id(taskId)
                .status(TaskStatus.PROVISIONING.name())
                .message("Infra is being provisioned (add_resource_personal)")
                .raw(raw)
                .build();
    }


    @PreAuthorize("hasRole('create_openstack_instance')")
    public InstanceResponse addResource(InstanceRequest req, String userId, String region) {
        String taskId = UUID.randomUUID().toString();
        int count = Math.max(1, Math.toIntExact(Optional.ofNullable(req.getCount()).orElse(1L)));
        provisionPersistService.createProvisioning(taskId, count);

        // ✅ LẤY infraId từ 1 VDI bất kỳ trong project (dùng projectId trong InstanceRequest)
        String projectId = req.getProjectId();
        String infraId = virtualDesktopService
                .findAnyByProject(projectId, region)
                .map(VirtualDesktop::getInfraId)
                .orElseThrow(() -> new AppException(ErrorCode.OPENSTACK_SERVICE_ADD_RESOURCE_ORG));

        var body = new java.util.HashMap<String, Object>();
        body.put("base_vol_id", req.getBase_vol_id());
        body.put("vol_size",    req.getVol_size());
        body.put("flavor_id",   req.getFlavor_id());
        body.put("vol_type",    req.getVol_type());
        body.put("count",       count);

        // ✅ /vdi/add_resource/{infraId}/organization
        String endpoint = UriComponentsBuilder
                .fromPath(Constants.OPENSTACK.ENDPOINT.ADD_RESOURCE_ORG)
                .buildAndExpand(infraId)
                .toUriString();

        String uriWithQuery = UriComponentsBuilder
                .fromPath(endpoint)
                .queryParam("identifier", taskId)
                .queryParam("user_id", userId)
                .build()
                .toUriString();

        String raw = strategy().callApi(HttpMethod.POST, uriWithQuery, body, region);

        return InstanceResponse.builder()
                .task_id(taskId)
                .status(TaskStatus.PROVISIONING.name())
                .message("Infra is being provisioned (add_resource_org)")
                .raw(raw)
                .build();
    }


    // =====================================================================
    // noVNC
    // =====================================================================
    @PreAuthorize("hasRole('get_openstack_noVNC')")
    public NoVNCResponse getConsoleUrl(NoVNCRequest noVNCRequest, String region) {
        try {
            String endpoint = UriComponentsBuilder
                    .fromPath(Constants.OPENSTACK.ENDPOINT.NOVNC)
                    .buildAndExpand(noVNCRequest.getInstance_id())
                    .toUriString();

            String responseJson = strategy().callApi(HttpMethod.GET, endpoint, null, region);

            JsonNode root = objectMapper.readTree(responseJson);
            String url = root.path("console").path("url").asText();
            if (url == null || url.isEmpty()) throw new AppException(ErrorCode.API_VNC_ERR);

            // ✅ Hậu xử lý URL
            try {
                java.net.URI original = new java.net.URI(url);
                String newUrl = String.format(
                        "https://cloud.mobifone.vn:%d/hn02%s",
                        original.getPort(),
                        original.getRawPath() + (original.getRawQuery() != null ? "?" + original.getRawQuery() : "")
                );

                return NoVNCResponse.builder().url(newUrl).build();
            } catch (Exception ex) {
                log.error("Failed to rewrite noVNC URL {}: {}", url, ex.getMessage());
                throw new AppException(ErrorCode.API_VNC_ERR);
            }

        } catch (Exception e) {
            log.error("Failed to get console URL for server {}: {}", noVNCRequest.getInstance_id(), e.getMessage());
            throw new AppException(ErrorCode.API_VNC_ERR);
        }
    }

}
