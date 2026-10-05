package com.salesmanager.shop.store.api.v1.search;

import java.util.List;

import jakarta.inject.Inject;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.SearchProductRequest;
import com.salesmanager.shop.model.entity.ValueList;
import com.salesmanager.shop.store.controller.search.facade.SearchFacade;

import modules.commons.search.request.SearchItem;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Api for searching shopizer catalog based on search term when filtering products based on product
 * attribute is required, see /api/v1/product
 *
 * @author c.samson
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Search products and search word/sentence completion functionality (Search Api)", description = "Search products and search term completion functionality")
public class SearchApi {

  @Inject private SearchFacade searchFacade;


  /**
   * Search products from underlying elastic search
   */
  @PostMapping("/search")
  @Parameters({
    @Parameter(name = "store", in = ParameterIn.QUERY, schema = @Schema(type = "string", defaultValue = "DEFAULT")),
    @Parameter(name = "lang", in = ParameterIn.QUERY, schema = @Schema(type = "string", defaultValue = "en"))
  })
  
  //TODO use total, count and page
  public @ResponseBody List<SearchItem> search(
      @RequestBody SearchProductRequest searchRequest,
      @Parameter(hidden = true) MerchantStore merchantStore,
      @Parameter(hidden = true) Language language) {

    return searchFacade.search(merchantStore, language, searchRequest);
  }

  @PostMapping("/search/autocomplete")
  @Parameters({
    @Parameter(name = "store", in = ParameterIn.QUERY, schema = @Schema(type = "string", defaultValue = "DEFAULT")),
    @Parameter(name = "lang", in = ParameterIn.QUERY, schema = @Schema(type = "string", defaultValue = "en"))
  })
  public @ResponseBody ValueList autocomplete(
      @RequestBody SearchProductRequest searchRequest,
      @Parameter(hidden = true) MerchantStore merchantStore,
      @Parameter(hidden = true) Language language) {
    return searchFacade.autocompleteRequest(searchRequest.getQuery(), merchantStore, language);
  }
}
