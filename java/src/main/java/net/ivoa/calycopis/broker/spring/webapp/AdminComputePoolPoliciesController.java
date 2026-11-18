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

import net.ivoa.calycopis.broker.engine.entities.compute.pool.policy.SimpleComputePoolPolicyEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.policy.SimpleComputePoolPolicyEntityFactory;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.simple.SimpleComputeResourcePoolEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.simple.SimpleComputeResourcePoolEntityFactory;
import net.ivoa.calycopis.broker.spring.webapp.ComputePolicyRequestValidation.ValidatedPolicy;
import net.ivoa.calycopis.broker.spring.webapp.error.BrokerProblemException;
import net.ivoa.calycopis.broker.spring.webapp.error.ProblemType;

/**
 * Admin endpoints for the policies of one compute pool.
 *
 * The collection path carries the pool identifier, and an individual policy is
 * addressed by its own UUID under /admin/policies/compute, so no path carries
 * two identifiers.
 *
 */
@Slf4j
@RestController
@RequestMapping("/admin/pools/compute")
public class AdminComputePoolPoliciesController
    {
    private final SimpleComputeResourcePoolEntityFactory poolFactory;
    private final SimpleComputePoolPolicyEntityFactory policyFactory;

    public AdminComputePoolPoliciesController(
        final SimpleComputeResourcePoolEntityFactory poolFactory,
        final SimpleComputePoolPolicyEntityFactory policyFactory
        ){
        this.poolFactory = poolFactory;
        this.policyFactory = policyFactory;
        }

    /**
     * List the policies of one pool.
     *
     */
    @GetMapping(
        path = "/{uuid}/policies",
        produces = MediaType.APPLICATION_JSON_VALUE
        )
    public ResponseEntity<List<ComputePolicyResponse>> list(
        @PathVariable("uuid") final String uuid
        ){
        SimpleComputeResourcePoolEntity pool = selectPool(
            uuid
            );
        List<ComputePolicyResponse> responses = new ArrayList<ComputePolicyResponse>();
        for (SimpleComputePoolPolicyEntity entity : this.policyFactory.list(pool))
            {
            responses.add(
                ComputePolicyResponse.of(
                    entity
                    )
                );
            }
        return ResponseEntity.ok(
            responses
            );
        }

    /**
     * Add a policy to one pool.
     *
     */
    @PostMapping(
        path = "/{uuid}/policies",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
        )
    public ResponseEntity<ComputePolicyResponse> create(
        @PathVariable("uuid") final String uuid,
        @RequestBody final ComputePolicyRequest request
        ){
        SimpleComputeResourcePoolEntity pool = selectPool(
            uuid
            );
        ValidatedPolicy policy = ComputePolicyRequestValidation.validate(
            request
            );
        if (this.policyFactory.findByName(pool, policy.name()).isPresent())
            {
            throw new BrokerProblemException(
                ProblemType.POLICY_NAME_IN_USE,
                "A policy named [" + policy.name() + "] already exists in this pool."
                );
            }
        SimpleComputePoolPolicyEntity entity = this.policyFactory.create(
            pool,
            policy.kind(),
            policy.name(),
            request.minimumMemory(),
            request.maximumMemory(),
            request.username(),
            request.startHour(),
            request.endHour()
            );
        log.info(
            "Created compute policy [{}] [{}] for pool [{}]",
            entity.getName(),
            entity.getUuid(),
            pool.getName()
            );
        return ResponseEntity.created(
            URI.create("/admin/policies/compute/" + entity.getUuid())
            ).body(
                ComputePolicyResponse.of(
                    entity
                    )
                );
        }

    /**
     * Find a pool, or refuse the request with a 404.
     *
     */
    private SimpleComputeResourcePoolEntity selectPool(final String uuid)
        {
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
        return selected.get();
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
    }
