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
 *     "interval": "2026-11-17T15:55:00/2026-11-17T18:30:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 100,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.entities.compute.allocation.simple;

import java.net.URI;

import net.ivoa.calycopis.broker.engine.entities.compute.allocation.AbstractComputeAllocation;

/**
 * A compute allocation as this deployment implements it.
 *
 * Allocations are not published in the OpenAPI schema yet, so the kind URI is
 * a Calycopis identifier rather than an IVOA one. It follows the precedent set
 * by uri:compute-pool and uri:identity, which are off-schema in the same way.
 *
 */
public interface SimpleComputeAllocation
extends AbstractComputeAllocation
    {
    public static final URI KIND_DISCRIMINATOR = URI.create("uri:compute-allocation") ;

    }
