package com.salesmanager.shop.populator.catalog;

import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.salesmanager.core.business.exception.ConversionException;
import com.salesmanager.core.business.utils.AbstractDataPopulator;
import com.salesmanager.core.model.catalog.product.review.ProductReview;
import com.salesmanager.core.model.catalog.product.review.ProductReviewDescription;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.ReadableProductReview;
import com.salesmanager.shop.model.customer.ReadableCustomer;
import com.salesmanager.shop.utils.DateUtil;

public class ReadableProductReviewPopulator extends
		AbstractDataPopulator<ProductReview, ReadableProductReview> {

	@Override
	public ReadableProductReview populate(ProductReview source,
			ReadableProductReview target, MerchantStore store, Language language)
			throws ConversionException {

		
		try {
			ReadableCustomer customer = toReviewer(source.getCustomer());

			target.setId(source.getId());
			target.setDate(DateUtil.formatDate(source.getReviewDate()));
			target.setCustomer(customer);
			target.setRating(source.getReviewRating());
			target.setProductId(source.getProduct().getId());
			
			Set<ProductReviewDescription> descriptions = source.getDescriptions();
			if(descriptions!=null) {
				for(ProductReviewDescription description : descriptions) {
					target.setDescription(description.getDescription());
					target.setLanguage(description.getLanguage().getCode());
					break;
				}
			}

			return target;
			
		} catch (Exception e) {
			throw new ConversionException("Cannot populate ProductReview", e);
		}
		
		
		
	}

	/**
	 * Reviews are publicly readable, only expose a display name of the reviewer
	 */
	private ReadableCustomer toReviewer(Customer source) {
		if (source == null) {
			return null;
		}
		ReadableCustomer reviewer = new ReadableCustomer();
		reviewer.setId(source.getId());
		if (source.getBilling() != null) {
			reviewer.setFirstName(source.getBilling().getFirstName());
			String lastName = source.getBilling().getLastName();
			if (StringUtils.isNotBlank(lastName)) {
				reviewer.setLastName(lastName.trim().substring(0, 1) + ".");
			}
		}
		return reviewer;
	}

	@Override
	protected ReadableProductReview createTarget() {
		return null;
	}

}
