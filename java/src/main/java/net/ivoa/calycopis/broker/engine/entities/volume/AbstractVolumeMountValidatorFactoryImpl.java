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
package net.ivoa.calycopis.broker.engine.entities.volume;

import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.offerset.OfferSetRequestParserContext;
import net.ivoa.calycopis.broker.engine.functional.validator.Validator;
import net.ivoa.calycopis.broker.engine.functional.validator.ValidatorFactoryImpl;
import net.ivoa.calycopis.openapi.spring.model.IvoaAbstractVolumeMount;

/**
 *
 */
@Slf4j
public class AbstractVolumeMountValidatorFactoryImpl
extends ValidatorFactoryImpl<IvoaAbstractVolumeMount, AbstractVolumeMountEntity>
implements AbstractVolumeMountValidatorFactory
    {

    /**
     * Public constructor, used by our Platform.
     *
     */
    public AbstractVolumeMountValidatorFactoryImpl()
        {
        super();
        }

    /**
     * Create a volume mount result, so that the fall-through for an
     * unrecognised volume mount kind returns the type our callers expect.
     *
     */
    @Override
    protected Validator.Result<IvoaAbstractVolumeMount, AbstractVolumeMountEntity> newResult(final ResultEnum result)
        {
        return new AbstractVolumeMountValidator.ResultBean(
            result
            );
        }

    /**
     * Validate a volume mount, needed to resolve the inherited generics.
     *
     */
    public AbstractVolumeMountValidator.Result validateObject(
        final IvoaAbstractVolumeMount object,
        final OfferSetRequestParserContext context
        ){
        Validator.Result<IvoaAbstractVolumeMount, AbstractVolumeMountEntity> result = super.validateObject(
            object,
            context
            );
        if (result instanceof AbstractVolumeMountValidator.Result)
            {
            return (AbstractVolumeMountValidator.Result) result;
            }
        //
        // A validator returned a result of the wrong type. Fail the validation
        // rather than throwing, so that a request we cannot validate becomes a
        // NO with an empty offer list, not a 500 response (see issue #147).
        //
        return new AbstractVolumeMountValidator.ResultBean(
            ResultEnum.FAILED
            );
        } 
    
    @Override
    public void unknown(
        final OfferSetRequestParserContext context,
        final IvoaAbstractVolumeMount resource
        ){
        unknown(
            context,
            resource.getKind(),
            resource.getMeta()
            );
        }
    }
