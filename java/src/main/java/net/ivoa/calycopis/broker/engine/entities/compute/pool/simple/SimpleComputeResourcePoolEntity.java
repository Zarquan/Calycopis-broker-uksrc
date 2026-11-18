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
 *     "interval": "2026-11-18T11:15:00/2026-11-18T11:55:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 20,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.entities.compute.pool.simple;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.extern.slf4j.Slf4j;

import net.ivoa.calycopis.broker.engine.entities.compute.pool.AbstractComputeResourcePoolEntity;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeOffer;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeOfferBean;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeReservationQuery;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeReservationQueryHandler;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeResourceCriteria;

/**
 * The concrete compute pool entity.
 *
 * It carries the component base and its name. Capacity comes from the pool's
 * allocations, and the pool makes offers from the default booking criteria.
 * Pool policies arrive in the next stage, so there is no policy table yet.
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

    @Override
    public Iterable<SimpleComputeOffer> makeOffers(
        final SimpleComputeResourceCriteria criteria,
        final SimpleComputeReservationQueryHandler queryHandler
        ){
        String query = SimpleComputeReservationQuery.build(
            criteria,
            this.getUuid()
            );

        Iterable<SimpleComputeOffer> queryOffers = queryHandler.query(
            query
            );

        List<SimpleComputeOffer> offers = new ArrayList<SimpleComputeOffer>();
        for (SimpleComputeOffer queryOffer : queryOffers)
            {
            offers.add(
                SimpleComputeOfferBean.withPool(
                    queryOffer,
                    this
                    )
                );
            }
        return offers;
        }

    }

