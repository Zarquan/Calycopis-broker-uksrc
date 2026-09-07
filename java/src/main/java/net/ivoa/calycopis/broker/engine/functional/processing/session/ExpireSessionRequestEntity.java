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
 *     "timestamp": "2026-09-05T11:13:53",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.1.1-rc.2",
 *     "model": "deepseek-v4-flash",
 *     "contribution": {
 *       "value": 100,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-09-05T12:05:01",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.1.1-rc.2",
 *     "model": "deepseek-v4-flash",
 *     "contribution": {
 *       "value": 5,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.functional.processing.session;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.data.AbstractDataResourceEntity;
import net.ivoa.calycopis.broker.engine.entities.session.simple.SimpleExecutionSessionEntity;
import net.ivoa.calycopis.broker.engine.entities.storage.AbstractStorageResourceEntity;
import net.ivoa.calycopis.broker.engine.functional.platform.Platform;
import net.ivoa.calycopis.broker.engine.functional.processing.action.ProcessingAction;
import net.ivoa.calycopis.openapi.spring.model.IvoaSimpleExecutionSessionPhase;

/**
 * A session-level processing request that expires sessions that were
 * never accepted, i.e. sessions that are still in the OFFERED phase
 * when their expiry time is reached.
 *
 * When the request is processed:
 *  - If the session is no longer OFFERED, the request does nothing.
 *  - If the session is still OFFERED and the expiry time has not been
 *    reached, the request is re-scheduled after the configured polling
 *    interval.
 *  - If the session is still OFFERED and the expiry time has been
 *    reached, the session phase is set to EXPIRED and a ReleaseComponent
 *    request is scheduled for all of the session components.
 *
 */
@Slf4j
@Entity
@Table(
    name = "expiresessionrequests"
    )
@Inheritance(
    strategy = InheritanceType.JOINED
    )
public class ExpireSessionRequestEntity
extends SessionProcessingRequestEntity
implements SessionProcessingRequest
    {

    protected ExpireSessionRequestEntity()
        {
        super();
        }

    protected ExpireSessionRequestEntity(final SimpleExecutionSessionEntity session)
        {
        super(
            SessionProcessingRequest.KIND,
            session
            );
        }

    @Override
    public ProcessingAction preProcess(final Platform platform)
        {
        log.debug(
            "Pre-processing [EXPIRE] for session [{}][{}][{}]",
            this.session.getUuid(),
            this.session.getClass().getSimpleName(),
            this.session.getPhase()
            );

        //
        // Check the current session phase.
        if (this.session.getPhase() == IvoaSimpleExecutionSessionPhase.OFFERED)
            {
            Instant expires = this.session.getExpires();
            //
            // If the expiry time has been reached, expire the session.
            if ((expires != null) && (!expires.isAfter(Instant.now())))
                {
                log.debug(
                    "Session [{}][{}] is [OFFERED] and has reached its expiry time, setting phase to [EXPIRED]",
                    this.session.getUuid(),
                    this.session.getClass().getSimpleName()
                    );
                this.session.setPhase(
                    IvoaSimpleExecutionSessionPhase.EXPIRED
                    );
                this.scheduleReleaseComponents(
                    platform
                    );
                }
            else {
                //
                // The expiry time has not been reached yet.
                // The postProcess method will decide whether to re-schedule or finish.
                log.debug(
                    "Session [{}][{}] is [OFFERED] but has not reached its expiry time [{}] yet",
                    this.session.getUuid(),
                    this.session.getClass().getSimpleName(),
                    expires
                    );
                }
            }
        else {
            //
            // The session is no longer OFFERED, nothing to do.
            log.debug(
                "Session [{}][{}] is no longer [OFFERED], nothing to do",
                this.session.getUuid(),
                this.session.getClass().getSimpleName()
                );
            }
        return ProcessingAction.NO_ACTION;
        }

    @Override
    public void postProcess(final Platform platform, final ProcessingAction action)
        {
        log.debug(
            "Post-processing [EXPIRE] for session [{}][{}][{}]",
            this.session.getUuid(),
            this.session.getClass().getSimpleName(),
            this.session.getPhase()
            );

        //
        // If the session is still OFFERED then the expiry time has not
        // been reached yet, re-schedule the request.
        if (this.session.getPhase() == IvoaSimpleExecutionSessionPhase.OFFERED)
            {
            Instant expires = this.session.getExpires();
            if (expires == null)
                {
                //
                // No expiry time, nothing for this request to do.
                log.warn(
                    "Session [{}][{}] has no expiry time, finishing expire request",
                    this.session.getUuid(),
                    this.session.getClass().getSimpleName()
                    );
                this.done(
                    platform
                    );
                }
            else if (expires.isAfter(Instant.now()))
                {
                //
                // The expiry time has not been reached, re-schedule the request.
                long pollingSeconds = platform.getSessionTimingSettings().getExpiredPollingSeconds();
                log.debug(
                    "Session [{}][{}] is still [OFFERED], re-scheduling expire request for [{}]s",
                    this.session.getUuid(),
                    this.session.getClass().getSimpleName(),
                    pollingSeconds
                    );
                this.activate(
                    Duration.ofSeconds(
                        pollingSeconds
                        )
                    );
                }
            else {
                //
                // Defensive branch: the expiry time has been reached but the
                // session is still OFFERED, expire the session now.
                log.debug(
                    "Session [{}][{}] is [OFFERED] and has reached its expiry time, setting phase to [EXPIRED]",
                    this.session.getUuid(),
                    this.session.getClass().getSimpleName()
                    );
                this.session.setPhase(
                    IvoaSimpleExecutionSessionPhase.EXPIRED
                    );
                this.scheduleReleaseComponents(
                    platform
                    );
                this.done(
                    platform
                    );
                }
            }
        else {
            //
            // The session is no longer OFFERED, or has just been set to
            // EXPIRED, nothing more for this request to do.
            this.done(
                platform
                );
            }
        }

    /**
     * Schedule a ReleaseComponent request for all of the session components.
     *
     */
    protected void scheduleReleaseComponents(final Platform platform)
        {
        log.debug(
            "Scheduling [RELEASE] requests for all components of session [{}][{}]",
            this.session.getUuid(),
            this.session.getClass().getSimpleName()
            );

        if (this.session.getExecutable() != null)
            {
            log.debug(
                "Scheduling [RELEASE] request for executable [{}][{}]",
                this.session.getExecutable().getUuid(),
                this.session.getExecutable().getClass().getSimpleName()
                );
            platform.getProcessingRequestFactory().getComponentProcessingRequestFactory().createReleaseComponentRequest(
                this.session.getExecutable()
                );
            }

        if (this.session.getComputeResource() != null)
            {
            log.debug(
                "Scheduling [RELEASE] request for compute resource [{}][{}]",
                this.session.getComputeResource().getUuid(),
                this.session.getComputeResource().getClass().getSimpleName()
                );
            platform.getProcessingRequestFactory().getComponentProcessingRequestFactory().createReleaseComponentRequest(
                this.session.getComputeResource()
                );
            }

        for (AbstractDataResourceEntity dataResource : this.session.getDataResources())
            {
            log.debug(
                "Scheduling [RELEASE] request for data resource [{}][{}]",
                dataResource.getUuid(),
                dataResource.getClass().getSimpleName()
                );
            platform.getProcessingRequestFactory().getComponentProcessingRequestFactory().createReleaseComponentRequest(
                dataResource
                );
            }

        for (AbstractStorageResourceEntity storageResource : this.session.getStorageResources())
            {
            log.debug(
                "Scheduling [RELEASE] request for storage resource [{}][{}]",
                storageResource.getUuid(),
                storageResource.getClass().getSimpleName()
                );
            platform.getProcessingRequestFactory().getComponentProcessingRequestFactory().createReleaseComponentRequest(
                storageResource
                );
            }
        }
    }
