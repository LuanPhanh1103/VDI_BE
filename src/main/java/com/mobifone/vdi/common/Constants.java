package com.mobifone.vdi.common;

// Constants.java
public class Constants {
    public interface OPENSTACK {
        interface ENDPOINT {
            // tất cả là relative path
            String AUTHENTICATION      = "/v3/auth/tokens";
            String IMAGES              = "/images";
            String FLAVORS             = "/flavors";

            String PROVISION_PERSONAL  = "/vdi/provision_infra/personal";
            String PROVISION_ORG       = "/vdi/provision_infra/organization";
            String ADD_RESOURCE        = "/vdi/add_resource/organization";

            String ADD_RESOURCE_FOR_PERSONAL        = "/vdi/add_resource/personal";

            String DELETE_RESOURCE     = "/vdi/delete_resource/{idInstance}";
            String DESTROY_INFRA   = "/vdi/destroy_infra";

            String VOLUMES_V3          = "/v3/{projectId}/volumes";
            String NOVNC               = "/servers/{instanceId}/novnc-console"; // nếu cần
        }

        String URL = "http://42.1.75.51:80";
        String NAME_SERVICE = "OPENSTACK";
    }

    public interface TYPE_QUOTA {
        interface STORAGE { String QUOTA = "quota"; }
        interface HOSTING { String PLAN  = "plan";  }
    }
}

