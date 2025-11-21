package com.mobifone.vdi.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class InfraSuccessEvent {

    @JsonProperty("identifier")
    private String identifier;

    @JsonProperty("id")
    private String infraId;

    @JsonProperty("pfsense_config")
    private PfsenseConfig pfsenseConfig;

    @JsonProperty("resources")
    private List<ResourceGroup> resources;

    // ==== ResourceGroup ====
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResourceGroup {

        private String type;                    // personal/org

        @JsonProperty("infra_id")              // add-resource
        private String infraId;

        @JsonProperty("instances")             // personal/org
        private List<Instance> instances;

        @JsonProperty("resource")              // add-resource
        private List<ResourceItem> resourceItems;
    }

    // ==== ResourceItem (add-resource) ====
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResourceItem {

        private String type;

        @JsonProperty("instances")
        private List<Instance> instances;
    }

    // ==== Instance + Attributes ====
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Instance {

        @JsonProperty("attributes")
        private Attributes attributes;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Attributes {

        @JsonProperty("id")
        private String id;

        @JsonProperty("access_ip_v4")
        private String accessIpV4;

        @JsonProperty("fixed_ip_v4")
        private String fixedIpV4;
    }

    // ==== PfsenseConfig ====
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PfsenseConfig {

        @JsonProperty("network")
        private List<Net> network;

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Net {

            @JsonProperty("name")
            private String name;

            @JsonProperty("fixed_ip_v4")
            private String fixedIpV4;
        }
    }
}
