package com.salesmanager.test.shop.common;

public abstract class Pr350IntegrationSupport extends ServicesTestSupport {
    static {
        System.setProperty("config.cms.store.location", "target/pr350-cms/store");
        System.setProperty("config.cms.files.location", "target/pr350-cms/repos");
        System.setProperty("search.noindex", "true");
    }
}
