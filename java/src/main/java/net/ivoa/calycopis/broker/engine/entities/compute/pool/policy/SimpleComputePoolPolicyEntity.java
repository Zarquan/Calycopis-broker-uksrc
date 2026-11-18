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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import net.ivoa.calycopis.broker.engine.entities.compute.pool.AbstractComputeResourcePoolEntity;

/**
 * A policy row attached to one compute pool.
 *
 * The entity stores the configuration. It builds a PoolPolicy only when a
 * request needs a decision, so a pool with no policies pays nothing for them.
 *
 */
@Entity
@Table(
    name = "simplecomputepoolpolicies"
    )
public class SimpleComputePoolPolicyEntity
    {
    /**
     * Protected constructor for JPA entities.
     *
     */
    protected SimpleComputePoolPolicyEntity()
        {
        super();
        }

    /**
     * Public constructor used by the factory.
     *
     */
    public SimpleComputePoolPolicyEntity(
        final AbstractComputeResourcePoolEntity pool,
        final SimpleComputePoolPolicyKind kind,
        final String name,
        final Long minimumMemory,
        final Long maximumMemory,
        final String username,
        final Integer startHour,
        final Integer endHour
        ){
        Optional<String> error = SimpleComputePoolPolicyValidation.validate(
            kind.name(),
            name,
            minimumMemory,
            maximumMemory,
            username,
            startHour,
            endHour
            );
        if (error.isPresent())
            {
            throw new IllegalArgumentException(
                error.get()
                );
            }
        this.pool = pool;
        this.kind = kind;
        this.name = name.trim();
        this.minimumMemory = minimumMemory;
        this.maximumMemory = maximumMemory;
        this.username = username;
        this.startHour = startHour;
        this.endHour = endHour;
        this.created = Instant.now();
        }

    @Id
    @GeneratedValue
    private UUID uuid;

    public UUID getUuid()
        {
        return this.uuid;
        }

    @ManyToOne(
        fetch = FetchType.LAZY,
        optional = false
        )
    @JoinColumn(
        name = "pool_uuid",
        referencedColumnName = "uuid",
        nullable = false
        )
    private AbstractComputeResourcePoolEntity pool;

    public AbstractComputeResourcePoolEntity getPool()
        {
        return this.pool;
        }

    public void setPool(final AbstractComputeResourcePoolEntity pool)
        {
        this.pool = pool;
        }

    @Enumerated(EnumType.STRING)
    @Column(
        name = "kind",
        nullable = false
        )
    private SimpleComputePoolPolicyKind kind;

    public SimpleComputePoolPolicyKind getKind()
        {
        return this.kind;
        }

    @Column(
        name = "name",
        nullable = false
        )
    private String name;

    public String getName()
        {
        return this.name;
        }

    @Column(name = "minimum_memory")
    private Long minimumMemory;

    public Long getMinimumMemory()
        {
        return this.minimumMemory;
        }

    @Column(name = "maximum_memory")
    private Long maximumMemory;

    public Long getMaximumMemory()
        {
        return this.maximumMemory;
        }

    @Column(name = "username")
    private String username;

    public String getUsername()
        {
        return this.username;
        }

    @Column(name = "start_hour")
    private Integer startHour;

    public Integer getStartHour()
        {
        return this.startHour;
        }

    @Column(name = "end_hour")
    private Integer endHour;

    public Integer getEndHour()
        {
        return this.endHour;
        }

    @Column(
        name = "created",
        nullable = false
        )
    private Instant created;

    public Instant getCreated()
        {
        return this.created;
        }

    /**
     * Change this policy in place, after validating the new configuration.
     *
     */
    public void update(
        final SimpleComputePoolPolicyKind kind,
        final String name,
        final Long minimumMemory,
        final Long maximumMemory,
        final String username,
        final Integer startHour,
        final Integer endHour
        ){
        Optional<String> error = SimpleComputePoolPolicyValidation.validate(
            kind.name(),
            name,
            minimumMemory,
            maximumMemory,
            username,
            startHour,
            endHour
            );
        if (error.isPresent())
            {
            throw new IllegalArgumentException(
                error.get()
                );
            }
        this.kind = kind;
        this.name = name.trim();
        this.minimumMemory = minimumMemory;
        this.maximumMemory = maximumMemory;
        this.username = username;
        this.startHour = startHour;
        this.endHour = endHour;
        }

    /**
     * Build the runtime policy this row describes.
     *
     */
    public PoolPolicy makePolicy()
        {
        switch (this.kind)
            {
            case MEMORY_FLOOR:
                return new MemoryFloorPolicy(
                    this.name,
                    this.minimumMemory.longValue()
                    );
            case MEMORY_CEILING:
                return new MemoryCeilingPolicy(
                    this.name,
                    this.maximumMemory.longValue()
                    );
            case OWNER:
                return new OwnerPolicy(
                    this.name,
                    this.username
                    );
            case TIME_WINDOW:
                return new TimeWindowPolicy(
                    this.name,
                    this.startHour.intValue(),
                    this.endHour.intValue()
                    );
            default:
                throw new IllegalStateException(
                    "Unknown compute pool policy kind [" + this.kind + "]."
                    );
            }
        }
    }
