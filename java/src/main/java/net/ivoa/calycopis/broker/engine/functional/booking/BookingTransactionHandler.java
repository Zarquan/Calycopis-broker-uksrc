/*
 * <meta:header>
 *   <meta:licence>
 *     Copyright (c) 2026, University of Manchester (http://www.manchester.ac.uk/)
 *
 *     This information is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This information is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this software. If not, see <http://www.gnu.org/licenses/>.
 *   </meta:licence>
 * </meta:header>
 *
 * AIMetrics: [
 *     {
 *     "interval": "2026-10-15T09:30:00/2026-10-15T09:46:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 100,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.functional.booking;

import java.util.function.Supplier;

/**
 * Framework-neutral interface for a transactional booking step.
 *
 * Booking is staged: one resource type's provisional set of offers is
 * generated and its reservations committed before the next resource type is
 * considered. Each stage runs through this handler, which
 *
 *  * runs the booking step in its own transaction, so the reservations it
 *    produces are committed as one unit and become visible to a concurrent
 *    request in one step; and
 *  * holds a lock scoped to the resource type for the length of that
 *    transaction, so a concurrent request for the same resource type waits
 *    instead of generating a second set of offers against the same free
 *    space.
 *
 * The lock is scoped to the resource type, not to the offer set, so stages for
 * different resource types do not block one another. That is what keeps the
 * staged pipeline in issue #169 possible, and it is why the staging, rather
 * than a single transaction over the whole response, is the unit here.
 *
 * The resource type is the OpenAPI kind discriminator of the resource being
 * booked, for example {@code SimpleComputeResource.KIND_DISCRIMINATOR}.
 *
 */
public interface BookingTransactionHandler
    {

    /**
     * Run a booking step in its own transaction, holding the lock for the
     * given resource type.
     *
     * The booking step should run the booking query and commit the
     * reservations that the query's offers depend on, so that no other request
     * can book the same space in between.
     *
     * @param resourceType the OpenAPI kind discriminator of the resource type
     *                     whose provisional set is being generated.
     * @param booking      the booking step to run inside the transaction.
     *
     */
    public <T> T book(final String resourceType, final Supplier<T> booking);

    }
