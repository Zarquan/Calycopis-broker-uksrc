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
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.entities.compute.pool.simple;

import java.net.URI;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.extern.slf4j.Slf4j;

import net.ivoa.calycopis.broker.engine.entities.compute.pool.AbstractComputeResourcePoolEntity;

/**
 * The concrete compute pool entity.
 *
 * It carries nothing beyond the component base for now. Allocations arrive in
 * the next stage and policies after that, so there is no capacity column and no
 * policy table to add yet.
 *
 */
@Slf4j
@Entity
@Table(
    name = "simplecomputeresourcepools"
    )
@DiscriminatorValue(
    value = "uri:compute-pool"
    )
public class SimpleComputeResourcePoolEntity
extends AbstractComputeResourcePoolEntity
implements SimpleComputeResourcePool
    {
    /**
     * Protected no-arg constructor required by JPA.
     *
     */
    protected SimpleComputeResourcePoolEntity()
        {
        super();
        }

    /**
     * Protected constructor used by our factories.
     *
     */
    public SimpleComputeResourcePoolEntity(
        final String name,
        final String description
        ){
        super(
            name,
            description
            );
        }

    @Override
    public URI getKind()
        {
        return SimpleComputeResourcePool.KIND_DISCRIMINATOR;
        }

    }

