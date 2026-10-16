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
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 15,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-10-16T05:35:00/2026-10-16T06:15:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 30,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */
package net.ivoa.calycopis.broker.engine.entities.storage;

import net.ivoa.calycopis.broker.engine.entities.offerset.OfferSetRequestParserContext;
import net.ivoa.calycopis.broker.engine.functional.validator.Validator;
import net.ivoa.calycopis.broker.engine.functional.validator.ValidatorFactoryImpl;
import net.ivoa.calycopis.openapi.spring.model.IvoaAbstractStorageResource;

/**
 * An AbstractStorageResourceValidatorFactory implementation.
 * 
 */
public class AbstractStorageResourceValidatorFactoryImpl
extends ValidatorFactoryImpl<IvoaAbstractStorageResource, AbstractStorageResourceEntity>
implements AbstractStorageResourceValidatorFactory
    {

    /**
     * Public constructor used by our Platform.
     * 
     */
    public AbstractStorageResourceValidatorFactoryImpl()
        {
        super();
        }

    /**
     * Create a storage result, so that the fall-through for an unrecognised
     * storage kind returns the type our callers expect.
     *
     */
    @Override
    protected Validator.Result<IvoaAbstractStorageResource, AbstractStorageResourceEntity> newResult(final ResultEnum result)
        {
        return new AbstractStorageResourceValidator.ResultBean(
            result
            );
        }

    /**
     * Validate a StorageResource, needed to resolve the inherited generics.
     *
     */
    public AbstractStorageResourceValidator.Result validateObject(
        final IvoaAbstractStorageResource object,
        final OfferSetRequestParserContext context
        ){
        Validator.Result<IvoaAbstractStorageResource, AbstractStorageResourceEntity> result = super.validateObject(
            object,
            context
            );
        if (result instanceof AbstractStorageResourceValidator.Result)
            {
            return (AbstractStorageResourceValidator.Result) result;
            }
        //
        // A validator returned a result of the wrong type. Fail the validation
        // rather than throwing, so that a request we cannot validate becomes a
        // NO with an empty offer list, not a 500 response (see issue #147).
        //
        return new AbstractStorageResourceValidator.ResultBean(
            ResultEnum.FAILED
            );
        } 

    @Override
    public void unknown(
        final OfferSetRequestParserContext context,
        final IvoaAbstractStorageResource resource
        ){
        unknown(
            context,
            resource.getKind(),
            resource.getMeta()
            );
        }
    }
