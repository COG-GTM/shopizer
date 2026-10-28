package com.salesmanager.test.shop.unit.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.salesmanager.core.business.services.user.GroupService;
import com.salesmanager.core.business.services.user.PermissionService;
import com.salesmanager.core.model.user.Group;
import com.salesmanager.core.model.user.GroupType;
import com.salesmanager.core.model.user.Permission;
import com.salesmanager.shop.store.api.v1.security.SecurityApi;

public class SecurityApiMappingTest {

	private PermissionService permissionService;
	private GroupService groupService;
	private MockMvc mockMvc;

	@Before
	public void setUp() throws Exception {
		permissionService = mock(PermissionService.class);
		groupService = mock(GroupService.class);

		Permission viewPermission = permission(11, "VIEW_CATALOG");
		Permission editPermission = permission(12, "EDIT_CATALOG");
		Group adminGroup = new Group("ADMIN");
		adminGroup.setId(1);
		adminGroup.setGroupType(GroupType.ADMIN);
		adminGroup.setPermissions(new HashSet<>(Arrays.asList(viewPermission, editPermission)));

		when(permissionService.list()).thenReturn(Arrays.asList(viewPermission, editPermission));
		when(groupService.list()).thenReturn(Arrays.asList(adminGroup));
		when(groupService.findByName("ADMIN")).thenReturn(adminGroup);

		SecurityApi api = new SecurityApi();
		ReflectionTestUtils.setField(api, "permissionService", permissionService);
		ReflectionTestUtils.setField(api, "groupService", groupService);
		mockMvc = MockMvcBuilders.standaloneSetup(api).build();
	}

	@Test
	public void canonicalAndLegacyGroupMappingsReturnSameJson() throws Exception {
		MvcResult canonical = mockMvc.perform(get("/api/v1/private/security/groups"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(1))
				.andExpect(jsonPath("$[0].name").value("ADMIN"))
				.andExpect(jsonPath("$[0].type").value("ADMIN"))
				.andReturn();
		MvcResult legacy = mockMvc.perform(get("/api/v1/sec/private/groups"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(1))
				.andExpect(jsonPath("$[0].name").value("ADMIN"))
				.andExpect(jsonPath("$[0].type").value("ADMIN"))
				.andReturn();

		assertEquals(canonical.getResponse().getContentAsString(), legacy.getResponse().getContentAsString());
	}

	@Test
	public void canonicalAndLegacyPermissionMappingsReturnSameJson() throws Exception {
		MvcResult canonical = mockMvc.perform(get("/api/v1/private/security/permissions"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(11))
				.andExpect(jsonPath("$[0].name").value("VIEW_CATALOG"))
				.andReturn();
		MvcResult legacy = mockMvc.perform(get("/api/v1/sec/private/permissions"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(11))
				.andExpect(jsonPath("$[0].name").value("VIEW_CATALOG"))
				.andReturn();

		assertEquals(canonical.getResponse().getContentAsString(), legacy.getResponse().getContentAsString());
	}

	@Test
	public void canonicalAndLegacyGroupPermissionMappingsReturnSameJson() throws Exception {
		MvcResult canonical = mockMvc.perform(get("/api/v1/private/security/ADMIN/permissions"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").exists())
				.andExpect(jsonPath("$[0].id").exists())
				.andReturn();
		MvcResult legacy = mockMvc.perform(get("/api/v1/sec/private/ADMIN/permissions"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").exists())
				.andExpect(jsonPath("$[0].id").exists())
				.andReturn();

		assertEquals(canonical.getResponse().getContentAsString(), legacy.getResponse().getContentAsString());
	}

	@Test
	public void obsoletePrivateGroupsPathIsNotMapped() throws Exception {
		mockMvc.perform(get("/api/v1/private/groups")).andExpect(status().isNotFound());
	}

	private Permission permission(int id, String name) {
		Permission permission = new Permission(name);
		permission.setId(id);
		return permission;
	}
}
