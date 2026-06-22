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
 *     "timestamp": "2026-02-17T07:10:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 3,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-02-17T13:20:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 3,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-03-25T14:45:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 10,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */
package net.ivoa.calycopis.broker.engine.entities.compute.simple;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.compute.AbstractComputeResourceEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.AbstractComputeResourceValidatorImpl;
import net.ivoa.calycopis.broker.engine.entities.offerset.OfferSetRequestParserContext;
import net.ivoa.calycopis.broker.engine.entities.session.simple.SimpleExecutionSessionEntity;
import net.ivoa.calycopis.broker.engine.entities.volume.AbstractVolumeMountValidator;
import net.ivoa.calycopis.broker.engine.entities.volume.AbstractVolumeMountValidatorFactory;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeResourceOffer;
import net.ivoa.calycopis.broker.engine.functional.validator.Validator;
import net.ivoa.calycopis.broker.engine.functional.validator.ValidatorTools;
import net.ivoa.calycopis.schema.spring.model.IvoaAbstractComputeResource;
import net.ivoa.calycopis.schema.spring.model.IvoaAbstractVolumeMount;
import net.ivoa.calycopis.schema.spring.model.IvoaSimpleComputeResource;

/**
 * A validator implementation to handle simple data resources.
 * 
 */
@Slf4j
public abstract class SimpleComputeResourceValidatorImpl
extends AbstractComputeResourceValidatorImpl
implements SimpleComputeResourceValidator
    {

    private final SimpleComputeResourceEntityFactory entityFactory;
    protected final AbstractVolumeMountValidatorFactory volumeMountValidatorFactory;

    /**
     * Public constructor.
     * 
     */
    public SimpleComputeResourceValidatorImpl(
        final SimpleComputeResourceEntityFactory entityFactory,
        final AbstractVolumeMountValidatorFactory volumeMountValidatorFactory
        ){
        super();
        this.entityFactory = entityFactory;
        this.volumeMountValidatorFactory = volumeMountValidatorFactory;
        }
    
    @Override
    public SimpleComputeResourceValidator.Result validateObject(
        final IvoaAbstractComputeResource requested,
        final OfferSetRequestParserContext context
        ){
        log.debug("validate(IvoaAbstractComputeResource)");
        log.debug("Resource [{}]", requested);
        //
        // Use exact class matching rather than instanceof to ensure each
        // validator only handles its specific type, not subclass types.
        // This prevents a parent type's validator from intercepting requests
        // that should be handled by a more specific subclass validator.
        // TODO Is this necessary, or do we just need to be careful about the order of validator registration?
        if (requested.getClass() == IvoaSimpleComputeResource.class)
            {
            return validate(
                (IvoaSimpleComputeResource) requested,
                context
                );
            }
        return new SimpleComputeResourceValidator.ResultBean(
            ResultEnum.CONTINUE
            );
        }

    protected abstract boolean validateCores(
        final IvoaSimpleComputeResource requested,
        final IvoaSimpleComputeResource validated,
        final OfferSetRequestParserContext context
        );

    protected abstract boolean validateMemory(
        final IvoaSimpleComputeResource requested,
        final IvoaSimpleComputeResource validated,
        final OfferSetRequestParserContext context
        );

    /**
     * Validate an IvoaAbstractComputeResource.
     *
     */
    public SimpleComputeResourceValidator.Result validate(
        final IvoaSimpleComputeResource requested,
        final OfferSetRequestParserContext context
        ){
        log.debug("validate(IvoaSimpleComputeResource)");
        log.debug("Resource [{}]", requested);

        boolean success = true ;

        IvoaSimpleComputeResource validated = new IvoaSimpleComputeResource()
            .kind(SimpleComputeResource.KIND_DISCRIMINATOR)
            .meta(
                ValidatorTools.makeMeta(
                    requested.getMeta(),
                    context
                    )
                );
        
        success &= validateCores(
            requested,
            validated,
            context
            );

        success &= validateMemory(
            requested,
            validated,
            context
            );
        
        //
        // Validate the volume mounts for this compute resource.
        log.debug("Validating the volume mounts");
        List<AbstractVolumeMountValidator.Result> volumeResults = new ArrayList<AbstractVolumeMountValidator.Result>();
        if (requested.getVolumes() != null)
            {
            for (IvoaAbstractVolumeMount volumeMount : requested.getVolumes())
                {
                log.debug("Validating volume mount [{}]", volumeMount);
                AbstractVolumeMountValidator.Result volumeResult = volumeMountValidatorFactory.validateObject(
                    volumeMount,
                    context
                    );
                volumeResults.add(
                    volumeResult
                    );
                success &= ResultEnum.ACCEPTED.equals(volumeResult.getEnum());
                }
            }

        //
        // Everything is good, create our Result.
        if (success)
            {
            SimpleComputeResourceValidator.Result result = new SimpleComputeResourceValidator.ResultBean(
                Validator.ResultEnum.ACCEPTED,
                validated,
                volumeResults
                ){
                @Override
                public AbstractComputeResourceEntity build(final SimpleExecutionSessionEntity session, final SimpleComputeResourceOffer offer)                
                    {
                    this.entity = SimpleComputeResourceValidatorImpl.this.entityFactory.create(
                        session,
                        this,
                        offer
                        );
                    return this.entity;
                    }

                @Override
                public Long getPrepareDuration()
                    {
                    return SimpleComputeResourceValidatorImpl.this.getPrepareDuration(
                        validated
                        );
                    }

                @Override
                public Long getReleaseDuration()
                    {
                    return SimpleComputeResourceValidatorImpl.this.getReleaseDuration(
                        validated
                        );
                    }
                };
            context.addComputeValidatorResult(
                result
                );
            log.debug("Validation PASSED for compute resource [{}]", requested.getMeta().getName());
            return result;
            }
        //
        // Something wasn't right, fail the validation.
        else {
            context.valid(false);
            log.debug("Validation FAILED for compute resource [{}]", requested.getMeta().getName());
            return new SimpleComputeResourceValidator.ResultBean(
                ResultEnum.FAILED
                );
            }
        }

    /**
     * Get the prepare duration for a resource.
     * This will be platform dependent, so it should be implemented in the platform specific subclasses.
     * 
     */
    protected abstract Long getPrepareDuration(final IvoaSimpleComputeResource validated);

    /**
     * Get the release duration for a resource.
     * This will be platform dependent, so it should be implemented in the platform specific subclasses.
     * 
     */
    protected abstract Long getReleaseDuration(final IvoaSimpleComputeResource validated);
    
    }
