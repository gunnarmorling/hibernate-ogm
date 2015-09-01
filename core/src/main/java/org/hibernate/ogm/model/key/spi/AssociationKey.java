/*
 * Hibernate OGM, Domain model persistence for NoSQL datastores
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.ogm.model.key.spi;

import java.util.Arrays;

import org.hibernate.annotations.common.AssertionFailure;

/**
 * Represents the key used to link a property value and the id of it's owning entity
 *
 * @author Emmanuel Bernard
 * @author Sanne Grinovero
 * @author Gunnar Morling
 */
public final class AssociationKey {

	//column value types do have to be serializable so AssociationKey is serializable
	//should it be a Serializable[] type? It seems to be more pain than anything else
	private final String tableName;
	private final String[] columnNames;
	private final Object[] columnValues;
	private final int hashCode;

	//role and entity key are not part of the object identity
	private final EntityKey entityKey;


	public AssociationKey(String tableName, String[] columnNames, Object[] columnValues, EntityKey entityKey) {
		this.tableName = tableName;
		this.columnNames = columnNames;
		if ( columnNames.length != columnValues.length ) {
			throw new AssertionFailure( "Column names do not match column values" );
		}
		this.columnValues = columnValues;
		this.entityKey = entityKey;

		this.hashCode = tableName.hashCode() * 31 + Arrays.hashCode( columnValues );
	}

	/**
	 * Returns the table name of this key.
	 *
	 * @return the table name of this key
	 */
	public String getTable() {
		return tableName;
	}

	/**
	 * The columns identifying the association.
	 *
	 * For example, in a many to many association, the row key will look like:
	 *
	 * <pre>
	 * RowKey{table='AccountOwner_BankAccount', columnNames=[owners_id, bankAccounts_id], columnValues=[...]},
	 * </pre>
	 *
	 * the association key will be something like:
	 *
	 * <pre>
	 * AssociationKey{table='AccountOwner_BankAccount', columnNames=[owners_id], columnValues=[...]},
	 * </pre>
	 *
	 * @return the columns names as an array, it never returns {@code null}
	 */
	public String[] getColumnNames() {
		return columnNames;
	}

	/**
	 * Get values of the key
	 *
	 * @return the values of the key. It never returns {@code null}
	 */
	public Object[] getColumnValues() {
		return columnValues;
	}

	/**
	 * Returns the owning entity key.
	 *
	 * @return an {@link EntityKey} representing the owner of the association
	 */
	public EntityKey getEntityKey() {
		return entityKey;
	}

	@Override
	public boolean equals(Object o) {
		if ( this == o ) {
			return true;
		}
		if ( o == null || AssociationKey.class != o.getClass() ) {
			return false;
		}

		AssociationKey that = (AssociationKey) o;

		// order of comparison matters on performance:
		if ( !tableName.equals( that.tableName ) ) {
			return false;
		}

		// Probably incorrect - comparing Object[] arrays with Arrays.equals
		if ( !Arrays.equals( columnValues, that.columnValues ) ) {
			return false;
		}
		if ( !Arrays.equals( columnNames, that.columnNames ) ) {
			return false;
		}

		return true;
	}

	@Override
	public int hashCode() {
		return hashCode;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append( "AssociationKey(" );
		sb.append( getTable() );
		sb.append( ") [" );
		int i = 0;
		for ( String column : columnNames ) {
			sb.append( column ).append( "=" ).append( columnValues[i] );
			i++;
			if ( i < columnNames.length ) {
				sb.append( ", " );
			}
		}
		sb.append( "]" );
		return sb.toString();
	}

	/**
	 * Returns the value of the given column if part of this key. Use {@link AssociationKeyMetadata#isKeyColumn(String)}
	 * to check whether a given column is part of this key prior to invoking this method.
	 *
	 * @param columnName the name of interest
	 * @return the value of the given column.
	 */
	public Object getColumnValue(String columnName) {
		for ( int i = 0; i < getColumnNames().length; i++ ) {
			String name = getColumnNames()[i];
			if ( name.equals( columnName ) ) {
				return getColumnValues()[i];
			}
		}

		throw new AssertionFailure(
				String.format( "Given column %s is not part of this key: %s", columnName, this.toString() )
		);
	}
}
