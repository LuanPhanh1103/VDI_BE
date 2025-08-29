package com.mobifone.vdi.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonIgnoreProperties(ignoreUnknown = true)
public class InfraSuccessEvent {
    @Getter
    String identifier;

    @JsonProperty("created_resources")
    List<CreatedResource> created_resources;

    // ✅ Bổ sung pfsense_config (chỉ lấy access_ip_v4)
    @JsonProperty("pfsense_config")
    PfsenseConfig pfsense_config;


    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CreatedResource {
        String type;
        List<Instance> instances;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Instance {
        @JsonProperty("index_key")
        Integer index_key;
        Attributes attributes;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Attributes {
        String id;                         // instance id
        @JsonProperty("access_ip_v4")
        String accessIpV4;                 // VM IP
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PfsenseConfig {
        String id;

        @JsonProperty("access_ip_v4")
        String accessIpV4;

        List<Net> network;   // để lọc provider

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Net {
            @JsonProperty("name")
            String name;

            @JsonProperty("fixed_ip_v4")
            String fixedIpV4;
        }
    }



    public List<CreatedResource> getCreatedResources() { return created_resources; }
    public PfsenseConfig getPfsenseConfig() { return pfsense_config; }
}


