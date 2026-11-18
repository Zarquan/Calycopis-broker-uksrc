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
 *     along with this software. If not, see <http://www.gnu.org/licenses/>.
 *   </meta:licence>
 * </meta:header>
 *
 * AIMetrics: [
 *     {
 *     "interval": "2026-11-18T13:45:00/2026-11-18T17:00:00",
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

package net.ivoa.calycopis.broker.engine.entities.compute.pool.policy;

import net.ivoa.calycopis.broker.engine.entities.identity.IdentityEntity;
import net.ivoa.calycopis.broker.engine.entities.offerset.OfferSetRequestParserContext;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeResourceCriteria;

/**
 * Accepts requests made by one named owner.
 *
 * Identities do not carry groups yet, so this stage implements an owner
 * allowlist. Group membership can replace this comparison later without
 * changing the pool body.
 *
 */
public class OwnerPolicy
implements PoolPolicy
    {
    public static final String REASON_TYPE = "urn:compute-policy-owner";

    private final String name;
    private final String username;

    public OwnerPolicy(
        final String name,
        final String username
        ){
        this.name = name;
        this.username = username;
        }

    @Override
    public String getName()
        {
        return this.name;
        }

    @Override
    public PoolPolicyResult accepts(final SimpleComputeResourceCriteria criteria)
        {
        if (criteria == null)
            {
            return PoolPolicyMessages.decline(
                REASON_TYPE,
                "No request criteria were supplied."
                );
            }
        OfferSetRequestParserContext request = criteria.getRequest();
        if (request == null)
            {
            return PoolPolicyMessages.decline(
                REASON_TYPE,
                "The request has no owner."
                );
            }
        IdentityEntity owner = request.getOwner();
        if (owner == null)
            {
            return PoolPolicyMessages.decline(
                REASON_TYPE,
                "The request has no owner."
                );
            }
        if (!this.username.equals(owner.getUsername()))
            {
            return PoolPolicyMessages.decline(
                REASON_TYPE,
                "Request owner [" + owner.getUsername()
                    + "] is not [" + this.username + "]."
                );
            }
        return PoolPolicyResultBean.accepted();
        }
    }
