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
 *     "timestamp": "2026-06-23T14:03:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 25,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.entities.component;

import java.time.Duration;
import java.time.Instant;

import org.threeten.extra.Interval;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.identity.IdentityEntity;
import net.ivoa.calycopis.broker.engine.entities.session.simple.SimpleExecutionSessionEntity;
import net.ivoa.calycopis.broker.engine.functional.platform.Platform;
import net.ivoa.calycopis.broker.engine.functional.processing.action.ProcessingAction;
import net.ivoa.calycopis.broker.engine.functional.processing.component.ComponentProcessingRequest;
import net.ivoa.calycopis.schema.spring.model.IvoaComponentMetadata;
import net.ivoa.calycopis.schema.spring.model.IvoaLifecyclePhase;
import net.ivoa.calycopis.schema.spring.model.IvoaLifecycleSchedule;
import net.ivoa.calycopis.schema.spring.model.IvoaLifecycleStartDurationInstant;
import net.ivoa.calycopis.schema.spring.model.IvoaLifecycleStartDurationInterval;

/**
 * 
 */
@Slf4j
@Entity
@Table(name = "lifecyclecomponents")
@Inheritance(
    strategy = InheritanceType.JOINED
    )
public abstract class LifecycleComponentEntity
extends ComponentEntity
implements LifecycleComponent
    {
    /**
     * Protected constructor for JPA entities.
     * 
     */
    protected LifecycleComponentEntity()
        {
        super();
        }

    /**
     * Protected constructor used by our Factories.
     * 
     */
    protected LifecycleComponentEntity(
        final IvoaComponentMetadata meta,
        final IdentityEntity owner
        ){
        this(
            null,
            meta,
            owner
            );
        }
    
    /**
     * Protected constructor used by our Factories.
     * 
     */
    protected LifecycleComponentEntity(
        final IvoaLifecycleSchedule schedule,
        final IvoaComponentMetadata meta,
        final IdentityEntity owner
        ){
        super(
            meta,
            owner
            );
        if (schedule != null)
            {
            IvoaLifecycleStartDurationInstant preparing = schedule.getPreparing();
            if (null != preparing)
                {
                String startInstantString = preparing.getStart();
                if (null != startInstantString)
                    {
                    try {
                        this.prepareStartInstantSeconds = Instant.parse(
                            startInstantString
                            ).getEpochSecond();
                        }
                    catch (Exception ouch)
                        {
                        log.warn("Exception parsing prepare start instant [{}][{}]", startInstantString, ouch.getMessage());
                        }
                    }
                String durationString = preparing.getDuration();
                if (null != durationString)
                    {
                    try {
                        this.prepareDurationSeconds = Duration.parse(
                            durationString
                            ).getSeconds();
                        }
                    catch (Exception ouch)
                        {
                        log.warn("Exception parsing prepare duration [{}][{}]", durationString, ouch.getMessage());
                        }
                    }
                }
            }
        }

    /**
     * Get the parent Session.  
     *
     */
    public abstract SimpleExecutionSessionEntity getSession();
    
    @Column(name = "phase")
    @Enumerated(EnumType.STRING)
    private IvoaLifecyclePhase phase = IvoaLifecyclePhase.INITIALIZING;
    @Override
    public IvoaLifecyclePhase getPhase()
        {
        return this.phase;
        }
    @Override
    public void setPhase(final IvoaLifecyclePhase newphase)
        {
        // TODO This is where we need to have the phase transition checking.
        this.phase = newphase;
        }
    
    @Column(name = "prepare_start_instant_seconds")
    protected long prepareStartInstantSeconds;
    @Override
    public long getPrepareStartInstantSeconds()
        {
        return this.prepareStartInstantSeconds;
        }
    @Override
    public Instant getPrepareStartInstant()
        {
        return Instant.ofEpochSecond(
            prepareStartInstantSeconds
            );
        }
    
    @Column(name = "prepare_duration_seconds")
    protected long prepareDurationSeconds;
    @Override
    public long getPrepareDurationSeconds()
        {
        return this.prepareDurationSeconds;
        }
    @Override
    public Duration getPrepareDuration()
        {
        return Duration.ofSeconds(
            prepareDurationSeconds
            );
        }

    @Column(name = "available_start_instant_seconds")
    protected long availableStartInstantSeconds;
    @Override
    public long getAvailableStartInstantSeconds()
        {
        return this.availableStartInstantSeconds;
        }
    @Override
    public Instant getAvailableStartInstant()
        {
        return Instant.ofEpochSecond(
            availableStartInstantSeconds
            );
        }
    @Column(name = "available_start_duration_seconds")
    protected long availableStartDurationSeconds;
    @Override
    public long getAvailableStartDurationSeconds()
        {
        return this.availableStartDurationSeconds;
        }
    @Override
    public Duration getAvailableStartDuration()
        {
        return Duration.ofSeconds(
            availableStartDurationSeconds
            );
        }
    @Override
    public Interval getAvailableStartInterval()
        {
        return Interval.of(
            getAvailableStartInstant(),
            getAvailableStartDuration()
            );
        }
    
    @Column(name = "available_duration_seconds")
    protected long availableDurationSeconds;
    @Override
    public long getAvailableDurationSeconds()
        {
        return this.availableDurationSeconds;
        }
    @Override
    public Duration getAvailableDuration()
        {
        return Duration.ofSeconds(
            availableDurationSeconds
            );
        }
    
    @Column(name = "release_start_instant_seconds")
    protected long releaseStartInstantSeconds;
    @Override
    public long getReleaseStartInstantSeconds()
        {
        return this.releaseStartInstantSeconds;
        }
    @Override
    public Instant getReleaseStartInstant()
        {
        return Instant.ofEpochSecond(
            releaseStartInstantSeconds
            );
        }
    
    @Column(name = "release_duration_seconds")
    protected long releaseDurationSeconds;
    @Override
    public long getReleaseDurationSeconds()
        {
        return this.releaseDurationSeconds;
        }
    @Override
    public Duration getReleaseDuration()
        {
        return Duration.ofSeconds(
            releaseDurationSeconds
            );
        }

    public IvoaLifecycleStartDurationInstant makePreparingBean()
        {
        boolean valid = false;
        IvoaLifecycleStartDurationInstant bean = new IvoaLifecycleStartDurationInstant(); 
        if (getPrepareStartInstantSeconds() > 0)
            {
            bean.setStart(
                getPrepareStartInstant().toString()
                );
            valid = true;
            }
        if (getPrepareDurationSeconds() > 0)
            {
            bean.setDuration(
                getPrepareDuration().toString()
                );
            valid = true;
            }
        if (valid)
            {
            return bean;
            }
        else {
            return null ;
            }
        }

    public IvoaLifecycleStartDurationInterval makeAvailableBean()
        {
        boolean valid = false;
        IvoaLifecycleStartDurationInterval bean = new IvoaLifecycleStartDurationInterval();
        if (getAvailableStartInstantSeconds() > 0)
            {
            StringBuffer buffer = new StringBuffer();
            buffer.append(
                getAvailableStartInstant().toString()
                );
            buffer.append(
                "/"
                );
            buffer.append(
                getAvailableStartDuration().toString()
                );
            bean.setStart(
                buffer.toString()
                );
            valid = true ;
            }
        if (getAvailableDurationSeconds() > 0)
            {
            bean.setDuration(
                getAvailableDuration().toString()
                );
            valid = true ;
            }
        if (valid)
            {
            return bean;
            }
        else {
            return null ;
            }
        }

    public IvoaLifecycleStartDurationInstant makeReleasingBean()
        {
        boolean valid = false;
        IvoaLifecycleStartDurationInstant bean = new IvoaLifecycleStartDurationInstant(); 
        if (getReleaseStartInstantSeconds() > 0)
            {
            bean.setStart(
                getReleaseStartInstant().toString()
                );
            valid = true;
            }
        if (getReleaseDurationSeconds() > 0)
            {
            bean.setDuration(
                getReleaseDuration().toString()
                );
            valid = true;
            }
        if (valid)
            {
            return bean;
            }
        else {
            return null ;
            }
        }
    
    public IvoaLifecycleSchedule makeScheduleBean()
        {
        boolean valid = false;
        IvoaLifecycleSchedule bean = new IvoaLifecycleSchedule(); 

        IvoaLifecycleStartDurationInstant preparing = this.makePreparingBean();
        if (null != preparing)
            {
            bean.setPreparing(
                preparing
                );
            valid = true;
            }

        IvoaLifecycleStartDurationInterval available = this.makeAvailableBean();
        if (null != available)
            {
            bean.setAvailable(
                available
                );
            valid = true;
            }

        IvoaLifecycleStartDurationInstant releasing = this.makeReleasingBean(); 
        if (releasing != null)
            {
            bean.setReleasing(
                this.makeReleasingBean()
                );
            valid = true ;
            }
        if (valid)
            {
            return bean;
            }
        else {
            return null ;
            }
        }
    
    public static final Duration DEFAULT_PREPARE_RULES_INTERVAL = Duration.ofSeconds(2);
    public static final Duration DEFAULT_PREPARE_LOOP_INTERVAL  = Duration.ofSeconds(5);

    /**
     * Check the rules for this component to determine whether
     * it is allowed to transition to PREPARING.
     *
     * @return IvoaLifecyclePhase.PREPARING if the transition is allowed,
     *         IvoaLifecyclePhase.WAITING if dependencies are not yet met,
     *         IvoaLifecyclePhase.FAILED if the transition is invalid.
     */
    protected IvoaLifecyclePhase checkPrepareActionRules()
        {
        return IvoaLifecyclePhase.PREPARING;
        }

    /**
     * Check the timing values to determine whether this component should
     * wait before transitioning to PREPARING.
     *
     * @return IvoaLifecyclePhase.PREPARING if the timing allows the transition,
     *         IvoaLifecyclePhase.WAITING if the prepare start instant is in the future.
     */
    protected IvoaLifecyclePhase checkPrepareActionTiming()
        {
        if ((this.getPrepareStartInstantSeconds() > 0) && (this.getPrepareStartInstant().isAfter(Instant.now())))
            {
            log.debug(
                "Component [{}][{}] prepare start is in the future [{}]",
                this.getUuid(),
                this.getClass().getSimpleName(),
                this.getPrepareStartInstant()
                );
            return IvoaLifecyclePhase.WAITING;
            }
        else {
            return IvoaLifecyclePhase.PREPARING;
            }
        }

    /**
     * Create a platform-specific ProcessingAction to prepare this component.
     * 
     */
    protected abstract ProcessingAction makePrepareAction(final Platform platform);

    /**
     * Calculate the duration to wait before re-checking when the component is waiting to start PREPARING.
     * 
     */
    public Duration getPrepareWaitDuration()
        {
        if ((this.getPrepareStartInstantSeconds() > 0) && (this.getPrepareStartInstant().isAfter(Instant.now())))
            {
            return Duration.between(
                Instant.now(),
                this.getPrepareStartInstant()
                ).dividedBy(2L);
            }
        return DEFAULT_PREPARE_RULES_INTERVAL;
        }

    /**
     * Calculate the duration to wait before re-checking when the component is in PREPARING state.
     *
     */
    public Duration getPrepareLoopDuration()
        {
        return DEFAULT_PREPARE_LOOP_INTERVAL;
        }

    @Override
    public ProcessingAction getPrepareAction(final Platform platform, final ComponentProcessingRequest request)
        {
        //
        // Check the current phase.
        switch(this.getPhase())
            {
            //
            // If the component is in INITIALIZING or WAITING, check the rules and timings.
            case INITIALIZING:
            case WAITING:
                //
                // Check the prepare rules first.
                switch(this.checkPrepareActionRules())
                    {
                    //
                    // If the prepare rules are not satisfied, stay where we are.
                    case WAITING:
                        log.debug(
                            "Component [{}][{}] prepare rules not yet satisfied, staying at [{}]",
                            this.getUuid(),
                            this.getClass().getSimpleName(),
                            IvoaLifecyclePhase.WAITING
                            );
                        this.setPhase(
                            IvoaLifecyclePhase.WAITING
                            );
                        return ProcessingAction.NO_ACTION;
                    //
                    // If prepare rules are satisfied, check the prepare timings.
                    case PREPARING:
                        switch(this.checkPrepareActionTiming())
                            {
                            //
                            // If the prepare timings are not satisfied, stay in where we are.
                            case WAITING:
                                log.debug(
                                    "Component [{}][{}] prepare timings not yet satisfied, staying at [{}]",
                                    this.getUuid(),
                                    this.getClass().getSimpleName(),
                                    IvoaLifecyclePhase.WAITING
                                    );
                                this.setPhase(
                                    IvoaLifecyclePhase.WAITING
                                    );
                                return ProcessingAction.NO_ACTION;
                            //
                            // If both the prepare rules and timings are satisfied, move to PREPARING.
                            case PREPARING:
                                log.debug(
                                    "Component [{}][{}] prepare rules and timings are satisfied, moving to [{}]",
                                    this.getUuid(),
                                    this.getClass().getSimpleName(),
                                    IvoaLifecyclePhase.PREPARING
                                    );
                                this.setPhase(
                                    IvoaLifecyclePhase.PREPARING
                                    );
                                return this.makePrepareAction(platform);
                            default:
                                log.error(
                                    "Unexpected result [{}] from checkPrepareActionTiming() for component [{}][{}]",
                                    this.checkPrepareActionRules(),
                                    this.getUuid(),
                                    this.getClass().getSimpleName()
                                    );
                                this.setPhase(
                                    IvoaLifecyclePhase.FAILED
                                    );
                                return ProcessingAction.NO_ACTION;
                            }
                    default:
                        log.error(
                            "Unexpected result [{}] from checkPrepareActionRules() for component [{}][{}]",
                            this.checkPrepareActionRules(),
                            this.getUuid(),
                            this.getClass().getSimpleName()
                            );
                        this.setPhase(
                            IvoaLifecyclePhase.FAILED
                            );
                        return ProcessingAction.NO_ACTION;
                    }

            //
            // If the component is already PREPARING.
            case PREPARING:
                return this.makePrepareAction(
                    platform
                    );

            //
            // If the component is already beyond PREPARING, no action required.
            case AVAILABLE:
            case RUNNING:
            case RELEASING:
            case COMPLETED:
            case CANCELLED:
            case FAILED:
                return ProcessingAction.NO_ACTION;

            default:
                log.error(
                    "Unexpected phase [{}] for component [{}][{}]",
                    this.getPhase(),
                    this.getUuid(),
                    this.getClass().getSimpleName()
                    );
                this.setPhase(IvoaLifecyclePhase.FAILED);
                return ProcessingAction.NO_ACTION;
            }
        }

    public static final Duration DEFAULT_MONITOR_LOOP_INTERVAL  = Duration.ofSeconds(5);
    
    /**
     * Create a platform-specific ProcessingAction to monitor this component.
     * Subclasses should override this to provide a type-specific ProcessingAction.
     * 
     */
    protected abstract ProcessingAction makeMonitorAction(final Platform platform);

    /**
     * Calculate the duration to wait between monitor loop iterations.
     *
     */
    public Duration getMonitorLoopDuration()
        {
        return DEFAULT_MONITOR_LOOP_INTERVAL;
        }

    @Override
    public ProcessingAction getMonitorAction(final Platform platform, final ComponentProcessingRequest request)
        {
        //
        // Check the current phase.
        switch(this.getPhase())
            {
            //
            // If the component is AVAILABLE or RUNNING.
            case AVAILABLE:
            case RUNNING:
                return this.makeMonitorAction(
                    platform
                    );

            //
            // If the component is already beyond AVAILABLE, no action required.
            case RELEASING:
            case COMPLETED:
            case CANCELLED:
            case FAILED:
                return ProcessingAction.NO_ACTION;

            default:
                log.error(
                    "Unexpected phase [{}] for component [{}][{}]",
                    this.getPhase(),
                    this.getUuid(),
                    this.getClass().getSimpleName()
                    );
                this.setPhase(IvoaLifecyclePhase.FAILED);
                return ProcessingAction.NO_ACTION;
            }
        }

    
    public static final Duration DEFAULT_RELEASE_RULES_INTERVAL = Duration.ofSeconds(2);
    public static final Duration DEFAULT_RELEASE_LOOP_INTERVAL  = Duration.ofSeconds(5);

    /**
     * Check the rules for this component to determine whether
     * it is allowed to transition to RELEASING.
     *
     * @return IvoaLifecyclePhase.RELEASING if the transition is allowed,
     *         IvoaLifecyclePhase.AVAILABLE if the transition is not allowed,
     *         IvoaLifecyclePhase.FAILED if the transition is invalid.
     *
     */
    protected IvoaLifecyclePhase checkReleaseActionRules()
        {
        return IvoaLifecyclePhase.RELEASING;
        }

    /**
     * Check the timing values to determine whether this component should
     * wait before transitioning to RELEASING.
     *
     * @return IvoaLifecyclePhase.RELEASING if the timing allows the transition,
     *         IvoaLifecyclePhase.AVAILABLE if the transition is not allowed,
     *
     */
    protected IvoaLifecyclePhase checkReleaseActionTiming()
        {
        return IvoaLifecyclePhase.RELEASING;
        }

    /**
     * Create a platform-specific ProcessingAction to release this component.
     * 
     */
    protected abstract ProcessingAction makeReleaseAction(final Platform platform);

    /**
     * Calculate the duration to wait before re-checking when the component is waiting to start RELEASING.
     * 
     */
    public Duration getReleaseWaitDuration()
        {
        return DEFAULT_RELEASE_RULES_INTERVAL;
        }

    /**
     * Calculate the duration to wait before re-checking when the component is in AVAILABLE state.
     *
     */
    public Duration getReleaseLoopDuration()
        {
        return DEFAULT_RELEASE_LOOP_INTERVAL;
        }

    @Override
    public ProcessingAction getReleaseAction(final Platform platform, final ComponentProcessingRequest request)
        {
        //
        // Check the current phase.
        switch(this.getPhase())
            {
            //
            // If the component is AVAILABLE or RUNNING, check the rules and timing.
            case AVAILABLE:
            case RUNNING:
                //
                // Check the release rules first.
                switch(this.checkReleaseActionRules())
                    {
                    //
                    // If the release rules are not satisfied, stay where we are.
                    case AVAILABLE:
                    case RUNNING:
                        log.debug(
                            "Component [{}][{}] release rules are not satisfied, staying at [{}]",
                            this.getUuid(),
                            this.getClass().getSimpleName(),
                            this.getPhase()
                            );
                        return ProcessingAction.NO_ACTION;
                    //
                    // If the release rules are satisfied, check the release timing.
                    case RELEASING:
                        switch(this.checkReleaseActionTiming())
                            {
                            //
                            // If the release timing is not satisfied, stay where we are.
                            case AVAILABLE:
                            case RUNNING:
                                log.debug(
                                    "Component [{}][{}] release timings are not satisfied, staying at [{}]",
                                    this.getUuid(),
                                    this.getClass().getSimpleName(),
                                    this.getPhase()
                                    );
                                return ProcessingAction.NO_ACTION;
                            //
                            // If both the release rules and timings are satisfied, move to RELEASING.
                            case RELEASING:
                                log.debug(
                                    "Component [{}][{}] release rules and timings are satisfied, moving to [RELEASING]",
                                    this.getUuid(),
                                    this.getClass().getSimpleName()
                                    );
                                this.setPhase(
                                    IvoaLifecyclePhase.RELEASING
                                    );
                                return this.makeReleaseAction(
                                    platform
                                    );
                            default:
                                log.error(
                                    "Unexpected result [{}] from checkPrepareActionTiming() for component [{}][{}]",
                                    this.checkPrepareActionRules(),
                                    this.getUuid(),
                                    this.getClass().getSimpleName()
                                    );
                                this.setPhase(
                                    IvoaLifecyclePhase.FAILED
                                    );
                                return ProcessingAction.NO_ACTION;
                            }
                    default:
                        log.error(
                            "Unexpected result [{}] from checkPrepareActionRules() for component [{}][{}]",
                            this.checkPrepareActionRules(),
                            this.getUuid(),
                            this.getClass().getSimpleName()
                            );
                        this.setPhase(
                            IvoaLifecyclePhase.FAILED
                            );
                        return ProcessingAction.NO_ACTION;
                    }

            //
            // If the component is already RELEASING.
            case RELEASING:
                return this.makeReleaseAction(
                    platform
                    );

            //
            // If the component is already beyond RELEASING, no action required.
            case COMPLETED:
            case CANCELLED:
            case FAILED:
                return ProcessingAction.NO_ACTION;

            default:
                log.error(
                    "Unexpected phase [{}] for component [{}][{}]",
                    this.getPhase(),
                    this.getUuid(),
                    this.getClass().getSimpleName()
                    );
                this.setPhase(IvoaLifecyclePhase.FAILED);
                return ProcessingAction.NO_ACTION;
            }
        }
    
    @Override
    public ProcessingAction getCancelAction(final Platform platform, final ComponentProcessingRequest request)
        {
        return ProcessingAction.NO_ACTION;
        }

    @Override
    public ProcessingAction getFailAction(final Platform platform, final ComponentProcessingRequest request)
        {
        return ProcessingAction.NO_ACTION;
        }
    }
