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
 *     "interval": "2026-11-17T15:55:00/2026-11-17T18:30:00",
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

import java.time.Instant;
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

import net.ivoa.calycopis.broker.engine.entities.compute.allocation.simple.SimpleComputeAllocationEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.allocation.simple.SimpleComputeAllocationEntityFactory;
import net.ivoa.calycopis.broker.engine.entities.compute.simple.SimpleComputeReservation;
import net.ivoa.calycopis.broker.engine.functional.booking.BookingTransactionHandler;
import net.ivoa.calycopis.broker.spring.webapp.error.BrokerProblemException;
import net.ivoa.calycopis.broker.spring.webapp.error.ProblemType;

/**
 * Admin endpoints for one compute allocation.
 *
 * An allocation is addressed by its own identifier, so these paths carry one
 * UUID. Its pool is in the response rather than in the path.
 *
 * An update or a delete changes the pool's supply, which is check-then-act
 * against the live reservations. Both run inside the booking transaction, so
 * they take the same advisory lock as a booking and cannot commit a change
 * that a concurrent request would invalidate.
 *
 */
@Slf4j
@RestController
@RequestMapping("/admin/allocations/compute")
public class AdminComputeAllocationsController
    {
    private final SimpleComputeAllocationEntityFactory allocationFactory;

    private final BookingTransactionHandler bookingTransactionHandler;

    public AdminComputeAllocationsController(
        final SimpleComputeAllocationEntityFactory allocationFactory,
        final BookingTransactionHandler bookingTransactionHandler
        ){
        this.allocationFactory = allocationFactory;
        this.bookingTransactionHandler = bookingTransactionHandler;
        }

    /**
     * Select one allocation.
     *
     */
    @GetMapping(
        path = "/{uuid}",
        produces = MediaType.APPLICATION_JSON_VALUE
        )
    public ResponseEntity<ComputeAllocationResponse> select(
        @PathVariable("uuid") final String uuid
        ){
        return ResponseEntity.ok(
            ComputeAllocationResponse.of(
                find(
                    uuid
                    )
                )
            );
        }

    /**
     * Update the sizes and the window of one allocation.
     *
     * A change that would leave the pool with less capacity than its live
     * reservations in any block is rejected, and the allocation is left as it
     * was.
     *
     */
    @PostMapping(
        path = "/{uuid}",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
        )
    public ResponseEntity<ComputeAllocationResponse> update(
        @PathVariable("uuid") final String uuid,
        @RequestBody final ComputeAllocationRequest request
        ){
        UUID identifier = parseAllocationUuid(
            uuid
            );
        long cores = ComputeAllocationValidation.cores(
            request.cores()
            );
        long memory = ComputeAllocationValidation.memory(
            request.memory()
            );
        Instant start = ComputeAllocationValidation.start(
            request.start()
            );
        Instant end = ComputeAllocationValidation.end(
            request.end(),
            start
            );
        SimpleComputeAllocationEntity updated = this.bookingTransactionHandler.book(
            SimpleComputeReservation.KIND_DISCRIMINATOR.toString(),
            () -> {
                SimpleComputeAllocationEntity entity = find(
                    identifier
                    );
                if (this.allocationFactory.wouldOverCommit(
                    entity.getPool().getUuid(),
                    entity.getUuid(),
                    cores,
                    memory,
                    start,
                    end
                    ))
                    {
                    throw new BrokerProblemException(
                        ProblemType.ALLOCATION_OVERCOMMITTED,
                        "Updating compute allocation [" + uuid + "] would over-commit its pool."
                        );
                    }
                return this.allocationFactory.update(
                    entity,
                    cores,
                    memory,
                    start,
                    end
                    );
                }
            );
        log.info(
            "Updated compute allocation [{}]",
            updated.getUuid()
            );
        return ResponseEntity.ok(
            ComputeAllocationResponse.of(
                updated
                )
            );
        }

    /**
     * Delete one allocation.
     *
     * A removal that would leave the pool with less capacity than its live
     * reservations in any block is rejected, and the allocation remains.
     *
     */
    @DeleteMapping(
        path = "/{uuid}"
        )
    public ResponseEntity<Void> delete(
        @PathVariable("uuid") final String uuid
        ){
        UUID identifier = parseAllocationUuid(
            uuid
            );
        this.bookingTransactionHandler.book(
            SimpleComputeReservation.KIND_DISCRIMINATOR.toString(),
            () -> {
                SimpleComputeAllocationEntity entity = find(
                    identifier
                    );
                if (this.allocationFactory.wouldOverCommit(
                    entity.getPool().getUuid(),
                    entity.getUuid(),
                    0L,
                    0L,
                    Instant.EPOCH,
                    null
                    ))
                    {
                    throw new BrokerProblemException(
                        ProblemType.ALLOCATION_OVERCOMMITTED,
                        "Deleting compute allocation [" + uuid + "] would over-commit its pool."
                        );
                    }
                this.allocationFactory.delete(
                    entity
                    );
                return null;
                }
            );
        log.info(
            "Deleted compute allocation [{}]",
            uuid
            );
        return ResponseEntity.noContent().build();
        }

    /**
     * Resolve the allocation named in the path.
     *
     */
    private SimpleComputeAllocationEntity find(final String uuid)
        {
        return find(
            parseAllocationUuid(
                uuid
                )
            );
        }

    /**
     * Resolve the allocation named by an identifier.
     *
     */
    private SimpleComputeAllocationEntity find(final UUID identifier)
        {
        Optional<SimpleComputeAllocationEntity> entity = this.allocationFactory.select(
            identifier
            );
        if (entity.isEmpty())
            {
            throw new BrokerProblemException(
                ProblemType.ALLOCATION_NOT_FOUND,
                "No compute allocation with identifier [" + identifier + "]."
                );
            }
        return entity.get();
        }

    /**
     * Parse an allocation UUID, refusing anything else with a 400.
     *
     */
    private static UUID parseAllocationUuid(final String uuid)
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
                ProblemType.INVALID_ALLOCATION,
                "The compute allocation identifier [" + uuid + "] is not a UUID."
                );
            }
        }

    }
