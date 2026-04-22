package com.salesmanager.shop.store.api.v1.catalog;

import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.catalog.PersistableCatalog;
import com.salesmanager.shop.model.catalog.catalog.PersistableCatalogCategoryEntry;
import com.salesmanager.shop.model.catalog.catalog.ReadableCatalog;
import com.salesmanager.shop.model.catalog.catalog.ReadableCatalogCategoryEntry;
import com.salesmanager.shop.model.entity.EntityExists;
import com.salesmanager.shop.model.entity.ReadableEntityList;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.controller.catalog.facade.CatalogFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Optional;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;

@RestController
@RequestMapping(value = "/api/v1")
@Tag(name = "Catalog management resource (Catalog Management Api)")
public class CatalogApi {

  private static final Logger LOGGER = LoggerFactory.getLogger(CatalogApi.class);

  @Autowired
  private CatalogFacade catalogFacade;

  @GetMapping(value = "/private/catalogs")
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "Get catalogs by merchant")
  @Parameters({
      @Parameter(name = "store", description = "Default: DEFAULT"),
      @Parameter(name = "lang", description = "Default: en")})
  public ReadableEntityList<ReadableCatalog> getCatalogs(
      @Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language,
      Optional<String> code,
      @RequestParam(value = "page", required = false, defaultValue="0") Integer page,
      @RequestParam(value = "count", required = false, defaultValue="10") Integer count) {

      return catalogFacade.getListCatalogs(code, merchantStore, language, page, count);

  }

  @ResponseStatus(HttpStatus.OK)
  @GetMapping(value = {"/private/catalog/unique"}, produces = MediaType.APPLICATION_JSON_VALUE)
  @Parameters({
    @Parameter(name = "store", description = "Default: DEFAULT"),
    @Parameter(name = "lang", description = "Default: en")
  })
  @Operation(summary = "Check if catalog code already exists")
  public ResponseEntity<EntityExists> exists(
      @RequestParam(value = "code") String code,
      @Parameter(hidden = true) MerchantStore merchantStore,
      @Parameter(hidden = true) Language language) {
      boolean existByCode = catalogFacade.uniqueCatalog(code, merchantStore);
      return new ResponseEntity<EntityExists>(new EntityExists(existByCode), HttpStatus.OK);
  }

  @PostMapping(value = "/private/catalog")
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "Create catalog")
  @Parameters({
      @Parameter(name = "store", description = "Default: DEFAULT"),
      @Parameter(name = "lang", description = "Default: en")})
  public ReadableCatalog createCatalog(
      @RequestBody @Valid PersistableCatalog catalog,
      @Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language) {

	  return catalogFacade.saveCatalog(catalog, merchantStore, language);

  }

  @PatchMapping(value = "/private/catalog/{id}")
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "Update catalog")
  @Parameters({
      @Parameter(name = "store", description = "Default: DEFAULT"),
      @Parameter(name = "lang", description = "Default: en")})
  public void updateCatalog(
	  @PathVariable Long id,
      @RequestBody @Valid PersistableCatalog catalog,
      @Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language) {

	  catalog.setId(id);
	  catalogFacade.updateCatalog(id, catalog, merchantStore, language);

  }

  @GetMapping(value = "/private/catalog/{id}")
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "Get catalog")
  @Parameters({
      @Parameter(name = "store", description = "Default: DEFAULT"),
      @Parameter(name = "lang", description = "Default: en")})
  public ReadableCatalog getCatalog(
	  @PathVariable Long id,
      @Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language) {

	  return catalogFacade.getCatalog(id, merchantStore, language);

  }

  @DeleteMapping(value = "/private/catalog/{id}")
  @Operation(summary = "Deletes a catalog")
  @Parameters({
      @Parameter(name = "store", description = "Default: DEFAULT"),
      @Parameter(name = "lang", description = "Default: en")})
  public void deleteCatalog(
      @PathVariable Long id,
      @Parameter(hidden = true) MerchantStore merchantStore,
      @Parameter(hidden = true) Language language) {

	  catalogFacade.deleteCatalog(id, merchantStore, language);
  }

  @PostMapping(value = "/private/catalog/{id}")
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "Add catalog entry to catalog")
  @Parameters({
      @Parameter(name = "store", description = "Default: DEFAULT"),
      @Parameter(name = "lang", description = "Default: en")})
  public ReadableCatalogCategoryEntry addCatalogEntry(
      @PathVariable Long id,
	  @RequestBody @Valid PersistableCatalogCategoryEntry catalogEntry,
      @Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language) {

	  ReadableCatalog c = catalogFacade.getCatalog(id, merchantStore, language);

	  if(c == null) {
		  throw new ResourceNotFoundException("Catalog id [" + id + "] not found");
	  }

	  catalogEntry.setCatalog(c.getCode());
	  return catalogFacade.addCatalogEntry(catalogEntry, merchantStore, language);

  }

  @DeleteMapping(value = "/private/catalog/{id}/entry/{entryId}")
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "Remove catalog entry from catalog")
  @Parameters({
      @Parameter(name = "store", description = "Default: DEFAULT"),
      @Parameter(name = "lang", description = "Default: en")})
  public void removeCatalogEntry(
      @PathVariable Long id,
      @PathVariable Long entryId,
      @Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language) {

	  catalogFacade.removeCatalogEntry(id, entryId, merchantStore, language);

  }

  @GetMapping(value = "/private/catalog/{id}/entry")
  @ResponseStatus(HttpStatus.OK)
  @Operation(summary = "Get catalog entry by catalog")
  @Parameters({
      @Parameter(name = "store", description = "Default: DEFAULT"),
      @Parameter(name = "lang", description = "Default: en")})
  public ReadableEntityList<ReadableCatalogCategoryEntry> getCatalogEntry(
	  @PathVariable(value="id") Long id,
      @Parameter(hidden = true) MerchantStore merchantStore,
      @Parameter(hidden = true) Language language,
      @RequestParam(value = "page", required = false, defaultValue="0") Integer page,
      @RequestParam(value = "count", required = false, defaultValue="10") Integer count,
      HttpServletRequest request) {

	  return catalogFacade.listCatalogEntry(catalogEntryFilter(request), id, merchantStore, language, page, count);

  }

  private Optional<String> catalogFilter(HttpServletRequest request) {

	    return Optional.ofNullable((String)request.getAttribute("code"));
  }

  private Optional<String> catalogEntryFilter(HttpServletRequest request) {

	    return Optional.ofNullable((String)request.getAttribute("name"));
}

}
