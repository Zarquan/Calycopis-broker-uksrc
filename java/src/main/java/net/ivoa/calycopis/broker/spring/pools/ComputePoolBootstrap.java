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
 *     },
 *     {
 *     "interval": "2026-11-18T18:50:00/2026-11-18T20:30:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 10,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.spring.pools;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import net.ivoa.calycopis.broker.engine.entities.compute.allocation.simple.SimpleComputeAllocationEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.allocation.simple.SimpleComputeAllocationEntityFactory;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.AbstractComputeResourcePool;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.policy.SimpleComputePoolPolicyEntityFactory;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.policy.SimpleComputePoolPolicyKind;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.policy.SimpleComputePoolPolicyValidation;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.simple.SimpleComputeResourcePoolEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.simple.SimpleComputeResourcePoolEntityFactory;

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
 * pools. The request path asks every enabled pool, so the default is there to
 * disable rather than delete when a deployment declares its own pools.
 *
 * A pool that declares no allocations gets one open-ended allocation from the
 * bootstrap's built-in fallback. That keeps a deployment that configures
 * nothing working without a separate global capacity setting.
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

    /**
     * The cores in the allocation a pool gets when it declares none.
     *
     */
    public static final long DEFAULT_ALLOCATION_CORES = 256L ;

    /**
     * The memory, in GiB, in the allocation a pool gets when it declares none.
     *
     */
    public static final long DEFAULT_ALLOCATION_MEMORY = 256L ;

    private final ComputePoolsSettings settings;
    private final SimpleComputeResourcePoolEntityFactory poolFactory;
    private final SimpleComputeAllocationEntityFactory allocationFactory;
    private final SimpleComputePoolPolicyEntityFactory policyFactory;

    public ComputePoolBootstrap(
        final ComputePoolsSettings settings,
        final SimpleComputeResourcePoolEntityFactory poolFactory,
        final SimpleComputeAllocationEntityFactory allocationFactory,
        final SimpleComputePoolPolicyEntityFactory policyFactory
        ){
        this.settings = settings;
        this.poolFactory = poolFactory;
        this.allocationFactory = allocationFactory;
        this.policyFactory = policyFactory;
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
     * Create one pool, and its allocations and policies, unless a pool of that
     * name already exists.
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

        boolean policiesValid = checkPolicies(
            name,
            spec
            );

        Optional<SimpleComputeResourcePoolEntity> existing = this.poolFactory.findByName(
            name
            );
        if (existing.isPresent())
            {
            SimpleComputeResourcePoolEntity entity = existing.get();
            if (!policiesValid && entity.isEnabled())
                {
                log.error(
                    "Disabling compute pool [{}] because its configured policy is invalid",
                    name
                    );
                this.poolFactory.update(
                    entity,
                    entity.getPriority(),
                    false
                    );
                }
            else {
                log.info(
                    "Compute pool [{}] already exists",
                    name
                    );
                }
            return;
            }

        int priority = 0;
        if (spec.getPriority() != null)
            {
            if (spec.getPriority().intValue() < 0)
                {
                log.warn(
                    "Ignoring a negative priority for compute pool [{}]",
                    name
                    );
                }
            else {
                priority = spec.getPriority().intValue();
                }
            }

        boolean enabled = true;
        if (spec.getEnabled() != null)
            {
            enabled = spec.getEnabled().booleanValue();
            }
        if (!policiesValid)
            {
            enabled = false;
            }

        SimpleComputeResourcePoolEntity entity = this.poolFactory.create(
            name,
            spec.getDescription(),
            priority,
            enabled
            );
        log.info(
            "Created compute pool [{}] [{}] with priority [{}] and enabled [{}]",
            entity.getName(),
            entity.getUuid(),
            priority,
            enabled
            );
        this.seedAllocations(
            entity,
            spec
            );
        this.seedPolicies(
            entity,
            spec
            );
        }

    /**
     * Check the configured policies before a pool is created or left enabled.
     *
     * An invalid policy makes the whole pool invalid rather than being skipped,
     * so a typo cannot leave a pool permissive by accident.
     *
     */
    private boolean checkPolicies(
        final String poolName,
        final ComputePoolSpec spec
        ){
        List<ComputePolicySpec> declared = spec.getPolicies();
        if (declared == null)
            {
            return true;
            }
        boolean valid = true;
        Set<String> names = new HashSet<String>();
        for (ComputePolicySpec policy : declared)
            {
            if (policy == null)
                {
                log.error(
                    "Invalid empty policy for compute pool [{}]",
                    poolName
                    );
                valid = false;
                continue;
                }
            Optional<String> error = SimpleComputePoolPolicyValidation.validate(
                policy.getKind(),
                policy.getName(),
                policy.getMinimumMemory(),
                policy.getMaximumMemory(),
                policy.getUsername(),
                policy.getStartHour(),
                policy.getEndHour()
                );
            if (error.isPresent())
                {
                log.error(
                    "Invalid policy for compute pool [{}] [{}]",
                    poolName,
                    error.get()
                    );
                valid = false;
                continue;
                }
            String policyName = policy.getName().trim();
            if (!names.add(policyName))
                {
                log.error(
                    "Duplicate policy name [{}] for compute pool [{}]",
                    policyName,
                    poolName
                    );
                valid = false;
                }
            }
        return valid;
        }

    /**
     * Seed the policies a pool declares.
     *
     */
    private void seedPolicies(
        final SimpleComputeResourcePoolEntity pool,
        final ComputePoolSpec spec
        ){
        List<ComputePolicySpec> declared = spec.getPolicies();
        if (declared == null)
            {
            return;
            }
        Set<String> names = new HashSet<String>();
        for (ComputePolicySpec policy : declared)
            {
            if (policy == null)
                {
                continue;
                }
            Optional<String> error = SimpleComputePoolPolicyValidation.validate(
                policy.getKind(),
                policy.getName(),
                policy.getMinimumMemory(),
                policy.getMaximumMemory(),
                policy.getUsername(),
                policy.getStartHour(),
                policy.getEndHour()
                );
            if (error.isPresent())
                {
                continue;
                }
            String policyName = policy.getName().trim();
            if (!names.add(policyName))
                {
                continue;
                }
            try {
                SimpleComputePoolPolicyKind kind = SimpleComputePoolPolicyKind.parse(
                    policy.getKind()
                    );
                this.policyFactory.create(
                    pool,
                    kind,
                    policyName,
                    policy.getMinimumMemory(),
                    policy.getMaximumMemory(),
                    policy.getUsername(),
                    policy.getStartHour(),
                    policy.getEndHour()
                    );
                log.info(
                    "Created compute policy [{}] for pool [{}]",
                    policyName,
                    pool.getName()
                    );
                }
            catch (RuntimeException ouch)
                {
                log.error(
                    "Failed to create policy [{}] for pool [{}] [{}][{}], disabling the pool",
                    policyName,
                    pool.getName(),
                    ouch.getClass().getSimpleName(),
                    ouch.getMessage()
                    );
                this.poolFactory.update(
                    pool,
                    pool.getPriority(),
                    false
                    );
                return;
                }
            }
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
                DEFAULT_ALLOCATION_CORES,
                DEFAULT_ALLOCATION_MEMORY,
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
