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

package net.ivoa.calycopis.broker.spring.pools;

/**
 * One compute pool policy as it is written in pools.yaml.
 *
 * The fields are optional in the configuration because each policy kind uses
 * only some of them. The shared validator rejects fields that do not belong to
 * the chosen kind.
 *
 */
public class ComputePolicySpec
    {
    private String kind;
    private String name;
    private Long minimumMemory;
    private Long maximumMemory;
    private String username;
    private Integer startHour;
    private Integer endHour;

    public String getKind()
        {
        return this.kind;
        }

    public void setKind(final String kind)
        {
        this.kind = kind;
        }

    public String getName()
        {
        return this.name;
        }

    public void setName(final String name)
        {
        this.name = name;
        }

    public Long getMinimumMemory()
        {
        return this.minimumMemory;
        }

    public void setMinimumMemory(final Long minimumMemory)
        {
        this.minimumMemory = minimumMemory;
        }

    public Long getMaximumMemory()
        {
        return this.maximumMemory;
        }

    public void setMaximumMemory(final Long maximumMemory)
        {
        this.maximumMemory = maximumMemory;
        }

    public String getUsername()
        {
        return this.username;
        }

    public void setUsername(final String username)
        {
        this.username = username;
        }

    public Integer getStartHour()
        {
        return this.startHour;
        }

    public void setStartHour(final Integer startHour)
        {
        this.startHour = startHour;
        }

    public Integer getEndHour()
        {
        return this.endHour;
        }

    public void setEndHour(final Integer endHour)
        {
        this.endHour = endHour;
        }
    }
