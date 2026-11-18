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

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import lombok.extern.slf4j.Slf4j;

import net.ivoa.calycopis.broker.engine.entities.component.ComponentEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.AbstractComputeReservation;
import net.ivoa.calycopis.broker.engine.entities.compute.AbstractComputeReservationEntity;

/**
 * The abstract tier of the compute pool entity family.
 *
 * The root table exists because the family is mapped with a joined inheritance
 * strategy, even though there is one concrete type today. A second pool
 * implementation adds an entity under this one, not a second root.
 *
 * A pool has no owner, because the admin credential in admin.yaml is a
 * configuration setting rather than an identity entity. The owner column stays
 * null and the API does not report one.
 *
 */
@Slf4j
@Entity
@Table(
    name = "abstractcomputeresourcepools"
    )
@Inheritance(
    strategy = InheritanceType.JOINED
    )
public abstract class AbstractComputeResourcePoolEntity
extends ComponentEntity
implements AbstractComputeResourcePool
    {
    /**
     * Protected no-arg constructor required by JPA.
     *
     */
    protected AbstractComputeResourcePoolEntity()
        {
        super();
        }

    /**
     * Protected constructor used by our factories.
     *
     */
    protected AbstractComputeResourcePoolEntity(
        final String name,
        final String description
        ){
        super(
            name,
            description,
            Instant.now(),
            null
            );
        }

    @OneToMany(
        mappedBy = "pool",
        fetch = FetchType.LAZY
        )
    private List<AbstractComputeReservationEntity> reservations = new ArrayList<AbstractComputeReservationEntity>();

    @Override
    public Iterable<AbstractComputeReservation> getReservations()
        {
        List<AbstractComputeReservation> live = new ArrayList<AbstractComputeReservation>();
        for (AbstractComputeReservationEntity reservation : this.reservations)
            {
            if (reservation.getSession().isLive())
                {
                live.add(
                    reservation
                    );
                }
            }
        return live;
        }

    @Override
    protected URI getWebappPath()
        {
        return URI.create("/pools/compute/");
        }

    }

