package com.salesmanager.shop.admin.security;

import com.salesmanager.core.business.services.merchant.MerchantStoreService;
import com.salesmanager.core.business.services.user.GroupService;
import com.salesmanager.core.business.services.user.PermissionService;
import com.salesmanager.core.business.services.user.UserService;
import com.salesmanager.core.model.merchant.MerchantStore;
import com.salesmanager.core.model.user.Group;
import com.salesmanager.core.model.user.GroupType;
import com.salesmanager.core.model.user.Permission;
import com.salesmanager.shop.constants.Constants;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import javax.inject.Named;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


/**
 * 
 * @author casams1
 *         http://stackoverflow.com/questions/5105776/spring-security-with
 *         -custom-user-details
 */
@Service("userDetailsService")
public class UserServicesImpl implements WebUserServices{
	
	private static final Logger LOGGER = LoggerFactory.getLogger(UserServicesImpl.class);
	
	public static final String DEFAULT_ADMIN_USER = "admin@shopizer.com";

	@Value("${shopizer.admin.initial-password:}")
	private String configuredInitialPassword;

	@Inject
	private UserService userService;
	

	@Inject
	private MerchantStoreService merchantStoreService;
	
	@Inject
	@Named("passwordEncoder")
	private PasswordEncoder passwordEncoder;
	

	
	@Inject
	protected PermissionService  permissionService;
	
	@Inject
	protected GroupService   groupService;
	
	public final static String ROLE_PREFIX = "ROLE_";
	
	
	
	public UserDetails loadUserByUsername(String userName)
			throws UsernameNotFoundException, DataAccessException {

		com.salesmanager.core.model.user.User user = null;
		Collection<GrantedAuthority> authorities = new ArrayList<GrantedAuthority>();
		
		try {

			user = userService.getByUserName(userName);

			if(user==null) {
				return null;
			}

			GrantedAuthority role = new SimpleGrantedAuthority(ROLE_PREFIX + Constants.PERMISSION_AUTHENTICATED);//required to login
			authorities.add(role);
	
			List<Integer> groupsId = new ArrayList<Integer>();
			List<Group> groups = user.getGroups();
			for(Group group : groups) {
				
				
				groupsId.add(group.getId());
				
			}
			
	
	    	
	    	List<Permission> permissions = permissionService.getPermissions(groupsId);
	    	for(Permission permission : permissions) {
	    		GrantedAuthority auth = new SimpleGrantedAuthority(ROLE_PREFIX + permission.getPermissionName());
	    		authorities.add(auth);
	    	}
    	
		} catch (Exception e) {
			LOGGER.error("Exception while querrying user",e);
			throw new SecurityDataAccessException("Exception while querrying user",e);
		}
		
		
		
	
		
		User secUser = new User(userName, user.getAdminPassword(), user.isActive(), true,
				true, true, authorities);
		return secUser;
	}
	
	
	public void createDefaultAdmin() throws Exception {

		  MerchantStore store = merchantStoreService.getByCode(MerchantStore.DEFAULT_STORE);

		  String password = passwordEncoder.encode(resolveInitialPassword());
		  
		  List<Group> groups = groupService.listGroup(GroupType.ADMIN);
		  
		  com.salesmanager.core.model.user.User user = new com.salesmanager.core.model.user.User(DEFAULT_ADMIN_USER,password,DEFAULT_ADMIN_USER);
		  user.setFirstName("Administrator");
		  user.setLastName("User");
		  
		  for(Group group : groups) {
			  if(group.getGroupName().equals(Constants.GROUP_SUPERADMIN) || group.getGroupName().equals(Constants.GROUP_ADMIN)) {
				  user.getGroups().add(group);
			  }
		  }

		  user.setMerchantStore(store);		  
		  userService.create(user);
		
		
	}

	private String resolveInitialPassword() {
		if (DefaultAdminPassword.isAcceptable(configuredInitialPassword)) {
			LOGGER.info("Creating default administrator [{}] with the password from shopizer.admin.initial-password", DEFAULT_ADMIN_USER);
			return configuredInitialPassword;
		}
		if (!StringUtils.isBlank(configuredInitialPassword)) {
			LOGGER.warn("Ignoring shopizer.admin.initial-password: it must be at least {} characters and not a well-known default",
					DefaultAdminPassword.MIN_CONFIGURED_LENGTH);
		}
		String generated = DefaultAdminPassword.generate();
		LOGGER.warn("\n**************************************************************\n"
				+ " Default administrator created: {}\n"
				+ " Generated one-time password : {}\n"
				+ " This password is shown only once. Change it after first login,\n"
				+ " or set SHOPIZER_ADMIN_INITIAL_PASSWORD before the first start.\n"
				+ "**************************************************************",
				DEFAULT_ADMIN_USER, generated);
		return generated;
	}



}
