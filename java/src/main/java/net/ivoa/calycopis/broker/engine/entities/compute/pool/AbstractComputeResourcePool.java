/*
 * <meta:header>
 *   <meta:licence>
 *     Copyright (C) 2026 University of Manchester.
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
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *   </meta:licence>
 * </meta:header>
 *
 * AIMetrics: [
 *     {
 *     "interval": "2026-11-16T07:05:00/2026-11-16T08:20:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 100,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-11-17T15:55:00/2026-11-17T18:30:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 20,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-11-17T18:30:00/2026-11-17T18:50:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 5,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.entities.compute.pool;

import net.ivoa.calycopis.broker.engine.entities.component.Component;
import net.ivoa.calycopis.broker.engine.entities.compute.AbstractComputeReservation;

/**
 * A named source of compute capacity, managed by an administrator.
 *
 * A pool holds capacity rather than bookings, so it keeps the word resource in
 * its name. What it holds is described by its allocations, and what it will
 * accept by its policies; both arrive in later stages.
 *
 * A pool is not a lifecycle component. Whether it can serve a request depends
 * on who is asking and when, so that answer belongs to its policies rather
 * than to a phase.
 *
 */
public interface AbstractComputeResourcePool
extends Component
    {

    /**
     * The name of the pool a deployment gets when it configures nothing.
     *
     * The request path uses this pool until pools can be ordered and selected
     * by policy, and the bootstrap guarantees it exists.
     *
     */
    public static final String DEFAULT_POOL_NAME = "default" ;

    /**
     * Get the live reservations made from this pool.
     *
     * A reservation is live while its session holds capacity, which is the
     * same set of phases the booking query counts. Terminal reservations stay
     * in the database but are not part of this view.
     *
     */
    public Iterable<AbstractComputeReservation> getReservations();

    }

