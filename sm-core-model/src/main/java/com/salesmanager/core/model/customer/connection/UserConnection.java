package com.salesmanager.core.model.customer.connection;

import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Deprecated
@Entity
@Table(name="USERCONNECTION", uniqueConstraints = { @UniqueConstraint(columnNames = { "userId",
		"providerId", "userRank" }) })
public class UserConnection extends AbstractUserConnectionWithCompositeKey {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;


}