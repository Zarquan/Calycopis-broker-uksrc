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
import java.time.Instant;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.extern.slf4j.Slf4j;

import net.ivoa.calycopis.broker.engine.entities.compute.allocation.AbstractComputeAllocationEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.AbstractComputeResourcePoolEntity;

/**
 * The concrete compute allocation entity.
 *
 * It carries nothing beyond the abstract tier for now: the pool, the window and
 * the sizes are common to every allocation type. A future allocation type adds
 * a subclass of the abstract entity, not a column here.
 *
 */
@Slf4j
@Entity
@Table(
    name = "simplecomputeallocations"
    )
@DiscriminatorValue(
    value = "uri:compute-allocation"
    )
public class SimpleComputeAllocationEntity
extends AbstractComputeAllocationEntity
implements SimpleComputeAllocation
    {
    /**
     * Protected no-arg constructor required by JPA.
     *
     */
    protected SimpleComputeAllocationEntity()
        {
        super();
        }

    /**
     * Protected constructor used by our factories.
     *
     */
    public SimpleComputeAllocationEntity(
        final AbstractComputeResourcePoolEntity pool,
        final long cores,
        final long memory,
        final Instant start,
        final Instant end
        ){
        super(
            pool,
            cores,
            memory,
            start,
            end
            );
        }

    @Override
    public URI getKind()
        {
        return SimpleComputeAllocation.KIND_DISCRIMINATOR;
        }

    }
