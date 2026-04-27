package com.salesmanager.shop.store.api.v1.content;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.salesmanager.core.model.content.ContentType;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.content.ContentFile;
import com.salesmanager.shop.model.content.ContentFolder;
import com.salesmanager.shop.model.content.ContentName;
import com.salesmanager.shop.model.content.PersistableContentEntity;
import com.salesmanager.shop.model.content.ReadableContentEntity;
import com.salesmanager.shop.model.content.ReadableContentFull;
import com.salesmanager.shop.model.content.box.PersistableContentBox;
import com.salesmanager.shop.model.content.box.ReadableContentBox;
import com.salesmanager.shop.model.content.page.PersistableContentPage;
import com.salesmanager.shop.model.content.page.ReadableContentPage;
import com.salesmanager.shop.model.entity.Entity;
import com.salesmanager.shop.model.entity.EntityExists;
import com.salesmanager.shop.model.entity.ReadableEntityList;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.controller.content.facade.ContentFacade;
import com.salesmanager.shop.utils.ImageFilePath;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping(value = "/api/v1")
@Tag(name = "Content management resource (Content Management Api)")
public class ContentApi {

	private static final Logger LOGGER = LoggerFactory.getLogger(ContentApi.class);

	private static final String DEFAULT_PATH = "/";
	
	private final static String BOX = "BOX";
	private final static String PAGE = "PAGE";

	@Inject
	private ContentFacade contentFacade;

	@Inject
	@Qualifier("img")
	private ImageFilePath imageUtils;

	/**
	 * List content pages
	 * @param merchantStore
	 * @param language
	 * @param page
	 * @param count
	 * @return
	 */
	@GetMapping(value = {"/private/content/pages", "/content/pages"}, produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Get page names created for a given MerchantStore")
	public ReadableEntityList<ReadableContentPage> pages(
			@Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language,
			int page,
			int count) {
		return contentFacade
				.getContentPages(merchantStore, language, page, count);
	}

	@Deprecated
	@GetMapping(value = "/content/summary", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Get pages summary created for a given MerchantStore. Content summary is a content bux having code summary.")
	public List<ReadableContentBox> pagesSummary(
			@Parameter(hidden = true) MerchantStore merchantStore, 
			@Parameter(hidden = true) Language language) {
		//return contentFacade.getContentBoxes(ContentType.BOX, "summary_", merchantStore, language);
		return null;
	}

	/**
	 * List all boxes
	 * 
	 * @param merchantStore
	 * @param language
	 * @return
	 */
	@GetMapping(value = {"/content/boxes","/private/content/boxes"}, produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Get boxes for a given MerchantStore")
	public ReadableEntityList<ReadableContentBox> boxes(
			@Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language,
			int page,
			int count
			) {
		return contentFacade.getContentBoxes(ContentType.BOX, merchantStore, language, page, count);
	}

	/**
	 * List specific content box
	 * @param code
	 * @param merchantStore
	 * @param language
	 * @return
	 */
	@GetMapping(value = "/content/pages/{code}", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Get page content by code for a given MerchantStore")
	public ReadableContentPage page(@PathVariable("code") String code, @Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {

		return contentFacade.getContentPage(code, merchantStore, language);

	}

	/**
	 * Get content page by name
	 * @param name
	 * @param merchantStore
	 * @param language
	 * @return
	 */
	@GetMapping(value = "/content/pages/name/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Get page content by code for a given MerchantStore")
	public ReadableContentPage pageByName(@PathVariable("name") String name, @Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {

		return contentFacade.getContentPageByName(name, merchantStore, language);

	}
	
	/**
	 * Create content box
	 * 
	 * @param page
	 * @param merchantStore
	 * @param language
	 * @param pageCode
	 */
	@PostMapping(value = "/private/content/box")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create content box")
	public Entity createBox(
			@RequestBody @Valid PersistableContentBox box, 
			@Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {

		Long id = contentFacade.saveContentBox(box, merchantStore, language);
		Entity entity = new Entity();
		entity.setId(id);
		return entity;
	}
	
	@GetMapping(value = "/private/content/box/{code}/exists")
	@ResponseStatus(HttpStatus.OK)
	@Operation(summary = "Check unique content box")
	public EntityExists boxExists(
			@PathVariable String code, 
			@Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {

		boolean exists = contentFacade.codeExist(code, BOX, merchantStore);
		EntityExists entity = new EntityExists(exists);
		return entity;
	}
	
	@GetMapping(value = "/private/content/page/{code}/exists")
	@ResponseStatus(HttpStatus.OK)
	@Operation(summary = "Check unique content page")
	public EntityExists pageExists(
			@PathVariable String code, 
			@Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {

		boolean exists = contentFacade.codeExist(code, PAGE, merchantStore);
		EntityExists entity = new EntityExists(exists);
		return entity;
	}
	
	/**
	 * Create content page
	 * @param page
	 * @param merchantStore
	 * @param language
	 */
	@PostMapping(value = "/private/content/page")
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create content page")
	public Entity createPage(
			@RequestBody @Valid PersistableContentPage page, 
			@Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {

		Long id = contentFacade.saveContentPage(page, merchantStore, language);
		Entity entity = new Entity();
		entity.setId(id);
		return entity;
	}
	
	
	/**
	 * Delete content page
	 * @param id
	 * @param merchantStore
	 * @param language
	 */
	@DeleteMapping(value = "/private/content/page/{id}")
	@ResponseStatus(HttpStatus.OK)
	@Operation(summary = "Delete content page")
	public void deletePage(
			@PathVariable Long id,
			@Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {

		contentFacade.delete(merchantStore, id);

	}
	
	/**
	 * Delete content box
	 * @param id
	 * @param merchantStore
	 * @param language
	 */
	@DeleteMapping(value = "/private/content/box/{id}")
	@ResponseStatus(HttpStatus.OK)
	@Operation(summary = "Delete content box")
	public void deleteBox(
			@PathVariable Long id,
			@Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {

		contentFacade.delete(merchantStore, id);

	}
	
	@PutMapping(value = "/private/content/page/{id}")
	@ResponseStatus(HttpStatus.OK)
	@Operation(summary = "Update content page")
	public void updatePage(
			@RequestBody @Valid PersistableContentPage page,
			@PathVariable Long id,
			@Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {

		contentFacade.updateContentPage(id, page, merchantStore, language);
	}
	
	@PutMapping(value = "/private/content/box/{id}")
	@ResponseStatus(HttpStatus.OK)
	@Operation(summary = "Update content box")
	public void updateBox(
			@RequestBody @Valid PersistableContentBox box,
			@PathVariable Long id,
			@Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {

		contentFacade.updateContentBox(id, box, merchantStore, language);
	}

	@Deprecated
	@GetMapping(value = "/private/content/any/{code}", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Get page content by code for a given MerchantStore")
	public ReadableContentFull content(@PathVariable("code") String code, @Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {

		return contentFacade.getContent(code, merchantStore, language);

	}

	@Deprecated
	@GetMapping(value = "/private/contents/any", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Get contents (page and box) for a given MerchantStore")
	public List<ReadableContentEntity> contents(@Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language) {

		Optional<String> op = Optional.empty();
		return contentFacade.getContents(op, merchantStore, language);

	}
	
	
	@GetMapping(value = "/private/content/boxes/{code}", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Manage box content by code for a code and a given MerchantStore")
	public ReadableContentBox manageBoxByCode(@PathVariable("code") String code, @Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {
		return contentFacade.getContentBox(code, merchantStore, language);
	}

	@GetMapping(value = "/content/boxes/{code}", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Get box content by code for a code and a given MerchantStore")
	public ReadableContentBox getBoxByCode(@PathVariable("code") String code, @Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {
		return contentFacade.getContentBox(code, merchantStore, language);
	}





	/**
	 * 
	 * @param parent
	 * @param folder
	 * @param merchantStore
	 * @param language
	 */
	@DeleteMapping(value = "/content/folder", produces = MediaType.APPLICATION_JSON_VALUE)
	@ResponseStatus(HttpStatus.CREATED)
	public void addFolder(@RequestParam String parent, @RequestParam String folder,
			@Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language) {

	}

	/**
	 * @param code
	 * @param path
	 * @param request
	 * @param response
	 * @return
	 * @throws Exception
	 */
	@GetMapping(value = "/content/images", produces = MediaType.APPLICATION_JSON_VALUE)
	@Operation(summary = "Get store content images")
	public ContentFolder images(@Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language,
			@RequestParam(value = "path", required = false) String path, HttpServletRequest request,
			HttpServletResponse response) throws Exception {

		//String decodedPath = decodeContentPath(path);
		ContentFolder folder = contentFacade.getContentFolder(path, merchantStore);
		return folder;
	}



	/**
	 * Need type, name and entity
	 *
	 * @param file
	 */
	@PostMapping(value = "/private/file")
	@ResponseStatus(HttpStatus.CREATED)
	public void upload(@RequestParam("file") MultipartFile file, @Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {

		ContentFile f = new ContentFile();
		f.setContentType(file.getContentType());
		f.setName(file.getOriginalFilename());
		try {
			f.setFile(file.getBytes());
		} catch (IOException e) {
			throw new ServiceRuntimeException("Error while getting file bytes");
		}

		contentFacade.addContentFile(f, merchantStore.getCode());

	}

	@PostMapping(value = "/private/files", consumes = { MediaType.MULTIPART_FORM_DATA_VALUE })
	@ResponseStatus(HttpStatus.CREATED)
	public void uploadMultipleFiles(@RequestParam(value = "file[]", required = true) MultipartFile[] files,
			@Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language) {

		for (MultipartFile f : files) {
			ContentFile cf = new ContentFile();
			cf.setContentType(f.getContentType());
			cf.setName(f.getName());
			try {
				cf.setFile(f.getBytes());
				contentFacade.addContentFile(cf, merchantStore.getCode());
			} catch (IOException e) {
				throw new ServiceRuntimeException("Error while getting file bytes");
			}
		}

	}

	
	@Deprecated
	@PutMapping(value = "/private/content/{id}")
	@ResponseStatus(HttpStatus.OK)
	@Operation(summary = "Update content page")
	public void updatePage(@PathVariable Long id, @RequestBody @Valid PersistableContentEntity page,
			@Parameter(hidden = true) MerchantStore merchantStore, @Parameter(hidden = true) Language language) {
		page.setId(id);
		//contentFacade.saveContentPage(page, merchantStore, language);
	}

	/**
	 * Deletes a content from CMS
	 *
	 * @param name
	 */
	@Deprecated
	@DeleteMapping(value = "/private/content/{id}")
	@Operation(summary = "Deletes a content from CMS")
	public void deleteContent(Long id, @Parameter(hidden = true) MerchantStore merchantStore) {
		contentFacade.delete(merchantStore, id);
	}

	/*  *//**
			 * Deletes a content from CMS
			 *
			 * @param name
			 *//*
			 * @DeleteMapping(value = "/private/content/page/{id}")
			 * 
			 * @Operation(summary = "")
			 * 
			 * public void deleteFile( Long id,
			 * 
			 * @Parameter(hidden = true) MerchantStore merchantStore) {
			 * contentFacade.deletePage(merchantStore, id); }
			 */

	/**
	 * Deletes a file from CMS
	 *
	 * @param name
	 */
	@DeleteMapping(value = "/private/content/")
	@Operation(summary = "Deletes a file from CMS")
	public void deleteFile(@Valid ContentName name, @Parameter(hidden = true) MerchantStore merchantStore,
			@Parameter(hidden = true) Language language) {
		contentFacade.delete(merchantStore, name.getName(), name.getContentType());
	}


}
