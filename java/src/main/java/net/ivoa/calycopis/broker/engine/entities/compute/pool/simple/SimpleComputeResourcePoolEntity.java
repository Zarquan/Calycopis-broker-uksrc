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
 *     },
 *     {
 *     "interval": "2026-11-18T14:55:00/2026-11-18T17:00:00",
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
import java.util.Map;

import jakarta.persistence.CascadeType;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import lombok.extern.slf4j.Slf4j;

import net.ivoa.calycopis.broker.engine.entities.compute.pool.AbstractComputeResourcePoolEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.policy.PoolPolicy;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.policy.PoolPolicyResult;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.policy.PoolPolicyResultEnum;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.policy.SimpleComputePoolPolicyEntity;
import net.ivoa.calycopis.broker.engine.entities.offerset.OfferSetRequestParserContext;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeOffer;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeOfferBean;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeReservationQuery;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeReservationQueryHandler;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeResourceCriteria;

/**
 * The concrete compute pool entity.
 *
 * It carries the component base and its name. Capacity comes from the pool's
 * allocations, and its policies decide which requests the pool will consider.
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

    /**
     * Public constructor used by our factories.
     *
     */
    public SimpleComputeResourcePoolEntity(
        final String name,
        final String description,
        final int priority,
        final boolean enabled
        ){
        super(
            name,
            description,
            priority,
            enabled
            );
        }

    @OneToMany(
        mappedBy = "pool",
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL,
        orphanRemoval = true
        )
    @OrderBy(
        "created ASC, uuid ASC"
        )
    private List<SimpleComputePoolPolicyEntity> policies = new ArrayList<SimpleComputePoolPolicyEntity>();

    /**
     * Get the policy rows attached to this pool.
     *
     */
    public Iterable<SimpleComputePoolPolicyEntity> getPolicies()
        {
        return this.policies;
        }

    /**
     * Attach a policy row to this pool in memory.
     *
     */
    public void addPolicy(final SimpleComputePoolPolicyEntity policy)
        {
        policy.setPool(
            this
            );
        this.policies.add(
            policy
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
        for (SimpleComputePoolPolicyEntity policyEntity : this.policies)
            {
            PoolPolicy policy = policyEntity.makePolicy();
            PoolPolicyResult result = policy.accepts(
                criteria
                );
            if (result.getResult() == PoolPolicyResultEnum.DECLINED)
                {
                OfferSetRequestParserContext request = criteria.getRequest();
                if (request != null)
                    {
                    request.addInfo(
                        "urn:compute-pool-declined",
                        "Compute pool [" + this.getName()
                            + "] declined the request because policy ["
                            + policy.getName() + "] rejected it.",
                        Map.of(
                            "pool",
                            this.getName(),
                            "policy",
                            policy.getName()
                            )
                        );
                    request.addMessages(
                        result.getReasons()
                        );
                    }
                return new ArrayList<SimpleComputeOffer>();
                }
            }

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

