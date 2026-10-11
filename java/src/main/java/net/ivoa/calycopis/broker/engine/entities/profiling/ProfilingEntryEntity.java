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
 *     "timestamp": "2026-10-11T11:15:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 100,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */
package net.ivoa.calycopis.broker.engine.entities.profiling;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import net.ivoa.calycopis.broker.engine.entities.offerset.OfferSetEntity;

/**
 * JPA Entity for ProfilingEntry measurements.
 *
 */
@Entity
@Table(
    name = "profiling_entries"
    )
public class ProfilingEntryEntity
implements ProfilingEntry
    {

    @Id
    @GeneratedValue
    private Long ident;

    @JoinColumn(name = "parent", referencedColumnName = "uuid", nullable = false)
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private OfferSetEntity parent;

    public OfferSetEntity getParent()
        {
        return this.parent;
        }

    public void setParent(final OfferSetEntity parent)
        {
        this.parent = parent;
        }

    /**
     * Protected constructor for JPA entities.
     *
     */
    protected ProfilingEntryEntity()
        {
        }

    /**
     * Public constructor, sets the start time to now.
     *
     */
    public ProfilingEntryEntity(final String className, final String methodName)
        {
        this.className = className;
        this.methodName = methodName;
        this.startTime = Instant.now();
        }

    @Column(name = "class_name")
    private String className;

    @Override
    public String getClassName()
        {
        return this.className;
        }

    @Column(name = "method_name")
    private String methodName;

    @Override
    public String getMethodName()
        {
        return this.methodName;
        }

    @Column(name = "start_time")
    private Instant startTime;

    @Override
    public Instant getStartTime()
        {
        return this.startTime;
        }

    @Column(name = "end_time")
    private Instant endTime;

    @Override
    public Instant getEndTime()
        {
        return this.endTime;
        }

    @Override
    public void start()
        {
        this.startTime = Instant.now();
        }

    @Override
    public void end()
        {
        this.endTime = Instant.now();
        }

    }
