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
 *       "value": 100,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-06-23T14:03:00",
 *     "name": "Cursor CLI",
 *     "version": "2026.02.13-41ac335",
 *     "model": "Claude 4.6 Opus (Thinking)",
 *     "contribution": {
 *       "value": 5,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.entities.storage.simple.mock;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.session.simple.SimpleExecutionSessionEntity;
import net.ivoa.calycopis.broker.engine.entities.storage.AbstractStorageResourceValidator;
import net.ivoa.calycopis.broker.engine.entities.storage.simple.SimpleStorageResourceEntity;
import net.ivoa.calycopis.broker.engine.functional.platform.Platform;
import net.ivoa.calycopis.broker.engine.functional.processing.action.ProcessingAction;
import net.ivoa.calycopis.broker.engine.functional.processing.action.mock.MockActionBuilder;

/**
 * 
 */
@Slf4j
@Entity
@Table(
    name = "mocksimplestorageresources"
    )
@DiscriminatorValue(
    value="uri:mock-simple-storage-resource"
    )
public class MockSimpleStorageResourceEntity
extends SimpleStorageResourceEntity
implements MockSimpleStorageResource
    {

    /**
     * Protected constructor for JPA entities.
     * 
     */
    protected MockSimpleStorageResourceEntity()
        {
        super();
        }

    /**
     * Protected constructor used by our Factories.
     *
     */
    protected MockSimpleStorageResourceEntity(
        final SimpleExecutionSessionEntity session,
        final AbstractStorageResourceValidator.Result result
        ){
        super(
            session,
            result
            );
        }

    @Embedded
    private MockActionBuilder actionBuilder = new MockActionBuilder(this);
    
    @Override
    protected ProcessingAction makePrepareAction(final Platform platform)
        {
        log.debug(
            "makePrepareAction for storage volume [{}][{}]",
            this.getUuid(),
            this.getClass().getSimpleName()
            );
        return actionBuilder.makePrepareAction(
            platform
            );
        }

    @Override
    protected ProcessingAction makeMonitorAction(Platform platform)
        {
        log.debug(
            "makeMonitorAction for storage volume [{}][{}]",
            this.getUuid(),
            this.getClass().getSimpleName()
            );
        return actionBuilder.makeMonitorAction(
            platform
            );
        }

    @Override
    protected ProcessingAction makeReleaseAction(Platform platform)
        {
        log.debug(
            "makeReleaseAction for storage volume [{}][{}]",
            this.getUuid(),
            this.getClass().getSimpleName()
            );
        return actionBuilder.makeReleaseAction(
            platform
            );
        }
    }
