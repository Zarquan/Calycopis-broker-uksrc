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
 *     "timestamp": "2026-06-03T01:33:00",
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
 *       "value": 30,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.entities.compute;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.component.LifecycleComponentEntity;
import net.ivoa.calycopis.broker.engine.entities.data.AbstractDataResourceEntity;
import net.ivoa.calycopis.broker.engine.entities.executable.AbstractExecutableEntity;
import net.ivoa.calycopis.broker.engine.entities.session.simple.SimpleExecutionSessionEntity;
import net.ivoa.calycopis.broker.engine.entities.storage.AbstractStorageResourceEntity;
import net.ivoa.calycopis.broker.engine.entities.volume.AbstractVolumeMount;
import net.ivoa.calycopis.broker.engine.entities.volume.AbstractVolumeMountEntity;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeResourceOffer;
import net.ivoa.calycopis.broker.engine.util.ListWrapper;
import net.ivoa.calycopis.broker.engine.util.URIBuilder;
import net.ivoa.calycopis.openapi.spring.model.IvoaAbstractComputeResource;
import net.ivoa.calycopis.openapi.spring.model.IvoaLifecyclePhase;

/**
 * 
 */
@Slf4j
@Entity
@Table(
    name = "abstractcomputeresources"
    )
@Inheritance(
    strategy = InheritanceType.JOINED
    )
public abstract class AbstractComputeResourceEntity
extends LifecycleComponentEntity
implements AbstractComputeResource
    {

    /**
     * Protected constructor for JPA entities.
     * 
     */
    protected AbstractComputeResourceEntity()
        {
        super();
        }

    /**
     * Protected constructor used by derived classes.
     * 
     */
    protected AbstractComputeResourceEntity(
        final SimpleExecutionSessionEntity session,
        final AbstractComputeResourceValidator.Result result,
        final SimpleComputeResourceOffer offer
        ){
        super(
            result.getMeta(),
            session.getOwner()
            );
        this.session = session;
        this.session.setComputeResource(
            this
            );
        
        //
        // Start preparing before the offer is available.
        // TODO Add available time and preparation time to the offer.
        this.prepareDurationSeconds     = result.getPrepareDuration();
        this.prepareStartInstantSeconds = offer.getStartInstant().getEpochSecond() - result.getPrepareDuration(); 

        //
        // Available as soon as the preparation is done.
        // TODO Add available time and preparation time to the offer.
        this.availableDurationSeconds      = offer.getDuration().getSeconds();
        this.availableStartDurationSeconds = 0L;
        this.availableStartInstantSeconds  = offer.getStartInstant().getEpochSecond();

        //
        // Hard coded 10s release duration.
        // Start releasing 5s after availability ends.
        this.releaseDurationSeconds = 10L ; 
        this.releaseStartInstantSeconds = this.availableStartInstantSeconds + this.availableDurationSeconds + 5L ;         
                
        }

    @JoinColumn(name = "session", referencedColumnName = "uuid", nullable = false)
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    protected SimpleExecutionSessionEntity session;
    @Override
    public SimpleExecutionSessionEntity getSession()
        {
        return this.session;
        }

    @OneToMany(
        mappedBy = "computeResource",
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL,
        orphanRemoval = true
        )
    List<AbstractVolumeMountEntity> volumeMounts = new ArrayList<AbstractVolumeMountEntity>();
    
    public Iterable<AbstractVolumeMountEntity> getVolumeMountEntities()
        {
        return volumeMounts;
        }

    @Override
    public Iterable<AbstractVolumeMount> getVolumeMounts()
        {
        return new ListWrapper<AbstractVolumeMount, AbstractVolumeMountEntity>(
            this.volumeMounts
            ){
            public AbstractVolumeMount wrap(final AbstractVolumeMountEntity inner)
                {
                return inner;
                }
            };
        }
    
    public void addVolumeMount(final AbstractVolumeMountEntity volume)
        {
        volumeMounts.add(
            volume
            );
        }
    
    @Override
    protected IvoaLifecyclePhase checkPrepareActionRules()
        {
        //
        // A compute resource MUST wait until the executable is AVAILABLE.
        AbstractExecutableEntity executable = this.session.getExecutable();
        if (executable.getPhase().compareTo(IvoaLifecyclePhase.AVAILABLE) < 0)
            {
            log.debug(
                "Compute resource [{}][{}] waiting for executable [{}][{}] to be AVAILABLE",
                this.getUuid(),
                this.getClass().getSimpleName(),
                executable.getUuid(),
                executable.getPhase()
                );
            return IvoaLifecyclePhase.WAITING;
            }
        //
        // If the executable has gone beyond AVAILABLE.
        if (executable.getPhase().compareTo(IvoaLifecyclePhase.AVAILABLE) > 0)
            {
            log.debug(
                "Compute resource [{}][{}] executable [{}][{}] gone beyond AVAILABLE",
                this.getUuid(),
                this.getClass().getSimpleName(),
                executable.getUuid(),
                executable.getPhase()
                );
            return IvoaLifecyclePhase.FAILED;
            }

        //
        // Compute resource MUST wait until ALL data/storage resources linked to its volume mounts are AVAILABLE.
        for (AbstractVolumeMountEntity mount : this.volumeMounts)
            {
            AbstractDataResourceEntity dataResource = mount.getDataResource();
            if (dataResource != null)
                {
                //
                // If the data resource hasn't reached AVAILABLE yet.
                if (dataResource.getPhase().compareTo(IvoaLifecyclePhase.AVAILABLE) < 0)
                    {
                    log.debug(
                        "Compute resource [{}][{}] waiting for data resource [{}][{}] to be AVAILABLE",
                        this.getUuid(),
                        this.getClass().getSimpleName(),
                        dataResource.getUuid(),
                        dataResource.getPhase()
                        );
                    return IvoaLifecyclePhase.WAITING;
                    }
                //
                // If the data resource has gone beyond AVAILABLE.
                if (dataResource.getPhase().compareTo(IvoaLifecyclePhase.AVAILABLE) > 0)
                    {
                    log.debug(
                        "Compute resource [{}][{}] data resource [{}][{}] has gone beyond AVAILABLE",
                        this.getUuid(),
                        this.getClass().getSimpleName(),
                        dataResource.getUuid(),
                        dataResource.getPhase()
                        );
                    return IvoaLifecyclePhase.FAILED;
                    }
                }
            else {
                AbstractStorageResourceEntity storageResource = mount.getStorageResource();
                if (storageResource != null)
                    {
                    //
                    // If the storage resource hasn't reached AVAILABLE yet.
                    if (storageResource.getPhase().compareTo(IvoaLifecyclePhase.AVAILABLE) < 0)
                        {
                        log.debug(
                            "Compute resource [{}][{}] waiting for storage resource [{}][{}] to be AVAILABLE",
                            this.getUuid(),
                            this.getClass().getSimpleName(),
                            storageResource.getUuid(),
                            storageResource.getPhase()
                            );
                        return IvoaLifecyclePhase.WAITING;
                        }
                    //
                    // If the storage resource has gone beyond AVAILABLE.
                    if (storageResource.getPhase().compareTo(IvoaLifecyclePhase.AVAILABLE) > 0)
                        {
                        log.debug(
                            "Compute resource [{}][{}] storage resource [{}][{}] has gone beyond AVAILABLE",
                            this.getUuid(),
                            this.getClass().getSimpleName(),
                            storageResource.getUuid(),
                            storageResource.getPhase()
                            );
                        return IvoaLifecyclePhase.FAILED;
                        }
                    }
                }
            }
        return IvoaLifecyclePhase.PREPARING;
        }

    protected IvoaLifecyclePhase checkReleaseActionRules()
        {
        return IvoaLifecyclePhase.RELEASING;
        }
    
    public abstract IvoaAbstractComputeResource makeBean(final URIBuilder builder);

    protected IvoaAbstractComputeResource fillBean(final IvoaAbstractComputeResource bean)
        {
        bean.setKind(
            this.getKind()
            );
        bean.setPhase(
            this.getPhase()
            );
        bean.setSchedule(
            this.makeScheduleBean()
            );
        bean.setCosts(
            this.getCostBeans()
            );
        bean.setMetrics(
            this.getMetricBeans()
            );
        return bean;
        }

    @Override
    protected URI getWebappPath()
        {
        return AbstractComputeResource.WEBAPP_PATH;
        }
    }
