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
 *     "interval": "2026-11-18T14:55:00/2026-11-18T17:00:00",
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

package net.ivoa.calycopis.broker.spring.webapp;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

import net.ivoa.calycopis.broker.engine.entities.compute.pool.simple.SimpleComputeResourcePoolEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.simple.SimpleComputeResourcePoolEntityFactory;
import net.ivoa.calycopis.broker.spring.webapp.error.BrokerProblemException;
import net.ivoa.calycopis.broker.spring.webapp.error.ProblemType;

/**
 * Admin endpoints for compute pools.
 *
 * The path carries the resource type as a segment, so a storage pool can sit at
 * /admin/pools/storage without moving what exists here.
 *
 * Pools are off-schema, so the request and response bodies are hand-written
 * records rather than generated beans.
 *
 */
@Slf4j
@RestController
@RequestMapping("/admin/pools/compute")
public class AdminComputePoolsController
    {
    private final SimpleComputeResourcePoolEntityFactory poolFactory;

    public AdminComputePoolsController(
        final SimpleComputeResourcePoolEntityFactory poolFactory
        ){
        this.poolFactory = poolFactory;
        }

    /**
     * Create a compute pool.
     *
     */
    @PostMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
        )
    public ResponseEntity<ComputePoolResponse> create(
        @RequestBody final ComputePoolRequest request
        ){
        String name = request.name();
        if (name == null || name.isBlank())
            {
            throw new BrokerProblemException(
                ProblemType.INVALID_POOL,
                "A compute pool needs a name."
                );
            }
        name = name.trim();
        if (this.poolFactory.findByName(name).isPresent())
            {
            throw new BrokerProblemException(
                ProblemType.POOL_NAME_IN_USE,
                "A compute pool named [" + name + "] already exists."
                );
            }
        SimpleComputeResourcePoolEntity entity = this.poolFactory.create(
            name,
            request.description(),
            0,
            true
            );
        log.info(
            "Created compute pool [{}] [{}]",
            entity.getName(),
            entity.getUuid()
            );
        return ResponseEntity.created(
            URI.create("/admin/pools/compute/" + entity.getUuid())
            ).body(
                response(entity)
                );
        }

    /**
     * List the compute pools.
     *
     */
    @GetMapping(
        produces = MediaType.APPLICATION_JSON_VALUE
        )
    public ResponseEntity<List<ComputePoolResponse>> list()
        {
        List<ComputePoolResponse> responses = new ArrayList<>();
        for (SimpleComputeResourcePoolEntity entity : this.poolFactory.list())
            {
            responses.add(
                response(entity)
                );
            }
        return ResponseEntity.ok(
            responses
            );
        }

    /**
     * Select one compute pool.
     *
     * The identifier arrives as a string so that a malformed one is a 400 rather
     * than a Spring type mismatch, which the generic handler would turn into a
     * 500.
     *
     */
    @GetMapping(
        path = "/{uuid}",
        produces = MediaType.APPLICATION_JSON_VALUE
        )
    public ResponseEntity<ComputePoolResponse> select(
        @PathVariable("uuid") final String uuid
        ){
        UUID identifier = parseUuid(
            uuid
            );
        Optional<SimpleComputeResourcePoolEntity> entity = this.poolFactory.select(
            identifier
            );
        if (entity.isEmpty())
            {
            throw new BrokerProblemException(
                ProblemType.POOL_NOT_FOUND,
                "No compute pool with identifier [" + uuid + "]."
                );
            }
        return ResponseEntity.ok(
            response(
                entity.get()
                )
            );
        }

    /**
     * Change a compute pool's offer settings.
     *
     * The identifier arrives as a string so that a malformed one is a 400 rather
     * than a Spring type mismatch, which the generic handler would turn into a
     * 500.
     *
     */
    @PostMapping(
        path = "/{uuid}",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
        )
    public ResponseEntity<ComputePoolResponse> update(
        @PathVariable("uuid") final String uuid,
        @RequestBody final ComputePoolUpdateRequest request
        ){
        UUID identifier = parseUuid(
            uuid
            );
        Optional<SimpleComputeResourcePoolEntity> selected = this.poolFactory.select(
            identifier
            );
        if (selected.isEmpty())
            {
            throw new BrokerProblemException(
                ProblemType.POOL_NOT_FOUND,
                "No compute pool with identifier [" + uuid + "]."
                );
            }
        if (request.priority() == null && request.enabled() == null)
            {
            throw new BrokerProblemException(
                ProblemType.INVALID_POOL,
                "A compute pool update needs a priority or an enabled flag."
                );
            }
        if (request.priority() != null && request.priority().intValue() < 0)
            {
            throw new BrokerProblemException(
                ProblemType.INVALID_POOL,
                "A compute pool priority cannot be negative."
                );
            }
        SimpleComputeResourcePoolEntity entity = selected.get();
        int priority = entity.getPriority();
        if (request.priority() != null)
            {
            priority = request.priority().intValue();
            }
        boolean enabled = entity.isEnabled();
        if (request.enabled() != null)
            {
            enabled = request.enabled().booleanValue();
            }
        entity = this.poolFactory.update(
            entity,
            priority,
            enabled
            );
        log.info(
            "Updated compute pool [{}] [{}] to priority [{}] and enabled [{}]",
            entity.getName(),
            entity.getUuid(),
            priority,
            enabled
            );
        return ResponseEntity.ok(
            response(
                entity
                )
            );
        }

    /**
     * Parse a UUID, refusing anything else with a 400.
     *
     */
    private static UUID parseUuid(final String uuid)
        {
        try
            {
            return UUID.fromString(
                uuid
                );
            }
        catch (IllegalArgumentException exception)
            {
            throw new BrokerProblemException(
                ProblemType.INVALID_POOL,
                "The compute pool identifier [" + uuid + "] is not a UUID."
                );
            }
        }

    /**
     * Map an entity onto the response body.
     *
     */
    private static ComputePoolResponse response(final SimpleComputeResourcePoolEntity entity)
        {
        return new ComputePoolResponse(
            entity.getUuid(),
            entity.getName(),
            entity.getDescription(),
            entity.getPriority(),
            entity.isEnabled(),
            entity.getCreated()
            );
        }

    }

