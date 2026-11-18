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
 *     "interval": "2026-11-16T05:14:00/2026-11-16T05:40:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 10,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "interval": "2026-11-18T11:15:00/2026-11-18T11:55:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-v4.1-flash-1017",
 *     "contribution": {
 *       "value": 20,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 *
 */

package net.ivoa.calycopis.broker.engine.entities.compute.simple;

import net.ivoa.calycopis.broker.engine.entities.compute.AbstractComputeReservationEntity;
import net.ivoa.calycopis.broker.engine.entities.compute.AbstractComputeReservationValidator;
import net.ivoa.calycopis.broker.engine.entities.session.simple.SimpleExecutionSessionEntity;
import net.ivoa.calycopis.broker.engine.entities.volume.AbstractVolumeMountValidator;
import net.ivoa.calycopis.broker.engine.functional.booking.compute.simple.SimpleComputeOffer;
import net.ivoa.calycopis.openapi.spring.model.IvoaAbstractComputeResource;

/**
 * 
 */
public interface SimpleComputeReservationValidator
extends AbstractComputeReservationValidator
    {
    /**
     * Public interface for a validator result.
     * 
     */
    public static interface Result
    extends AbstractComputeReservationValidator.Result 
        {
        /**
         * Build an ComputeResourceEntity based on a resource offer.
         * 
         */
        public AbstractComputeReservationEntity build(final SimpleExecutionSessionEntity session, final SimpleComputeOffer offer);
        }

    /**
     * Bean implementation of a validator result.
     * 
     */
    public static class ResultBean
    extends AbstractComputeReservationValidator.ResultBean
    implements SimpleComputeReservationValidator.Result
        {
        /**
         * Protected constructor with just a ResultEnum.
         * Used to respond to a failed validation, where we don't have an object to return.
         * 
         */
        protected ResultBean(final ResultEnum result)
            {
            super(result);
            }

        /**
         * Public constructor with a ResultEnum and IvoaAbstractComputeResource.
         * 
         */
        public ResultBean(
            final ResultEnum result,
            final IvoaAbstractComputeResource object,
            Iterable<AbstractVolumeMountValidator.Result> volumeResults
            ){
            super(
                result,
                object
                );
            this.volumeResults = volumeResults;
            }

        private Iterable<AbstractVolumeMountValidator.Result> volumeResults;
        @Override
        public Iterable<AbstractVolumeMountValidator.Result> getVolumeResults()
            {
            return volumeResults;
            }

        @Override
        public AbstractComputeReservationEntity build(
            final SimpleExecutionSessionEntity session,
            final SimpleComputeOffer offer
            ){
            return null;
            }
        }
    }
