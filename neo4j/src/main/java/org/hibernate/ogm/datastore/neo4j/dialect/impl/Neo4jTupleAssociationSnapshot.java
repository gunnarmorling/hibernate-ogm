/*
 * Hibernate OGM, Domain model persistence for NoSQL datastores
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.ogm.datastore.neo4j.dialect.impl;

import static org.hibernate.ogm.util.impl.EmbeddedHelper.isPartOfEmbedded;
import static org.hibernate.ogm.util.impl.EmbeddedHelper.split;
import static org.neo4j.graphdb.DynamicLabel.label;
import static org.neo4j.graphdb.DynamicRelationshipType.withName;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.hibernate.ogm.model.key.spi.AssociationKey;
import org.hibernate.ogm.model.key.spi.AssociationKeyMetadata;
import org.hibernate.ogm.model.key.spi.AssociationKind;
import org.hibernate.ogm.model.spi.TupleSnapshot;
import org.hibernate.ogm.util.impl.EmbeddedHelper;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

/**
 * @author Davide D'Alto
 */
public class Neo4jTupleAssociationSnapshot implements TupleSnapshot {

	private final Map<String, Object> properties;

	public Neo4jTupleAssociationSnapshot(Relationship relationship, AssociationKey associationKey, AssociationKeyMetadata metadata) {
		properties = collectProperties( relationship, associationKey, metadata );
	}

	private static Map<String, Object> collectProperties(Relationship relationship, AssociationKey associationKey, AssociationKeyMetadata metadata) {
		Map<String, Object> properties = new HashMap<String, Object>();
		String[] rowKeyColumnNames = metadata.getRowKeyColumnNames();

		Node ownerNode = findOwnerNode( relationship, associationKey, metadata );
		Node targetNode = findTargetNode( relationship, metadata, ownerNode );

		// Index columns
		for ( int i = 0; i < rowKeyColumnNames.length; i++ ) {
			if ( relationship.hasProperty( rowKeyColumnNames[i] ) ) {
				properties.put( rowKeyColumnNames[i], relationship.getProperty( rowKeyColumnNames[i] ) );
			}
		}

		// Properties stored in the target side of the association
		for ( String associationColumn : metadata.getAssociatedEntityKeyMetadata().getAssociationKeyColumns() ) {
			String targetColumnName = metadata.getAssociatedEntityKeyMetadata().getCorrespondingEntityKeyColumn( associationColumn );
			if ( isPartOfEmbedded( targetColumnName ) ) {
				// Embedded column
				String collectionRole = metadata.getCollectionRole();
				if ( targetColumnName.equals( collectionRole ) ) {
					// Ex: @ElementCollection List<String> examples
					targetColumnName = targetColumnName.substring( targetColumnName.lastIndexOf( "." ) + 1 );
					if ( targetNode.hasProperty( targetColumnName ) ) {
						properties.put( associationColumn, targetNode.getProperty( targetColumnName ) );
					}
				}
				else if ( targetNode.hasProperty( targetColumnName ) ) {
					// Embedded id
					properties.put( associationColumn, targetNode.getProperty( targetColumnName ) );
				}
				else {
					// Ex: @ElementCollection List<Embedded> examples
					Node embeddedNode = targetNode;

					if ( targetColumnName.startsWith( collectionRole ) ) {
						targetColumnName = targetColumnName.substring( collectionRole.length() + 1 );
					}

					String[] split = split( targetColumnName );
					boolean found = true;
					for ( int i = 0; i < split.length - 1; i++ ) {
						Iterator<Relationship> iterator = embeddedNode.getRelationships( Direction.OUTGOING, withName( split[i] ) ).iterator();
						if ( iterator.hasNext() ) {
							embeddedNode = iterator.next().getEndNode();
						}
						else {
							found = false;
							break;
						}
					}
					if ( found ) {
						targetColumnName = targetColumnName.substring( targetColumnName.lastIndexOf( "." ) + 1 );
						if ( embeddedNode.hasProperty( targetColumnName ) ) {
							properties.put( associationColumn, embeddedNode.getProperty( targetColumnName ) );
						}
					}
				}
			}
			else {
				if ( targetNode.hasProperty( targetColumnName ) ) {
					properties.put( associationColumn, targetNode.getProperty( targetColumnName ) );
				}
			}
		}

		// Property stored in the owner side of the association
		for ( int i = 0; i < associationKey.getColumnNames().length; i++ ) {
			if ( ownerNode.hasProperty( associationKey.getEntityKey().getColumnNames()[i] ) ) {
				properties.put( associationKey.getColumnNames()[i], ownerNode.getProperty( associationKey.getEntityKey().getColumnNames()[i] ) );
			}
		}
		return properties;
	}

	private static Node findTargetNode(Relationship relationship, AssociationKeyMetadata metadata, Node ownerNode) {
		if ( isEmbeddedCollection( metadata ) ) {
			return relationship.getEndNode();
		}
		else {
			return relationship.getOtherNode( ownerNode );
		}
	}

	private static Node findOwnerNode(Relationship relationship, AssociationKey associationKey, AssociationKeyMetadata metadata) {
		if ( isEmbeddedCollection( metadata ) ) {
			String collectionRole = metadata.getCollectionRole();
			return embeddedAssociationOwner( relationship, collectionRole );
		}
		else {
			return ownerNodeFromAssociation( associationKey, relationship );
		}
	}

	private static boolean isEmbeddedCollection(AssociationKeyMetadata metadata) {
		return metadata.getAssociationKind() == AssociationKind.EMBEDDED_COLLECTION;
	}

	private static Node embeddedAssociationOwner(Relationship relationship, String collectionRole) {
		if ( isPartOfEmbedded( collectionRole ) ) {
			String[] split = EmbeddedHelper.split( collectionRole );
			Node ownerNode = relationship.getStartNode();
			for ( int i = 1; i < split.length; i++ ) {
				String type = split[split.length - i - 1];
				Relationship next = ownerNode.getRelationships( Direction.INCOMING, withName( type ) ).iterator().next();
				ownerNode = next.getStartNode();
			}
			return ownerNode;
		}
		else {
			return relationship.getStartNode();
		}
	}

	private static Node ownerNodeFromAssociation(AssociationKey associationKey, Relationship relationship) {
		if ( relationship.getStartNode().hasLabel( label( associationKey.getEntityKey().getTable() ) ) ) {
			return relationship.getStartNode();
		}
		else {
			return relationship.getEndNode();
		}
	}

	@Override
	public Object get(String column) {
		return properties.get( column );
	}

	@Override
	public boolean isEmpty() {
		return properties.isEmpty();
	}

	@Override
	public Set<String> getColumnNames() {
		return properties.keySet();
	}

}
