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

import java.util.List;

import net.ivoa.calycopis.broker.engine.entities.message.Message;
import net.ivoa.calycopis.broker.engine.entities.message.MessageBean;
import net.ivoa.calycopis.openapi.spring.model.IvoaMessageItem.LevelEnum;

/**
 * Helpers for the reasons a policy gives when it declines.
 *
 */
final class PoolPolicyMessages
    {
    /**
     * Private constructor, this is a utility class.
     *
     */
    private PoolPolicyMessages()
        {
        }

    /**
     * Build a decline result with one INFO reason.
     *
     */
    static PoolPolicyResultBean decline(
        final String type,
        final String template
        ){
        Message reason = new MessageBean(
            LevelEnum.INFO,
            type,
            template
            );
        return PoolPolicyResultBean.declined(
            List.of(reason)
            );
        }
    }
