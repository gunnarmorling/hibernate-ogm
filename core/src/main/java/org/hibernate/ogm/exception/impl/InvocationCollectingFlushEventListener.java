/*
 * Hibernate OGM, Domain model persistence for NoSQL datastores
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.ogm.exception.impl;

import org.hibernate.HibernateException;
import org.hibernate.event.service.spi.DuplicationStrategy;
import org.hibernate.event.spi.AutoFlushEventListener;
import org.hibernate.event.spi.FlushEvent;
import org.hibernate.event.spi.FlushEventListener;
import org.hibernate.ogm.util.impl.EffectivelyFinal;

/**
 * Delegating {@link AutoFlushEventListener} which manages the {@link GridDialectInvocationCollector}.
 *
 * @author Gunnar Morling
 *
 */
public class InvocationCollectingFlushEventListener implements FlushEventListener {

	private final GridDialectInvocationCollector invocationCollector;
	private final ErrorHandlerService errorHandler;
	@EffectivelyFinal private FlushEventListener delegate;

	public InvocationCollectingFlushEventListener(GridDialectInvocationCollector invocationCollector, ErrorHandlerService errorHandler) {
		this.invocationCollector = invocationCollector;
		this.errorHandler = errorHandler;
	}

	public void setDelegate(FlushEventListener delegate) {
		this.delegate = delegate;
	}

	@Override
	public void onFlush(FlushEvent event) throws HibernateException {
		try {
			delegate.onFlush( event );
		}
		finally {
			errorHandler.onFlush();
			invocationCollector.finishFlushCycle();
		}
	}

	public static class InvocationCollectingFlushEventListenerDuplicationStrategy implements DuplicationStrategy {

		@Override
		public boolean areMatch(Object listener, Object original) {
			if ( listener instanceof InvocationCollectingFlushEventListener && original instanceof FlushEventListener ) {
				( (InvocationCollectingFlushEventListener) listener ).setDelegate( (FlushEventListener) original );
				return true;
			}

			return false;
		}

		@Override
		public Action getAction() {
			return Action.REPLACE_ORIGINAL;
		}
	}
}
