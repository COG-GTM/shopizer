package com.salesmanager.shop.populator.catalog;

import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.salesmanager.core.business.exception.ConversionException;
import com.salesmanager.core.business.utils.AbstractDataPopulator;
import com.salesmanager.core.model.catalog.product.review.ProductReview;
import com.salesmanager.core.model.catalog.product.review.ProductReviewDescription;
import com.salesmanager.core.model.common.Billing;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.reference.language.Language;
import com.salesmanager.shop.model.catalog.product.ReadableProductReview;
import com.salesmanager.shop.model.catalog.product.ReadableProductReviewer;
import com.salesmanager.shop.utils.DateUtil;

public class ReadableProductReviewPopulator extends
		AbstractDataPopulator<ProductReview, ReadableProductReview> {

	@Override
	public ReadableProductReview populate(ProductReview source,
			ReadableProductReview target, MerchantStore store, Language language)
			throws ConversionException {

		
		try {
			target.setId(source.getId());
			target.setDate(DateUtil.formatDate(source.getReviewDate()));
			target.setCustomer(reviewer(source.getCustomer()));
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

	static ReadableProductReviewer reviewer(Customer customer) {
		ReadableProductReviewer reviewer = new ReadableProductReviewer();
		if (customer == null) {
			return reviewer;
		}
		Billing billing = customer.getBilling();
		if (billing == null) {
			return reviewer;
		}
		String firstName = StringUtils.trimToNull(billing.getFirstName());
		String lastName = StringUtils.trimToNull(billing.getLastName());
		reviewer.setFirstName(firstName);
		if (firstName != null) {
			reviewer.setDisplayName(lastName == null
					? firstName
					: firstName + " " + lastName.substring(0, lastName.offsetByCodePoints(0, 1)).toUpperCase() + ".");
		}
		return reviewer;
	}

	@Override
	protected ReadableProductReview createTarget() {
		return null;
	}

}
