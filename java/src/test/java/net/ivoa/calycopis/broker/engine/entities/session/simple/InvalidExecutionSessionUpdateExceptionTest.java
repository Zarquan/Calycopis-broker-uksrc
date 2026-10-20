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
 *     "timestamp": "2026-10-20T11:57:26",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 100,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.entities.session.simple;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import net.ivoa.calycopis.openapi.spring.model.IvoaAbstractUpdate;
import net.ivoa.calycopis.openapi.spring.model.IvoaEnumValueUpdate;
import net.ivoa.calycopis.openapi.spring.model.IvoaSimpleExecutionSessionPhase;
import net.ivoa.calycopis.openapi.spring.model.IvoaStringValueUpdate;

/**
 * Tests for the invalid update exceptions raised by the update handler.
 *
 */
public class InvalidExecutionSessionUpdateExceptionTest
    {

    private TestHandler handler;
    private SimpleExecutionSessionEntity entity;

    @BeforeEach
    public void setup()
        {
        this.handler = new TestHandler();
        this.entity = Mockito.mock(
            SimpleExecutionSessionEntity.class
            );
        Mockito.when(
            this.entity.getUuid()
            ).thenReturn(
                UUID.randomUUID()
                );
        Mockito.when(
            this.entity.getPhase()
            ).thenReturn(
                IvoaSimpleExecutionSessionPhase.RUNNING
                );
        }

    @Test
    public void unknownUpdateTypeIsRejected()
        {
        IvoaStringValueUpdate update = new IvoaStringValueUpdate();
        update.setKind(
            "uri:string-value-update"
            );
        update.setPath(
            "/phase"
            );
        update.setValue(
            "RUNNING"
            );

        InvalidExecutionSessionUpdateException exception = assertThrows(
            InvalidExecutionSessionUpdateException.class,
            () -> this.handler.apply(this.entity, update)
            );
        assertTrue(
            exception.getMessage().contains("Unsupported update type"),
            "The exception must describe the unsupported update type"
            );
        }

    @Test
    public void unknownUpdatePathIsRejected()
        {
        IvoaEnumValueUpdate update = new IvoaEnumValueUpdate();
        update.setKind(
            "uri:enum-value-update"
            );
        update.setPath(
            "/bogus"
            );
        update.setValue(
            "CANCELLED"
            );

        InvalidExecutionSessionUpdateException exception = assertThrows(
            InvalidExecutionSessionUpdateException.class,
            () -> this.handler.apply(this.entity, update)
            );
        assertTrue(
            exception.getMessage().contains("Unsupported update path"),
            "The exception must describe the unsupported update path"
            );
        assertEquals(
            "/bogus",
            exception.getPath()
            );
        }

    @Test
    public void invalidUpdateValueIsRejected()
        {
        IvoaEnumValueUpdate update = new IvoaEnumValueUpdate();
        update.setKind(
            "uri:enum-value-update"
            );
        update.setPath(
            "/phase"
            );
        update.setValue(
            "NOT_A_PHASE"
            );

        InvalidExecutionSessionUpdateException exception = assertThrows(
            InvalidExecutionSessionUpdateException.class,
            () -> this.handler.apply(this.entity, update)
            );
        assertTrue(
            exception.getMessage().contains("Invalid value"),
            "The exception must describe the invalid update value"
            );
        assertEquals(
            "/phase",
            exception.getPath()
            );
        }

    @Test
    public void invalidPhaseTransitionIsRejected()
        {
        InvalidExecutionSessionUpdateException exception = assertThrows(
            InvalidExecutionSessionUpdateException.class,
            () -> this.handler.applyPhase(
                this.entity,
                IvoaSimpleExecutionSessionPhase.ACCEPTED
                )
            );
        assertTrue(
            exception.getMessage().contains("Invalid phase transition"),
            "The exception must describe the invalid phase transition"
            );
        Mockito.verify(
            this.entity,
            Mockito.never()
            ).setPhase(
                Mockito.any()
                );
        }

    /**
     * A test subclass that exposes the protected update methods.
     *
     */
    private static class TestHandler
    extends SimpleExecutionSessionEntityUpdateHandlerImpl
        {

        TestHandler()
            {
            super(null);
            }

        SimpleExecutionSessionEntity apply(
            final SimpleExecutionSessionEntity entity,
            final IvoaAbstractUpdate request
            ){
            return super.update(
                entity,
                request
                );
            }

        SimpleExecutionSessionEntity applyPhase(
            final SimpleExecutionSessionEntity entity,
            final IvoaSimpleExecutionSessionPhase phase
            ){
            return super.update(
                entity,
                phase
                );
            }
        }
    }
