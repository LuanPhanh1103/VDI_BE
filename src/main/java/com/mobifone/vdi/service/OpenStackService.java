package com.mobifone.vdi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mobifone.vdi.common.Constants;
import com.mobifone.vdi.dto.request.InstanceRequest;
import com.mobifone.vdi.dto.request.NoVNCRequest;
import com.mobifone.vdi.dto.response.*;
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

import java.util.*;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class OpenStackService {

    ApiStrategyFactory strategyFactory;
    ObjectMapper objectMapper;
    ProvisionPersistService provisionPersistService;

    // Dùng riêng cho vài parse nhanh
    ObjectMapper om = new ObjectMapper();

    private ApiStrategy strategy() {
        return strategyFactory.getStrategy(Constants.OPENSTACK.NAME_SERVICE);
    }

    /** Ghép region (vd: "yha_yoga") với endpoint tương đối trong Constants. */
    private String withRegion(String region, String endpoint) {
        String p = Optional.ofNullable(region).orElse("").trim();
        String e = Optional.ofNullable(endpoint).orElse("");
        if (!e.startsWith("/")) e = "/" + e;
        if (p.isEmpty()) return e;
        if (p.startsWith("/")) return p + e;
        return "/" + p + e;
    }

    // =====================================================================
    // Provision with retry (per-request region)
    // =====================================================================
    public String provisionWithRetry(String mode, InstanceRequest req, String userId, String region) {
        int attempts = 0;
        while (true) {
            try {
                InstanceResponse resp = switch (mode.toLowerCase()) {
                    case "personal"     -> provisionPersonal(req, userId, region);
                    case "organization" -> provisionOrganization(req, userId, region);
                    case "add-resource" -> addResource(req, userId, region);
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
    // Đợi kết quả hạ tầng (nếu bạn còn dùng ở chỗ khác)
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
    // DELETE instance (per-request region)
    // =====================================================================
    @PreAuthorize("hasRole('delete_openstack')")
    public InstanceResponse requestDeleteInstance(String idInstance, String userId, String region) {
        String taskId = UUID.randomUUID().toString();

        // Ghi nhận trạng thái xóa để khi nhận result=true thì dọn DB
        provisionPersistService.createDeleting(taskId, idInstance);

        ApiStrategy strategy = strategyFactory.getStrategy(Constants.OPENSTACK.NAME_SERVICE);

        String path = withRegion(region, Constants.OPENSTACK.ENDPOINT.DELETE_RESOURCE);
        String endpoint = UriComponentsBuilder.fromPath(path)
                .buildAndExpand(idInstance)
                .toUriString();

        String uriWithQuery = UriComponentsBuilder.fromPath(endpoint)
                .queryParam("identifier", taskId)
                .queryParam("user_id", userId)
                .build()
                .toUriString();

        String raw = strategy.callApi(HttpMethod.DELETE, uriWithQuery, null);

        return InstanceResponse.builder()
                .task_id(taskId)
                .status(TaskStatus.DELETING.name())
                .message("Resource is being deleted")
                .raw(raw)
                .build();
    }

    // =====================================================================
    // Volumes (per-request region)
    // =====================================================================
    @PreAuthorize("hasRole('get_openstack_volumes')")
    public PagedResponse<VolumeSummary> getVolumesAsPermissions(String projectId, int page, int size, String region) {
        try {
            ApiStrategy strategy = strategyFactory.getStrategy(Constants.OPENSTACK.NAME_SERVICE);
            String path = withRegion(region, Constants.OPENSTACK.ENDPOINT.VOLUMES_V3);
            String endpoint = UriComponentsBuilder.fromPath(path)
                    .buildAndExpand(projectId)
                    .toUriString();

            String json = strategy.callApi(HttpMethod.GET, endpoint, null);

            JsonNode root = om.readTree(json);
            JsonNode arr = root.path("volumes");

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

            // Phân trang thủ công 1-based
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
    // Images (per-request region)
    // =====================================================================
    @PreAuthorize("hasRole('get_openstack_images')")
    public List<ImagesResponse> getImagesAsPermissions(String region) {
        try {
            String endpoint = withRegion(region, Constants.OPENSTACK.ENDPOINT.IMAGES);
            String responseJson = strategy().callApi(HttpMethod.GET, endpoint, null);

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
    // Flavors (per-request region)
    // =====================================================================
    @PreAuthorize("hasRole('get_openstack_flavors')")
    public PagedResponse<FlavorsResponse> getFlavorsAsPermissions(int page, int size, String region) {
        try {
            String endpoint = withRegion(region, Constants.OPENSTACK.ENDPOINT.FLAVORS);
            String responseJson = strategy().callApi(HttpMethod.GET, endpoint, null);

            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode flavorsNode = root.path("flavors");

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
    // PERSONAL / ORGANIZATION / ADD_RESOURCE (per-request region)
    // =====================================================================
    @PreAuthorize("hasRole('create_openstack_instance')")
    public InstanceResponse provisionPersonal(InstanceRequest req, String userId, String region) {
        String taskId = UUID.randomUUID().toString();
        provisionPersistService.createProvisioning(taskId, 1);

        Map<String, Object> body = new HashMap<>();
        body.put("base_vol_id", req.getBase_vol_id());
        body.put("vol_size",    req.getVol_size());
        body.put("flavor_id",   req.getFlavor_id());
        body.put("vol_type",    req.getVol_type());

        String endpoint = UriComponentsBuilder.fromPath(
                        withRegion(region, Constants.OPENSTACK.ENDPOINT.PROVISION_PERSONAL))
                .queryParam("identifier", taskId)
                .queryParam("user_id", userId)
                .build().toUriString();

        String raw = strategy().callApi(HttpMethod.POST, endpoint, body);

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

        Map<String, Object> body = new HashMap<>();
        body.put("base_vol_id", req.getBase_vol_id());
        body.put("vol_size",    req.getVol_size());
        body.put("flavor_id",   req.getFlavor_id());
        body.put("vol_type",    req.getVol_type());
        body.put("count", 1);

        String endpoint = UriComponentsBuilder.fromPath(
                        withRegion(region, Constants.OPENSTACK.ENDPOINT.PROVISION_ORG))
                .queryParam("identifier", taskId)
                .queryParam("user_id", userId)
                .build().toUriString();

        String raw = strategy().callApi(HttpMethod.POST, endpoint, body);

        return InstanceResponse.builder()
                .task_id(taskId)
                .status(TaskStatus.PROVISIONING.name())
                .message("Infra is being provisioned (organization)")
                .raw(raw)
                .build();
    }

    @PreAuthorize("hasRole('create_openstack_instance')")
    public InstanceResponse addResource(InstanceRequest req, String userId, String region) {
        String taskId = UUID.randomUUID().toString();
        int count = Math.max(1, Math.toIntExact(Optional.ofNullable(req.getCount()).orElse(1L)));
        provisionPersistService.createProvisioning(taskId, count);

        Map<String, Object> body = new HashMap<>();
        body.put("base_vol_id", req.getBase_vol_id());
        body.put("vol_size",    req.getVol_size());
        body.put("flavor_id",   req.getFlavor_id());
        body.put("vol_type",    req.getVol_type());
        body.put("count",       count);

        String endpoint = UriComponentsBuilder.fromPath(
                        withRegion(region, Constants.OPENSTACK.ENDPOINT.ADD_RESOURCE))
                .queryParam("identifier", taskId)
                .queryParam("user_id", userId)
                .build().toUriString();

        String raw = strategy().callApi(HttpMethod.POST, endpoint, body);

        return InstanceResponse.builder()
                .task_id(taskId)
                .status(TaskStatus.PROVISIONING.name())
                .message("Infra is being provisioned (add_resource)")
                .raw(raw)
                .build();
    }

    // =====================================================================
    // noVNC (per-request region) – nếu backend yêu cầu đi qua region
    // =====================================================================
    @PreAuthorize("hasRole('get_openstack_noVNC')")
    public NoVNCResponse getConsoleUrl(NoVNCRequest noVNCRequest, String region) {
        try {
            String endpoint = UriComponentsBuilder
                    .fromPath(withRegion(region, Constants.OPENSTACK.ENDPOINT.NOVNC))
                    .buildAndExpand(noVNCRequest.getInstance_id())
                    .toUriString();

            String responseJson = strategy().callApi(HttpMethod.GET, endpoint, null);

            JsonNode root = objectMapper.readTree(responseJson);
            String url = root.path("console").path("url").asText();
            if (url == null || url.isEmpty()) throw new AppException(ErrorCode.API_VNC_ERR);

            return NoVNCResponse.builder().url(url).build();

        } catch (Exception e) {
            log.error("Failed to get console URL for server {}: {}", noVNCRequest.getInstance_id(), e.getMessage());
            throw new AppException(ErrorCode.API_VNC_ERR);
        }
    }
}
