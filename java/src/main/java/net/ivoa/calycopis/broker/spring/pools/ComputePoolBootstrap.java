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
 *     "interval": "2026-11-17T15:55:00/2026-11-17T18:30:00",
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

package net.ivoa.calycopis.broker.spring.pools;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import net.ivoa.calycopis.broker.engine.entities.compute.allocation.simple.SimpleComputeAllocationEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.allocation.simple.SimpleComputeAllocationEntityFactory;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.AbstractComputeResourcePool;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.simple.SimpleComputeResourcePoolEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.simple.SimpleComputeResourcePoolEntityFactory;
import net.ivoa.calycopis.broker.engine.functional.booking.BookingCapacitySettings;

/**
 * Creates the compute pools and allocations a deployment declares, once, at
 * startup.
 *
 * The seed is idempotent: a pool is looked up by name before it is created, so
 * a second run against the same database finds it and leaves it, and its
 * allocations, alone.
 *
 * The broker recreates its schema at startup, so in practice the tables are
 * empty when this runs. The lookup still matters for a broker started against a
 * database that was not rebuilt.
 *
 * The default pool is always seeded, even when pools.yaml names only other
 * pools. The request path has no pool ordering to choose from until stage 6, so
 * it needs one pool it can rely on. An enabled flag, to disable the default pool
 * rather than delete it, arrives with the pool policy.
 *
 * A pool that declares no allocations gets one open-ended allocation from the
 * configured capacity. That keeps the seeded supply and the capacity setting in
 * step, and it is what makes a deployment that configures nothing behave as it
 * did when capacity was one global number.
 *
 */
@Slf4j
@Component
public class ComputePoolBootstrap
implements ApplicationRunner
    {
    /**
     * The description of the default pool, which says what it is rather than
     * what it can do.
     *
     */
    public static final String DEFAULT_POOL_DESCRIPTION = "The default compute pool." ;

    /**
     * The start of an allocation the bootstrap creates: it has always been
     * available, so the booking query finds supply in every block it can look
     * at, including the blocks before the requested start.
     *
     */
    public static final Instant SEEDED_ALLOCATION_START = Instant.EPOCH ;

    private final ComputePoolsSettings settings;
    private final SimpleComputeResourcePoolEntityFactory poolFactory;
    private final SimpleComputeAllocationEntityFactory allocationFactory;
    private final BookingCapacitySettings bookingCapacitySettings;

    public ComputePoolBootstrap(
        final ComputePoolsSettings settings,
        final SimpleComputeResourcePoolEntityFactory poolFactory,
        final SimpleComputeAllocationEntityFactory allocationFactory,
        final BookingCapacitySettings bookingCapacitySettings
        ){
        this.settings = settings;
        this.poolFactory = poolFactory;
        this.allocationFactory = allocationFactory;
        this.bookingCapacitySettings = bookingCapacitySettings;
        }

    @Override
    public void run(final ApplicationArguments args) throws Exception
        {
        List<ComputePoolSpec> specs = resolve(
            this.settings.getCompute()
            );
        log.info(
            "Seeding [{}] compute pool(s)",
            specs.size()
            );
        for (ComputePoolSpec spec : specs)
            {
            this.seed(
                spec
                );
            }
        }

    /**
     * The pools to seed: what the configuration declares, plus the default pool
     * when the configuration does not declare it.
     *
     */
    static List<ComputePoolSpec> resolve(final List<ComputePoolSpec> configured)
        {
        List<ComputePoolSpec> specs = new ArrayList<>();
        boolean defaultDeclared = false;
        if (configured != null)
            {
            for (ComputePoolSpec spec : configured)
                {
                specs.add(
                    spec
                    );
                if (isDefault(spec))
                    {
                    defaultDeclared = true;
                    }
                }
            }
        if (!defaultDeclared)
            {
            specs.add(
                defaultSpec()
                );
            }
        return specs;
        }

    /**
     * Whether a specification names the default pool.
     *
     */
    static boolean isDefault(final ComputePoolSpec spec)
        {
        if (spec == null || spec.getName() == null)
            {
            return false;
            }
        return spec.getName().trim().equals(
            AbstractComputeResourcePool.DEFAULT_POOL_NAME
            );
        }

    /**
     * The default pool, as a specification.
     *
     */
    static ComputePoolSpec defaultSpec()
        {
        ComputePoolSpec spec = new ComputePoolSpec();
        spec.setName(
            AbstractComputeResourcePool.DEFAULT_POOL_NAME
            );
        spec.setDescription(
            DEFAULT_POOL_DESCRIPTION
            );
        return spec;
        }

    /**
     * Create one pool, and its allocations, unless a pool of that name already
     * exists.
     *
     */
    private void seed(final ComputePoolSpec spec)
        {
        if (spec == null)
            {
            log.warn(
                "Ignoring an empty compute pool entry in the configuration"
                );
            return;
            }
        String name = spec.getName();
        if (name == null || name.isBlank())
            {
            log.warn(
                "Ignoring a compute pool entry with no name in the configuration"
                );
            return;
            }
        name = name.trim();
        if (this.poolFactory.findByName(name).isPresent())
            {
            log.info(
                "Compute pool [{}] already exists",
                name
                );
            return;
            }
        SimpleComputeResourcePoolEntity entity = this.poolFactory.create(
            name,
            spec.getDescription()
            );
        log.info(
            "Created compute pool [{}] [{}]",
            entity.getName(),
            entity.getUuid()
            );
        this.seedAllocations(
            entity,
            spec
            );
        }

    /**
     * Seed the allocations a pool declares, or one fallback allocation when it
     * declares none.
     *
     */
    private void seedAllocations(
        final SimpleComputeResourcePoolEntity pool,
        final ComputePoolSpec spec
        ){
        List<ComputeAllocationSpec> declared = spec.getAllocations();
        if (declared == null || declared.isEmpty())
            {
            this.seedAllocation(
                pool,
                this.bookingCapacitySettings.getTotalAvailableCpuCores(),
                this.bookingCapacitySettings.getTotalAvailableCpuMemory(),
                SEEDED_ALLOCATION_START,
                null
                );
            return;
            }
        for (ComputeAllocationSpec allocation : declared)
            {
            this.seedDeclaredAllocation(
                pool,
                allocation
                );
            }
        }

    /**
     * Seed one declared allocation, skipping an entry that cannot be valid.
     *
     */
    private void seedDeclaredAllocation(
        final SimpleComputeResourcePoolEntity pool,
        final ComputeAllocationSpec allocation
        ){
        if (allocation == null)
            {
            log.warn(
                "Ignoring an empty compute allocation entry for pool [{}]",
                pool.getName()
                );
            return;
            }
        Long cores = allocation.getCores();
        Long memory = allocation.getMemory();
        if (cores == null || cores.longValue() <= 0L)
            {
            log.warn(
                "Ignoring a compute allocation with no positive cores for pool [{}]",
                pool.getName()
                );
            return;
            }
        if (memory == null || memory.longValue() <= 0L)
            {
            log.warn(
                "Ignoring a compute allocation with no positive memory for pool [{}]",
                pool.getName()
                );
            return;
            }
        Instant start = allocation.getStart();
        if (start == null)
            {
            start = SEEDED_ALLOCATION_START;
            }
        Instant end = allocation.getEnd();
        if (end != null && !end.isAfter(start))
            {
            log.warn(
                "Ignoring a compute allocation that ends before it starts for pool [{}]",
                pool.getName()
                );
            return;
            }
        this.seedAllocation(
            pool,
            cores.longValue(),
            memory.longValue(),
            start,
            end
            );
        }

    /**
     * Create one allocation and log it.
     *
     */
    private void seedAllocation(
        final SimpleComputeResourcePoolEntity pool,
        final long cores,
        final long memory,
        final Instant start,
        final Instant end
        ){
        SimpleComputeAllocationEntity allocation = this.allocationFactory.create(
            pool,
            cores,
            memory,
            start,
            end
            );
        log.info(
            "Created compute allocation [{}] for pool [{}] with [{}] cores and [{}] GiB",
            allocation.getUuid(),
            pool.getName(),
            cores,
            memory
            );
        }
    }
