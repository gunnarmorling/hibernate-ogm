/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @authors tag. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.hibernate.ogm;

import org.hibernate.Session;
import org.hibernate.ogm.datastore.spi.SessionOperations;
import org.hibernate.ogm.datastore.spi.SessionOperationsProvider;

/**
 * Provides functionality specific to Hibernate OGM in addition to the {@link Session} contract.
 *
 * @author Gunnar Morling
 */
public interface OgmSession extends Session {

	/**
	 * Provides the API entry point for performing operations specific to the given datastore. Not that not all dialects
	 * provide such operations.
	 *
	 * @param datastoreType The identifier type of a NoSQL datastore
	 * @return an API object with datastore-specific operations
	 */
	<P extends SessionOperationsProvider<O>, O extends SessionOperations> O operationsFor(Class<P> datastoreType);
}
