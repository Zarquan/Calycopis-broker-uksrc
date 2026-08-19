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
 *     "timestamp": "2026-04-14T17:00:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 5,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-05-26T16:50:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 2,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-06-23T14:03:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 40,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.functional.processing.component;

import jakarta.persistence.Entity;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.component.LifecycleComponentEntity;
import net.ivoa.calycopis.broker.engine.functional.platform.Platform;
import net.ivoa.calycopis.broker.engine.functional.processing.ProcessingAction;
import net.ivoa.calycopis.openapi.spring.model.IvoaLifecyclePhase;

/**
 * 
 */
@Slf4j
@Entity
@Table(
    name = "preparecomponentrequests"
    )
@Inheritance(
    strategy = InheritanceType.JOINED
    )
public class PrepareComponentRequestEntity
extends ComponentProcessingRequestEntity
implements ComponentProcessingRequest
    {

    protected PrepareComponentRequestEntity()
        {
        super();
        }

    protected PrepareComponentRequestEntity(final LifecycleComponentEntity component)
        {
        super(component);
        }

    @Override
    public ProcessingAction preProcess(final Platform platform)
        {
        LifecycleComponentEntity component = this.getComponent(
            platform
            );
        log.debug(
            "PrepareComponentRequest pre-processing component [{}][{}][{}]",
            component.getUuid(),
            component.getClass().getSimpleName(),
            component.getPhase()
            );
        //
        // Check the current phase.
        prevPhase = component.getPhase();
        switch(prevPhase)
            {
            //
            // If the component is INITIALIZING, start the prepare process. 
            case INITIALIZING:
                log.debug(
                    "Phase is [{}], starting the prepare process.",
                    component.getPhase()
                    );
                return component.getPrepareAction(
                    platform,
                    this
                    );
            //
            // If the component is WAITING or PREPARING , continue the prepare process. 
            case WAITING:
            case PREPARING:
                log.debug(
                    "Phase is [{}], continuing the prepare process.",
                    component.getPhase()
                    );
                return component.getPrepareAction(
                    platform,
                    this
                    );
            //
            // If the phase has gone beyond PREPARING, no further action is required.
            case AVAILABLE:
            case RUNNING:
            case RELEASING:
            case COMPLETED:
            case CANCELLED:
            case FAILED:
                log.debug(
                    "Phase is [{}], no action required.",
                    component.getPhase()
                    );
                return ProcessingAction.NO_ACTION;
            //
            // Anything else, shouldn't reach here.
            default:
                log.error(
                    "Unexpected phase [{}] for pre-processing component [{}][{}]",
                    component.getPhase(),
                    component.getUuid(),
                    component.getClass().getSimpleName()
                    );
                this.fail(
                    platform,
                    component
                    );
                return ProcessingAction.NO_ACTION;
            }
        }

    protected void postProcess(final Platform platform, final ComponentProcessingAction action)
        {
        LifecycleComponentEntity component = this.getComponent(
            platform
            );
        log.debug(
            "PrepareComponentRequest post-processing component [{}][{}][{}]",
            component.getUuid(),
            component.getClass().getSimpleName(),
            component.getPhase()
            );
        //
        // Call the action's postProcess() method to update the component in a transaction.
        if (action != null)
            {
            action.postProcess(
                component
                );
            }
        //
        // Update the session if the phase changed between pre- and post-processing. 
        nextPhase = component.getPhase();
        if (prevPhase != nextPhase)
            {
            log.debug(
                "Phase changed from [{}] to [{}], scheduling update session request.",
                prevPhase,
                nextPhase
                );
            platform.getProcessingRequestFactory().getSessionProcessingRequestFactory().createUpdateSessionRequest(
                component.getSession()
                );
            }
        //
        // Check the current phase.
        switch(nextPhase)
            {
            //
            // If the current phase is WAITING, reschedule this request.
            case WAITING:
                log.debug(
                    "Phase is [{}], waiting for wait duration [{}]",
                    component.getPhase(),
                    component.getPrepareWaitDuration()
                    );
                this.activate(
                    component.getPrepareWaitDuration()
                    );
                break;
                
            //
            // If the current phase is PREPARING, reschedule this request.
            case PREPARING:
                log.debug(
                    "Phase is [{}], waiting for loop duration [{}]",
                    component.getPhase(),
                    component.getPrepareLoopDuration()
                    );
                this.activate(
                    component.getPrepareLoopDuration()
                    );
                break;

            //
            // If the phase has changed to AVAILABLE, schedule a monitor component request.
            case AVAILABLE:
            case RUNNING:
                if (prevPhase != nextPhase)
                    {
                    log.debug(
                        "Phase changed from [{}] to [{}], scheduling monitor component request.",
                        prevPhase,
                        nextPhase
                        );
                    platform.getProcessingRequestFactory().getComponentProcessingRequestFactory().createMonitorComponentRequest(
                        component
                        );
                    }
                this.done(platform);
                break;

            //
            // If the phase has gone beyond AVAILABLE, no further action is required.
            case RELEASING:
            case COMPLETED:
            case CANCELLED:
            case FAILED:
                log.debug(
                    "Phase is [{}], no action required.",
                    component.getPhase()
                    );
                this.done(platform);
                break;

            default:
                log.error(
                    "Unexpected phase [{}] for post-processing component [{}][{}]",
                    component.getPhase(),
                    component.getUuid(),
                    component.getClass().getSimpleName()
                    );
                this.fail(
                    platform,
                    component
                    );
                break;
            }
        }
    }
