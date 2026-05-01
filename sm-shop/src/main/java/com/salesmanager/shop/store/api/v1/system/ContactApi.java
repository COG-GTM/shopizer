package com.salesmanager.shop.store.api.v1.system;

import java.util.Locale;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.shop.ContactForm;
import com.salesmanager.shop.utils.EmailTemplatesUtils;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Contact form api", description = "Contact form api")
public class ContactApi {

  @Inject private LanguageService languageService;

  @Inject private EmailTemplatesUtils emailTemplatesUtils;

  @PostMapping("/contact")
  @Operation(summary = "Sends an email contact us to store owner")
  public ResponseEntity<Void> contact(
      @Valid @RequestBody ContactForm contact,
      @Parameter(hidden = true) MerchantStore merchantStore,
      @Parameter(hidden = true) Language language,
      HttpServletRequest request) {
    Locale locale = languageService.toLocale(language, merchantStore);
    emailTemplatesUtils.sendContactEmail(contact, merchantStore, locale, request.getContextPath());
    return new ResponseEntity<Void>(HttpStatus.OK);
  }
}
