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

    private ApiStrategy strategy() {
        return strategyFactory.getStrategy(Constants.OPENSTACK.NAME_SERVICE);
    }

    @PreAuthorize("hasRole('get_openstack_images')")
    public List<ImagesResponse> getImagesAsPermissions() {
        try {
//            ApiStrategy strategy = strategyFactory.getStrategy(Constants.OPENSTACK.NAME_SERVICE);
            String responseJson = strategy().callApi(HttpMethod.GET, Constants.OPENSTACK.ENDPOINT.IMAGES, null);

            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode imagesNode = root.path("images");

            List<ImagesResponse> result = new ArrayList<>();

            if (imagesNode.isArray()) {
                for (JsonNode image : imagesNode) {
                    String name = image.path("name").asText();
                    String id = image.path("id").asText();

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

    @PreAuthorize("hasRole('get_openstack_flavors')")
    public PagedResponse<FlavorsResponse> getFlavorsAsPermissions(int page, int size) {
        try {
//            ApiStrategy strategy = strategyFactory.getStrategy(Constants.OPENSTACK.NAME_SERVICE);
            String responseJson = strategy().callApi(HttpMethod.GET, Constants.OPENSTACK.ENDPOINT.FLAVORS, null);

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

            // Phân trang thủ công 1-based
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

    /** PERSONAL: luôn 1 máy; identifier và user_id ở query */
    @PreAuthorize("hasRole('create_openstack_instance')")
    public InstanceResponse provisionPersonal(InstanceRequest req, String userId) {
        String taskId = UUID.randomUUID().toString();
        provisionPersistService.createProvisioning(taskId, 1);

        Map<String, Object> body = new HashMap<>();
        body.put("base_vol_id", req.getBase_vol_id());
        body.put("vol_size",   req.getVol_size());
        body.put("flavor_id",  req.getFlavor_id());
        body.put("vol_type", req.getVol_type());

        String endpoint = UriComponentsBuilder.fromPath("/vdi/provision_infra/personal")
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

    /** ORGANIZATION: giới hạn 1 máy; vẫn nhận vol_type nếu có */
    @PreAuthorize("hasRole('create_openstack_instance')")
    public InstanceResponse provisionOrganization(InstanceRequest req, String userId) {
        String taskId = UUID.randomUUID().toString();
        provisionPersistService.createProvisioning(taskId, 1);

        Map<String, Object> body = new HashMap<>();
        body.put("base_vol_id", req.getBase_vol_id());
        body.put("vol_size",   req.getVol_size());
        body.put("flavor_id",  req.getFlavor_id());
        body.put("vol_type", req.getVol_type());
        // count bị ép = 1
        body.put("count", 1);

        String endpoint = UriComponentsBuilder.fromPath("/vdi/provision_infra/organization")
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

    /** ADD_RESOURCE: có thể nhiều máy */
    @PreAuthorize("hasRole('create_openstack_instance')")
    public InstanceResponse addResource(InstanceRequest req, String userId) {
        String taskId = UUID.randomUUID().toString();
        int count = Math.max(1, Math.toIntExact(Optional.ofNullable(req.getCount()).orElse(1L)));
        provisionPersistService.createProvisioning(taskId, count);

        Map<String, Object> body = new HashMap<>();
        body.put("base_vol_id", req.getBase_vol_id());
        body.put("vol_size",   req.getVol_size());
        body.put("flavor_id",  req.getFlavor_id());
        body.put("vol_type", req.getVol_type());
        body.put("count", count);

        String endpoint = UriComponentsBuilder.fromPath("/vdi/add_resource")
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


    @PreAuthorize("hasRole('get_openstack_noVNC')")
    public NoVNCResponse getConsoleUrl(NoVNCRequest noVNCRequest) {
        try {
            String endpoint = "/servers/" + noVNCRequest.getInstance_id() + "/novnc-console";
            ApiStrategy strategy = strategyFactory.getStrategy(Constants.OPENSTACK.NAME_SERVICE);
            String responseJson = strategy.callApi(HttpMethod.GET, endpoint, null);

            JsonNode root = objectMapper.readTree(responseJson);
            String url = root.path("console").path("url").asText();

            if (url == null || url.isEmpty()) {
                throw new AppException(ErrorCode.API_VNC_ERR);
            }

            return NoVNCResponse.builder()
                    .url(url)
                    .build();

        } catch (Exception e) {
            log.error("Failed to get console URL for server {}: {}", noVNCRequest.getInstance_id(), e.getMessage());
            throw new AppException(ErrorCode.API_VNC_ERR);
        }
    }



//    public String authenticate() {
//        ApiStrategy strategy = strategyFactory.getStrategy(Constants.OPENSTACK.NAME_SERVICE);
//        return strategy.callApi(HttpMethod.POST, Constants.OPENSTACK.ENDPOINT.AUTHENTICATION, null);
//    }
}
