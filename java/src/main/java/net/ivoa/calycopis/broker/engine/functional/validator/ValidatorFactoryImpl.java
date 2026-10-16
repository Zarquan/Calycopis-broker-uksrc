/*
 * AIMetrics: [
 *     {
 *     "timestamp": "2026-02-17T07:10:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 8,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-02-17T13:20:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 10,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-10-16T05:35:00/2026-10-16T06:15:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 25,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */
package net.ivoa.calycopis.broker.engine.functional.validator;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.ivoa.calycopis.broker.engine.entities.component.ComponentEntity;
import net.ivoa.calycopis.broker.engine.entities.offerset.OfferSetRequestParserContext;
import net.ivoa.calycopis.broker.engine.functional.factory.FactoryBaseImpl;
import net.ivoa.calycopis.openapi.spring.model.IvoaComponentMetadata;

/**
 * Base class for validator factories.
 * 
 */
public abstract class ValidatorFactoryImpl<ObjectType, EntityType extends ComponentEntity>
extends FactoryBaseImpl
implements Validator<ObjectType, EntityType>, ValidatorFactory<ObjectType, EntityType>
    {

    /**
     * Our list of validators.
     * 
     */
    protected List<Validator<ObjectType, EntityType>> validators = new ArrayList<Validator<ObjectType, EntityType>>();

    /**
     * Add a validator to our list.
     * 
     */
    public void addValidator(
        final Validator<ObjectType, EntityType> validator
        ){
        this.validators.add(validator);
        }
    
    /**
     * Report an unknown type.
     *
     */
    public abstract void unknown(
        final OfferSetRequestParserContext context,
        final ObjectType object
        );

    /**
     * Create a result for this factory.
     *
     * A subclass whose validators return a more specific Result overrides this,
     * so that the fall-through for an unrecognised kind returns a result of the
     * type its callers expect, rather than one that has to be cast. An
     * unrecognised kind is a request the service cannot serve, so it becomes a
     * NO with an empty offer list, not an internal error (see issue #147).
     *
     */
    protected Result<ObjectType, EntityType> newResult(final ResultEnum result)
        {
        return new ResultBean<ObjectType, EntityType>(
            result
            );
        }

    @Override
    public Result<ObjectType, EntityType> validateObject(
        final ObjectType requested,
        final OfferSetRequestParserContext context
        ){
        //
        // Try each of the validators in our list.
        // Validators are tried in registration order and the first one to
        // return ACCEPTED or FAILED wins. Because of this, validators that
        // handle more specific subtypes (e.g. SkaoDataResource) must be
        // registered before validators that handle their parent types
        // (e.g. IvoaDataResource).
        for (Validator<ObjectType, EntityType> validator : validators)
            {
            Result<ObjectType, EntityType> result = validator.validateObject(
                requested,
                context
                );
            switch(result.getEnum())
                {
                case CONTINUE:
                    break;
                case ACCEPTED:
                case FAILED:
                    return result;
                }
            }
        //
        // Fail the validation if we didn't find a matching validator.
        // Not finding one means we cannot serve this request, which is a NO
        // with an empty offer list, not an internal error.
        unknown(
            context,
            requested
            );
        return newResult(
            ResultEnum.FAILED
            );
        }

    @Override
    public ResultEnum validateEnum(
        final ObjectType requested,
        final OfferSetRequestParserContext context
        ){
        return validateObject(
            requested,
            context
            ).getEnum();
        }
    
    /**
     * Report an unknown resource.
     *
     */
    protected void unknown(
        final OfferSetRequestParserContext context,
        final URI kind,
        final IvoaComponentMetadata meta
        ){
        if (meta != null)
            {
            context.addWarning(
                "uri:unknown-type",
                "Unknown kind [${kind}][${name}][${uuid}]",
                Map.of(
                    "kind",
                    (kind != null) ? kind.toString() : "null",
                    "name",
                    (meta.getName() != null) ? meta.getName() : "null",
                    "uuid",
                    (meta.getUuid() != null) ? meta.getUuid().toString() : "null"
                    )
                );
            }
        else
            {
            context.addWarning(
                "uri:unknown-type",
                "Unknown kind [${kind}]",
                Map.of(
                    "kind",
                    kind.toString()
                    )
                );
            }
        context.valid(false);
        }
    }
