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

/**
 * One compute pool as it is written in pools.yaml.
 *
 * The bootstrap turns these into entities. The description is optional, so it
 * is null when a deployment names a pool and says nothing about it.
 *
 */
public class ComputePoolSpec
    {
    private String name;
    private String description;

    public String getName()
        {
        return this.name;
        }

    public void setName(final String name)
        {
        this.name = name;
        }

    public String getDescription()
        {
        return this.description;
        }

    public void setDescription(final String description)
        {
        this.description = description;
        }

    }

