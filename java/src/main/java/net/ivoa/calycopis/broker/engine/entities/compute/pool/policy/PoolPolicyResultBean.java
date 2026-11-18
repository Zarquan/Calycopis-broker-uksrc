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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.ivoa.calycopis.broker.engine.entities.message.Message;

/**
 * Immutable pool policy result.
 *
 */
public class PoolPolicyResultBean
implements PoolPolicyResult
    {
    private final PoolPolicyResultEnum result;
    private final List<Message> reasons;

    /**
     * Public constructor.
     *
     */
    public PoolPolicyResultBean(
        final PoolPolicyResultEnum result,
        final List<Message> reasons
        ){
        this.result = result;
        if (reasons == null)
            {
            this.reasons = Collections.emptyList();
            }
        else {
            this.reasons = Collections.unmodifiableList(
                new ArrayList<Message>(reasons)
                );
            }
        }

    /**
     * An acceptance with no reasons.
     *
     */
    public static PoolPolicyResultBean accepted()
        {
        return new PoolPolicyResultBean(
            PoolPolicyResultEnum.ACCEPTED,
            Collections.emptyList()
            );
        }

    /**
     * A decline with its reasons.
     *
     */
    public static PoolPolicyResultBean declined(final List<Message> reasons)
        {
        return new PoolPolicyResultBean(
            PoolPolicyResultEnum.DECLINED,
            reasons
            );
        }

    @Override
    public PoolPolicyResultEnum getResult()
        {
        return this.result;
        }

    @Override
    public List<Message> getReasons()
        {
        return this.reasons;
        }
    }
