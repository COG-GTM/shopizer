package com.salesmanager.shop.store.api.v1.configurations;

import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import com.salesmanager.core.business.utils.CacheUtils;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.store.facade.StoreFacade;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;

/**
 *
 */

@RestController
@RequestMapping(value = "/api/v1")
public class CacheApi {

  private static final Logger LOGGER = LoggerFactory.getLogger(CacheApi.class);

  @Inject
  private StoreFacade storeFacade;

  @Inject
  private UserFacade userFacade;

  @Inject
  private CacheUtils cache;

  @DeleteMapping(value = "/private/cache/store/{storeId}/clear")
  public @ResponseBody ResponseEntity<String> clearCache(@PathVariable("storeId") String storeCode,
      @RequestParam(name = "cacheKey", required = false) String cacheKey, HttpServletRequest request) {

    String authenticatedUser = userFacade.authenticatedUser();
    if (authenticatedUser == null) {
      throw new UnauthorizedException();
    }

    userFacade.authorizedGroup(authenticatedUser,
        Stream.of(Constants.GROUP_SUPERADMIN, Constants.GROUP_ADMIN, Constants.GROUP_ADMIN_RETAIL)
            .collect(Collectors.toList()));

    MerchantStore merchantStore = storeFacade.get(storeCode);

    if (!userFacade.authorizeStore(merchantStore, request.getRequestURI())) {
      throw new UnauthorizedException("Cannot authorize user for store " + merchantStore.getCode());
    }

    try {
      if (StringUtils.isNotEmpty(cacheKey)) {
        cache.removeFromCache(merchantStore.getId() + "_" + cacheKey);
      } else {
        cache.removeAllFromCache(merchantStore);
      }
    } catch (Exception e) {
      LOGGER.error("Error while clearing cache", e);
      throw new ServiceRuntimeException("Error while clearing cache");
    }

    return new ResponseEntity<>(HttpStatus.OK);
  }

}
