package com.mobifone.vdi.common;

public class Constants {
    public interface OPENSTACK{
        interface ENDPOINT{
            String AUTHENTICATION = "/v3/auth/tokens";
            String IMAGES = "/images";
            String FLAVORS = "/flavors";
            String INSTANCE = "/infra";
        }
        String URL = "http://10.6.60.154:8000";
//        String URL = "http://42.1.124.232:8000/api";
        String NAME_SERVICE = "OPENSTACK";
    }


    public interface TYPE_QUOTA{
        interface STORAGE{
            String QUOTA ="quota";
        }
        interface HOSTING{
            String PLAN ="plan";
        }
    }

}
