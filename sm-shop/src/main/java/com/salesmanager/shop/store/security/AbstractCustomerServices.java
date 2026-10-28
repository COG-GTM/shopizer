package com.salesmanager.shop.store.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.salesmanager.core.business.exception.ServiceException;
import com.salesmanager.core.business.services.customer.CustomerService;
import com.salesmanager.core.business.services.user.GroupService;
import com.salesmanager.core.business.services.user.PermissionService;
import com.salesmanager.core.model.customer.Customer;
import com.salesmanager.core.model.user.Group;
import com.salesmanager.core.model.user.Permission;
import com.salesmanager.shop.admin.security.SecurityDataAccessException;
import com.salesmanager.shop.application.config.MerchantStoreArgumentResolver;
import com.salesmanager.shop.constants.Constants;

public abstract class AbstractCustomerServices implements UserDetailsService{
	
	private static final Logger LOGGER = LoggerFactory.getLogger(AbstractCustomerServices.class);
	
	protected CustomerService customerService;
	protected PermissionService  permissionService;
	protected GroupService   groupService;
	
	public final static String ROLE_PREFIX = "ROLE_";//Spring Security 4
	
	public AbstractCustomerServices(
			CustomerService customerService, 
			PermissionService permissionService, 
			GroupService groupService) {
		
		this.customerService = customerService;
		this.permissionService = permissionService;
		this.groupService = groupService;
	}
	
	protected abstract UserDetails userDetails(String userName, Customer customer, Collection<GrantedAuthority> authorities);
	

	/**
	 * Customer nicks are unique per store only, so the customer is resolved in the
	 * store of the current request (same {@code store} parameter used by the API).
	 */
	public UserDetails loadUserByUsername(String userName)
			throws UsernameNotFoundException, DataAccessException {
		return loadUserByUsername(userName, currentStoreCode());
	}

	public UserDetails loadUserByUsername(String userName, String storeCode)
			throws UsernameNotFoundException, DataAccessException {
		Customer user = null;
		Collection<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();

		try {
			
				LOGGER.debug("Loading user by user id: {} in store {}", userName, storeCode);

				user = customerService.getByNick(userName, storeCode);
			
				if(user==null) {
					//return null;
					throw new UsernameNotFoundException("User " + userName + " not found");
				}
	
	

			GrantedAuthority role = new SimpleGrantedAuthority(ROLE_PREFIX + Constants.PERMISSION_CUSTOMER_AUTHENTICATED);//required to login
			authorities.add(role); 
			
			List<Integer> groupsId = new ArrayList<Integer>();
			List<Group> groups = user.getGroups();
			for(Group group : groups) {
				groupsId.add(group.getId());
			}
			
	
			if(CollectionUtils.isNotEmpty(groupsId)) {
		    	List<Permission> permissions = permissionService.getPermissions(groupsId);
		    	for(Permission permission : permissions) {
		    		GrantedAuthority auth = new SimpleGrantedAuthority(permission.getPermissionName());
		    		authorities.add(auth);
		    	}
			}
			

		} catch (ServiceException e) {
			LOGGER.error("Exception while querrying customer",e);
			throw new SecurityDataAccessException("Cannot authenticate customer",e);
		}

		return userDetails(userName, user, authorities);
		
	}

	public static String currentStoreCode() {
		RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
		if (attributes instanceof ServletRequestAttributes) {
			return storeCode(((ServletRequestAttributes) attributes).getRequest());
		}
		return com.salesmanager.core.business.constants.Constants.DEFAULT_STORE;
	}

	public static String storeCode(HttpServletRequest request) {
		String code = request.getParameter(MerchantStoreArgumentResolver.REQUEST_PARAMATER_STORE);
		return StringUtils.isNotBlank(code) ? code : com.salesmanager.core.business.constants.Constants.DEFAULT_STORE;
	}

}
