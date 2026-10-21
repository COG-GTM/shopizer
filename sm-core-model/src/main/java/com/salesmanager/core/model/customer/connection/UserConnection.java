package com.salesmanager.core.model.customer.connection;

import javax.persistence.Entity;
import javax.persistence.Table;

@Deprecated
@Entity
// The former superclass table constraint was ignored on the mapped superclass.
@Table(name = "USERCONNECTION")
public class UserConnection extends AbstractUserConnectionWithCompositeKey {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;


}