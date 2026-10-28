package com.salesmanager.shop.store.facade.product;

import java.util.Collections;
import java.util.stream.Collectors;

import org.jsoup.helper.Validate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.catalog.product.type.ProductTypeService;
import com.salesmanager.core.model.catalog.product.type.ProductType;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.constants.Constants;
import com.salesmanager.shop.mapper.catalog.PersistableProductTypeMapper;
import com.salesmanager.shop.mapper.catalog.ReadableProductTypeMapper;
import com.salesmanager.shop.model.catalog.product.type.PersistableProductType;
import com.salesmanager.shop.model.catalog.product.type.ReadableProductType;
import com.salesmanager.shop.model.catalog.product.type.ReadableProductTypeList;
import com.salesmanager.shop.store.api.exception.OperationNotAllowedException;
import com.salesmanager.shop.store.api.exception.ResourceNotFoundException;
import com.salesmanager.shop.store.api.exception.ServiceRuntimeException;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.product.facade.ProductTypeFacade;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;

@Service("productTypeFacade")
public class ProductTypeFacadeImpl implements ProductTypeFacade {

	@Autowired
	private ProductTypeService productTypeService;

	@Autowired
	private ReadableProductTypeMapper readableProductTypeMapper;

	@Autowired
	private PersistableProductTypeMapper persistableProductTypeMapper;

	@Autowired
	private UserFacade userFacade;

	@Override
	public ReadableProductTypeList getByMerchant(MerchantStore store, Language language,  int count, int page) {

		Validate.notNull(store, "MerchantStore cannot be null");
		ReadableProductTypeList returnList = new ReadableProductTypeList();

		try {

			Page<ProductType> types = productTypeService.getByMerchant(store, language, page, count);

			if(types != null) {
				returnList.setList(types.getContent().stream().map(t -> readableProductTypeMapper.convert(t, store, language)).collect(Collectors.toList()));
				returnList.setTotalPages(types.getTotalPages());
				returnList.setRecordsTotal(types.getTotalElements());
				returnList.setRecordsFiltered(types.getSize());
			}

			return returnList;
		} catch (Exception e) {
			throw new ServiceRuntimeException(
					"An exception occured while getting product types for merchant[ " + store.getCode() + "]", e);
		}

	}

	@Override
	public ReadableProductType get(MerchantStore store, Long id, Language language) {

		Validate.notNull(store, "MerchantStore cannot be null");
		Validate.notNull(id, "ProductType code cannot be empty");
		try {

			ProductType type = null;
			if(language == null) {
				type = productTypeService.getById(id, store);
			} else {
				type = productTypeService.getById(id, store, language);
			}
			
			if(type == null) {
				throw new ResourceNotFoundException("Product type [" + id + "] not found for store [" + store.getCode() + "]");
			}
					
			ReadableProductType readableType = readableProductTypeMapper.convert(type, store, language);


			return readableType;

		} catch(Exception e) {
			throw new ServiceRuntimeException(
					"An exception occured while getting product type [" + id + "] not found for store [" + store.getCode() + "]", e);
		}

	}

	@Override
	public Long save(PersistableProductType type, MerchantStore store, Language language) {

		Validate.notNull(type,"ProductType cannot be null");
		Validate.notNull(store,"MerchantStore cannot be null");
		Validate.notNull(type.getCode(),"ProductType code cannot be empty");

		try {

			if(this.exists(type.getCode(), store, language)) {
				throw new OperationNotAllowedException(
						"Product type [" + type.getCode() + "] already exist for store [" + store.getCode() + "]");
			}

			ProductType model = persistableProductTypeMapper.convert(type, store, language);
			model.setMerchantStore(store);
			ProductType saved = productTypeService.saveOrUpdate(model);
			return saved.getId();

		} catch(Exception e) {
			throw new ServiceRuntimeException(
					"An exception occured while saving product type",e);
		}

	}

	@Override
	public void update(PersistableProductType type, Long id, MerchantStore store, Language language) {
		Validate.notNull(type,"ProductType cannot be null");
		Validate.notNull(store,"MerchantStore cannot be null");
		Validate.notNull(id,"id cannot be empty");

		try {

			ProductType t = productTypeService.getById(id, store, language);
			if(t == null) {
				throw new ResourceNotFoundException(
						"Product type [" + type.getCode() + "] does not exist for store [" + store.getCode() + "]");
			}

			assertCanModify(t, store);
			MerchantStore owner = t.getMerchantStore();

			type.setId(t.getId());
			type.setCode(t.getCode());

			ProductType model = persistableProductTypeMapper.merge(type, t, store, language);
			model.setMerchantStore(owner);
			productTypeService.saveOrUpdate(model);

		} catch(ResourceNotFoundException | UnauthorizedException e) {
			throw e;
		} catch(Exception e) {
			throw new ServiceRuntimeException(
					"An exception occured while saving product type",e);
		}

	}

	@Override
	public void delete(Long id, MerchantStore store, Language language) {
		Validate.notNull(store,"MerchantStore cannot be null");
		Validate.notNull(id,"id cannot be empty");

		try {

			ProductType t = productTypeService.getById(id, store, language);
			if(t == null) {
				throw new ResourceNotFoundException(
						"Product type [" + id + "] does not exist for store [" + store.getCode() + "]");
			}

			assertCanModify(t, store);

			productTypeService.delete(t);


		} catch(ResourceNotFoundException | UnauthorizedException e) {
			throw e;
		} catch(Exception e) {
			throw new ServiceRuntimeException(
					"An exception occured while saving product type",e);
		}

	}

	/**
	 * Product types without a merchant store are shared by every store and can
	 * only be modified by a super administrator; store owned types only by
	 * their own store.
	 */
	private void assertCanModify(ProductType type, MerchantStore store) {
		MerchantStore owner = type.getMerchantStore();
		if (owner == null) {
			if (!isSuperAdmin()) {
				throw new UnauthorizedException("Product type [" + type.getCode()
						+ "] is shared by all stores and cannot be modified by store [" + store.getCode() + "]");
			}
			return;
		}
		if (!owner.getId().equals(store.getId())) {
			throw new ResourceNotFoundException(
					"Product type [" + type.getId() + "] does not exist for store [" + store.getCode() + "]");
		}
	}

	private boolean isSuperAdmin() {
		String authenticatedUser = userFacade.authenticatedUser();
		return authenticatedUser != null
				&& userFacade.userInRoles(authenticatedUser, Collections.singletonList(Constants.GROUP_SUPERADMIN));
	}

	@Override
	public boolean exists(String code, MerchantStore store, Language language) {
		ProductType t;
		try {
			t = productTypeService.getByCode(code, store, language);
	    } catch (ServiceException e) {
			throw new RuntimeException("An exception occured while getting product type [" + code + "] for merchant store [" + store.getCode() +"]",e);
		}
		if(t != null) {
			return true;
		}
		return false;
	}

	@Override
	public ReadableProductType get(MerchantStore store, String code, Language language) {
		ProductType t;
		try {
			t = productTypeService.getByCode(code, store, language);
	    } catch (ServiceException e) {
			throw new RuntimeException("An exception occured while getting product type [" + code + "] for merchant store [" + store.getCode() +"]",e);
		}

		if(t == null) {
			throw new ResourceNotFoundException("Product type [" + code + "] not found for merchant [" + store.getCode() + "]");
		}
		
		ReadableProductType readableType = readableProductTypeMapper.convert(t, store, language);
		return readableType;

	}


}
