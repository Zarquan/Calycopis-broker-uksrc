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

package net.ivoa.calycopis.broker.spring.pools;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds the calycopis.broker.pools section of the application configuration.
 *
 * The list is empty by default, so a broker without a pools.yaml behaves as the
 * parent plan requires: one default pool, created by the bootstrap.
 *
 */
@Component
@ConfigurationProperties(prefix = "calycopis.broker.pools")
public class ComputePoolsSettingsImpl
implements ComputePoolsSettings
    {
    private List<ComputePoolSpec> compute = new ArrayList<>();

    @Override
    public List<ComputePoolSpec> getCompute()
        {
        return this.compute;
        }

    public void setCompute(final List<ComputePoolSpec> compute)
        {
        this.compute = compute;
        }

    }

