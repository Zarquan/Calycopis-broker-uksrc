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

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import net.ivoa.calycopis.broker.engine.entities.compute.pool.simple.SimpleComputeResourcePoolEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.simple.SimpleComputeResourcePoolEntityFactory;

/**
 * Creates the compute pools a deployment declares, once, at startup.
 *
 * The seed is idempotent: a pool is looked up by name before it is created, so
 * a second run against the same database finds it and leaves it alone.
 *
 * The broker recreates its schema at startup, so in practice the tables are
 * empty when this runs. The lookup still matters for a broker started against a
 * database that was not rebuilt.
 *
 */
@Slf4j
@Component
public class ComputePoolBootstrap
implements ApplicationRunner
    {
    /**
     * The pool every deployment gets when pools.yaml says nothing.
     *
     */
    public static final String DEFAULT_POOL_NAME = "default" ;

    /**
     * Its description, which says what it is rather than what it can do.
     *
     */
    public static final String DEFAULT_POOL_DESCRIPTION = "The default compute pool." ;

    private final ComputePoolsSettings settings;
    private final SimpleComputeResourcePoolEntityFactory factory;

    public ComputePoolBootstrap(
        final ComputePoolsSettings settings,
        final SimpleComputeResourcePoolEntityFactory factory
        ){
        this.settings = settings;
        this.factory = factory;
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
     * The pools to seed: what the configuration declares, or the default pool
     * when it declares nothing.
     *
     */
    static List<ComputePoolSpec> resolve(final List<ComputePoolSpec> configured)
        {
        if (configured != null && !configured.isEmpty())
            {
            return configured;
            }
        List<ComputePoolSpec> defaults = new ArrayList<>();
        defaults.add(
            defaultSpec()
            );
        return defaults;
        }

    /**
     * The default pool, as a specification.
     *
     */
    static ComputePoolSpec defaultSpec()
        {
        ComputePoolSpec spec = new ComputePoolSpec();
        spec.setName(
            DEFAULT_POOL_NAME
            );
        spec.setDescription(
            DEFAULT_POOL_DESCRIPTION
            );
        return spec;
        }

    /**
     * Create one pool unless a pool of that name already exists.
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
        if (this.factory.findByName(name).isPresent())
            {
            log.info(
                "Compute pool [{}] already exists",
                name
                );
            return;
            }
        SimpleComputeResourcePoolEntity entity = this.factory.create(
            name,
            spec.getDescription()
            );
        log.info(
            "Created compute pool [{}] [{}]",
            entity.getName(),
            entity.getUuid()
            );
        }

    }

