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

import java.util.Optional;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

import net.ivoa.calycopis.broker.engine.entities.compute.pool.policy.SimpleComputePoolPolicyEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.pool.policy.SimpleComputePoolPolicyEntityFactory;
import net.ivoa.calycopis.broker.spring.webapp.ComputePolicyRequestValidation.ValidatedPolicy;
import net.ivoa.calycopis.broker.spring.webapp.error.BrokerProblemException;
import net.ivoa.calycopis.broker.spring.webapp.error.ProblemType;

/**
 * Admin endpoints for one compute pool policy.
 *
 */
@Slf4j
@RestController
@RequestMapping("/admin/policies/compute")
public class AdminComputePoliciesController
    {
    private final SimpleComputePoolPolicyEntityFactory policyFactory;

    public AdminComputePoliciesController(
        final SimpleComputePoolPolicyEntityFactory policyFactory
        ){
        this.policyFactory = policyFactory;
        }

    /**
     * Select one policy.
     *
     */
    @GetMapping(
        path = "/{uuid}",
        produces = MediaType.APPLICATION_JSON_VALUE
        )
    public ResponseEntity<ComputePolicyResponse> select(
        @PathVariable("uuid") final String uuid
        ){
        return ResponseEntity.ok(
            ComputePolicyResponse.of(
                selectPolicy(
                    uuid
                    )
                )
            );
        }

    /**
     * Change one policy.
     *
     */
    @PostMapping(
        path = "/{uuid}",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
        )
    public ResponseEntity<ComputePolicyResponse> update(
        @PathVariable("uuid") final String uuid,
        @RequestBody final ComputePolicyRequest request
        ){
        SimpleComputePoolPolicyEntity entity = selectPolicy(
            uuid
            );
        ValidatedPolicy policy = ComputePolicyRequestValidation.validate(
            request
            );
        Optional<SimpleComputePoolPolicyEntity> named = this.policyFactory.findByName(
            entity.getPool(),
            policy.name()
            );
        if (named.isPresent() && !named.get().getUuid().equals(entity.getUuid()))
            {
            throw new BrokerProblemException(
                ProblemType.POLICY_NAME_IN_USE,
                "A policy named [" + policy.name() + "] already exists in this pool."
                );
            }
        entity = this.policyFactory.update(
            entity,
            policy.kind(),
            policy.name(),
            request.minimumMemory(),
            request.maximumMemory(),
            request.username(),
            request.startHour(),
            request.endHour()
            );
        log.info(
            "Updated compute policy [{}] [{}]",
            entity.getName(),
            entity.getUuid()
            );
        return ResponseEntity.ok(
            ComputePolicyResponse.of(
                entity
                )
            );
        }

    /**
     * Delete one policy.
     *
     */
    @DeleteMapping(
        path = "/{uuid}"
        )
    public ResponseEntity<Void> delete(
        @PathVariable("uuid") final String uuid
        ){
        SimpleComputePoolPolicyEntity entity = selectPolicy(
            uuid
            );
        this.policyFactory.delete(
            entity
            );
        log.info(
            "Deleted compute policy [{}] [{}]",
            entity.getName(),
            entity.getUuid()
            );
        return ResponseEntity.noContent().build();
        }

    /**
     * Find a policy, or refuse the request with a 404.
     *
     */
    private SimpleComputePoolPolicyEntity selectPolicy(final String uuid)
        {
        UUID identifier = parseUuid(
            uuid
            );
        Optional<SimpleComputePoolPolicyEntity> selected = this.policyFactory.select(
            identifier
            );
        if (selected.isEmpty())
            {
            throw new BrokerProblemException(
                ProblemType.POLICY_NOT_FOUND,
                "No compute policy with identifier [" + uuid + "]."
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
                ProblemType.INVALID_POLICY,
                "The compute policy identifier [" + uuid + "] is not a UUID."
                );
            }
        }
    }
