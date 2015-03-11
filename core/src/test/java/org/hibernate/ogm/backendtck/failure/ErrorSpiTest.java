/*
 * Hibernate OGM, Domain model persistence for NoSQL datastores
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.ogm.backendtck.failure;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;

import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import org.hibernate.StaleObjectStateException;
import org.hibernate.cfg.Configuration;
import org.hibernate.ogm.OgmSession;
import org.hibernate.ogm.cfg.OgmProperties;
import org.hibernate.ogm.dialect.batch.spi.BatchableGridDialect;
import org.hibernate.ogm.dialect.impl.GridDialects;
import org.hibernate.ogm.dialect.optimisticlock.spi.OptimisticLockingAwareGridDialect;
import org.hibernate.ogm.dialect.spi.DuplicateInsertPreventionStrategy;
import org.hibernate.ogm.dialect.spi.GridDialect;
import org.hibernate.ogm.failure.ErrorHandler.RollbackContext;
import org.hibernate.ogm.failure.operation.CreateTupleWithKey;
import org.hibernate.ogm.failure.operation.ExecuteBatch;
import org.hibernate.ogm.failure.operation.GridDialectOperation;
import org.hibernate.ogm.failure.operation.InsertOrUpdateTuple;
import org.hibernate.ogm.failure.operation.UpdateTupleWithOptimisticLock;
import org.hibernate.ogm.model.impl.DefaultEntityKeyMetadata;
import org.hibernate.ogm.utils.OgmTestCase;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * Tests around the Error reporting SPI.
 *
 * @author Gunnar Morling
 */
public class ErrorSpiTest extends OgmTestCase {

	private static ExecutorService executor;

	@BeforeClass
	public static void setUpExecutor() {
		ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat( "ogm-test-thread-%d" ).build();
		executor = Executors.newSingleThreadExecutor( threadFactory );
	}

	@Test
	public void onRollbackPresentsAppliedInsertOperations() {
		OgmSession session = openSession();
		session.getTransaction().begin();

		// given two inserted records
		session.persist( new Shipment( "shipment-1", "INITIAL" ) );
		session.persist( new Shipment( "shipment-2", "INITIAL" ) );
		session.flush();
		session.clear();

		try {
			// when provoking a duplicate-key exception
			session.persist( new Shipment( "shipment-1", "INITIAL" ) );

			session.getTransaction().commit();
		}
		catch (Exception e) {
			session.getTransaction().rollback();
		}

		// then expect the ops for inserting the two records
		Iterator<RollbackContext> onRollbackInvocations = InvocationTrackingHandler.INSTANCE.getOnRollbackInvocations().iterator();
		Iterator<GridDialectOperation> appliedOperations = onRollbackInvocations.next().getAppliedGridDialectOperations().iterator();
		assertThat( onRollbackInvocations.hasNext() ).isFalse();

		if ( currentDialectHasFacet( BatchableGridDialect.class ) ) {
			assertThat( appliedOperations.next() ).isInstanceOf( CreateTupleWithKey.class );
			assertThat( appliedOperations.next() ).isInstanceOf( CreateTupleWithKey.class );
			assertThat( appliedOperations.next() ).isInstanceOf( ExecuteBatch.class );
		}
		else {
			assertThat( appliedOperations.next() ).isInstanceOf( CreateTupleWithKey.class );
			assertThat( appliedOperations.next() ).isInstanceOf( InsertOrUpdateTuple.class );
			assertThat( appliedOperations.next() ).isInstanceOf( CreateTupleWithKey.class );
			assertThat( appliedOperations.next() ).isInstanceOf( InsertOrUpdateTuple.class );
		}

		// If LOOK_UP is used for duplicate prevention, the duplicated id will be detected prior to the actual insert
		// itself; otherwise, the CreateTuple call will succeed, and only the insert call will fail
		if ( currentDialectUsesLookupDuplicatePreventionStrategy() ) {
			assertThat( appliedOperations.hasNext() ).isFalse();
		}
		else {
			assertThat( appliedOperations.next() ).isInstanceOf( CreateTupleWithKey.class );
		}

		session.close();
	}

	@Test
	public void onRollbackPresentsAppliedInsertOperationsForSave() {
		OgmSession session = openSession();
		session.getTransaction().begin();

		// given two inserted records
		session.persist( new Shipment( "shipment-1", "INITIAL" ) );
		session.persist( new Shipment( "shipment-2", "INITIAL" ) );
		session.flush();
		session.clear();

		try {
			// when provoking a duplicate-key exception
			session.save( new Shipment( "shipment-1", "INITIAL" ) );
			session.getTransaction().commit();
		}
		catch (Exception e) {
			session.getTransaction().rollback();
		}

		// then expect the ops for inserting the two records
		Iterator<RollbackContext> onRollbackInvocations = InvocationTrackingHandler.INSTANCE.getOnRollbackInvocations().iterator();
		Iterator<GridDialectOperation> appliedOperations = onRollbackInvocations.next().getAppliedGridDialectOperations().iterator();
		assertThat( onRollbackInvocations.hasNext() ).isFalse();

		if ( currentDialectHasFacet( BatchableGridDialect.class ) ) {
			assertThat( appliedOperations.next() ).isInstanceOf( CreateTupleWithKey.class );
			assertThat( appliedOperations.next() ).isInstanceOf( CreateTupleWithKey.class );
			assertThat( appliedOperations.next() ).isInstanceOf( ExecuteBatch.class );
		}
		else {
			assertThat( appliedOperations.next() ).isInstanceOf( CreateTupleWithKey.class );
			assertThat( appliedOperations.next() ).isInstanceOf( InsertOrUpdateTuple.class );
			assertThat( appliedOperations.next() ).isInstanceOf( CreateTupleWithKey.class );
			assertThat( appliedOperations.next() ).isInstanceOf( InsertOrUpdateTuple.class );
		}

		// If LOOK_UP is used for duplicate prevention, the duplicated id will be detected prior to the actual insert
		// itself; otherwise, the CreateTuple call will succeed, and only the insert call will fail
		if ( currentDialectUsesLookupDuplicatePreventionStrategy() ) {
			assertThat( appliedOperations.hasNext() ).isFalse();
		}
		else {
			assertThat( appliedOperations.next() ).isInstanceOf( CreateTupleWithKey.class );
		}

		session.close();
	}

	@Test

	public void onRollbackPresentsAppliedUpdateOperations() throws Exception {
		OgmSession session = openSession();
		session.getTransaction().begin();

		Shipment shipment1 = new Shipment( "shipment-1", "INITIAL" );
		session.persist( shipment1 );

		Shipment shipment2 = new Shipment( "shipment-2", "INITIAL" );
		session.persist( shipment2 );

		session.getTransaction().commit();
		session.clear();
		session.getTransaction().begin();

		try {
			Shipment loadedShipment1 = (Shipment) session.get( Shipment.class, "shipment-1" );
			Shipment loadedShipment2 = (Shipment) session.get( Shipment.class, "shipment-2" );

			// do an update in parallel and wait until its done
			Future<?> future = updateShipmentInConcurrentThread( "shipment-2", "PROCESSING" );
			future.get();

			loadedShipment1.setState( "PROCESSING" );
			loadedShipment2.setState( "PROCESSING" );

			session.getTransaction().commit();

			fail( "expected exception was not raised" );
		}
		catch (StaleObjectStateException sose) {
			// Expected
		}
		finally {
			session.getTransaction().rollback();
			session.close();
		}

		Iterator<RollbackContext> onRollbackInvocations = InvocationTrackingHandler.INSTANCE.getOnRollbackInvocations().iterator();
		Iterator<GridDialectOperation> appliedOperations = onRollbackInvocations.next().getAppliedGridDialectOperations().iterator();
		assertThat( onRollbackInvocations.hasNext() ).isFalse();

		if ( currentDialectHasFacet( OptimisticLockingAwareGridDialect.class ) ) {
			GridDialectOperation appliedOperation = appliedOperations.next();
			assertThat( appliedOperation ).isInstanceOf( UpdateTupleWithOptimisticLock.class );
			UpdateTupleWithOptimisticLock updateTupleWithOptimisticLock = appliedOperation.as( UpdateTupleWithOptimisticLock.class );
			assertThat( updateTupleWithOptimisticLock.getEntityKey().getTable() ).isEqualTo( "Shipment" );
			assertThat( updateTupleWithOptimisticLock.getEntityKey().getColumnValues() ).isEqualTo( new Object[] { "shipment-1" } );
		}
		else {
			GridDialectOperation appliedOperation = appliedOperations.next();
			assertThat( appliedOperation ).isInstanceOf( InsertOrUpdateTuple.class );
			InsertOrUpdateTuple insertOrUpdate = appliedOperation.as( InsertOrUpdateTuple.class );
			assertThat( insertOrUpdate.getEntityKey().getTable() ).isEqualTo( "Shipment" );
			assertThat( insertOrUpdate.getEntityKey().getColumnValues() ).isEqualTo( new Object[] { "shipment-1" } );
		}

		assertThat( appliedOperations.hasNext() ).isFalse();
	}

	@Test
	@SkipByGridDialect(
			value = { GridDialectType.NEO4J, GridDialectType.INFINISPAN, GridDialectType.EHCACHE },
			comment = "Can use parallel local TX not with JTA"
	)
	public void appliedOperationsPassedToErrorHandlerAreSeparatedByTransaction() throws Exception {
		OgmSession session = openSession();
		session.getTransaction().begin();

		session.persist( new Shipment( "shipment-1", "INITIAL" ) );
		session.persist( new Shipment( "shipment-2", "INITIAL" ) );
		session.persist( new Shipment( "shipment-3", "INITIAL" ) );

		session.getTransaction().commit();
		session.close();


		OgmSession sessionA = openSession();
		sessionA.getTransaction().begin();

		OgmSession sessionB = openSession();
		sessionB.getTransaction().begin();

		try {
			Shipment loadedShipment1A = (Shipment) sessionA.get( Shipment.class, "shipment-1" );
			Shipment loadedShipment2B = (Shipment) sessionB.get( Shipment.class, "shipment-2" );
			Shipment loadedShipment3B = (Shipment) sessionB.get( Shipment.class, "shipment-3" );

			// do an update in parallel which will cause the rollback of TX B and wait until its done
			Future<?> future = updateShipmentInConcurrentThread( "shipment-3", "PROCESSING" );
			future.get();

			loadedShipment1A.setState( "PROCESSING" );
			sessionA.flush();

			loadedShipment2B.setState( "PROCESSING" );
			loadedShipment3B.setState( "PROCESSING" );

			sessionA.getTransaction().commit();
			sessionB.getTransaction().commit();

			fail( "expected exception was not raised" );
		}
		catch (StaleObjectStateException sose) {
			// Expected
		}
		finally {
			sessionB.getTransaction().rollback();

			sessionA.close();
			sessionB.close();
		}

		// The update to shipment-1 is done by TX A, so only the update to shipment-2 is expected in the applied ops by TX B
		// upon rollback due to the failure of the update to shipment-3
		Iterator<RollbackContext> onRollbackInvocations = InvocationTrackingHandler.INSTANCE.getOnRollbackInvocations().iterator();
		Iterator<GridDialectOperation> appliedOperations = onRollbackInvocations.next().getAppliedGridDialectOperations().iterator();
		assertThat( onRollbackInvocations.hasNext() ).isFalse();

		if ( currentDialectHasFacet( OptimisticLockingAwareGridDialect.class ) ) {
			GridDialectOperation appliedOperation = appliedOperations.next();
			assertThat( appliedOperation ).isInstanceOf( UpdateTupleWithOptimisticLock.class );
			UpdateTupleWithOptimisticLock updateTupleWithOptimisticLock = appliedOperation.as( UpdateTupleWithOptimisticLock.class );
			assertThat( updateTupleWithOptimisticLock.getEntityKey().getTable() ).isEqualTo( "Shipment" );
			assertThat( updateTupleWithOptimisticLock.getEntityKey().getColumnValues() ).isEqualTo( new Object[] { "shipment-2" } );
		}
		else {
			GridDialectOperation appliedOperation = appliedOperations.next();
			assertThat( appliedOperation ).isInstanceOf( InsertOrUpdateTuple.class );
			InsertOrUpdateTuple insertOrUpdate = appliedOperation.as( InsertOrUpdateTuple.class );
			assertThat( insertOrUpdate.getEntityKey().getTable() ).isEqualTo( "Shipment" );
			assertThat( insertOrUpdate.getEntityKey().getColumnValues() ).isEqualTo( new Object[] { "shipment-2" } );
		}
	}

	private Future<?> updateShipmentInConcurrentThread(final String id, final String newState) {
		return executor.submit( new Runnable() {

			@Override
			public void run() {
				OgmSession session = openSession();
				session.getTransaction().begin();

				Shipment shipment = (Shipment) session.get( Shipment.class, id );
				shipment.setState( newState );

				session.getTransaction().commit();
				session.close();
			}

		} );
	}

	@After
	public void deleteTestDataAndResetErrorHandler() {
		OgmSession session = openSession();
		session.getTransaction().begin();

		Shipment shipment = (Shipment) session.get( Shipment.class, "shipment-1" );
		if ( shipment != null ) {
			session.delete( shipment );
		}

		shipment = (Shipment) session.get( Shipment.class, "shipment-2" );
		if ( shipment != null ) {
			session.delete( shipment );
		}

		shipment = (Shipment) session.get( Shipment.class, "shipment-3" );
		if ( shipment != null ) {
			session.delete( shipment );
		}

		shipment = (Shipment) session.get( Shipment.class, "shipment-4" );
		if ( shipment != null ) {
			session.delete( shipment );
		}

		session.getTransaction().commit();
		session.close();

		InvocationTrackingHandler.INSTANCE.clear();
	}

	@Override
	protected void configure(Configuration cfg) {
		cfg.getProperties().put( OgmProperties.ERROR_HANDLER, InvocationTrackingHandler.INSTANCE );
	}

	@Override
	protected Class<?>[] getAnnotatedClasses() {
		return new Class<?>[] { Shipment.class };
	}

	private boolean currentDialectHasFacet(Class<? extends GridDialect> facet) {
		GridDialect gridDialect = sfi().getServiceRegistry().getService( GridDialect.class );
		return GridDialects.hasFacet( gridDialect, OptimisticLockingAwareGridDialect.class );
	}

	private boolean currentDialectUsesLookupDuplicatePreventionStrategy() {
		GridDialect gridDialect = sfi().getServiceRegistry().getService( GridDialect.class );
		DefaultEntityKeyMetadata ekm = new DefaultEntityKeyMetadata( "Shipment", new String[]{"id"} );

		return gridDialect.getDuplicateInsertPreventionStrategy( ekm ) == DuplicateInsertPreventionStrategy.LOOK_UP;
	}
}
