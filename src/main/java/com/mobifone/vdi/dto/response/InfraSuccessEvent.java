package com.mobifone.vdi.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonAlias;
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
    @JsonProperty("identifier")
    @JsonAlias({"identifier", "task_id"})
    String identifier;

    @JsonProperty("created_resources")
    List<CreatedResource> createdResources;

    @JsonProperty("pfsense_config")
    PfsenseConfig pfsenseConfig;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CreatedResource {
        String type;
        List<Instance> instances;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Instance {
        Attributes attributes;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Attributes {
        String id;

        @JsonProperty("access_ip_v4")
        String accessIpV4;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PfsenseConfig {
        List<Net> network;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Net {
            @JsonProperty("fixed_ip_v4")
            String fixedIpV4;
        }
    }
}


