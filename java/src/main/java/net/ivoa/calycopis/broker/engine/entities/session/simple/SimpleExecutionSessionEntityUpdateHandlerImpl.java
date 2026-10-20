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
 *     "timestamp": "2026-09-05T12:41:19",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.1.1-rc.2",
 *     "model": "deepseek-v4-flash",
 *     "contribution": {
 *       "value": 3,
 *       "units": "%"
 *       }
 *     },
 *     {
 *     "timestamp": "2026-10-20T11:57:26",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 15,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */

package net.ivoa.calycopis.broker.engine.entities.session.simple;

import java.util.Optional;
import java.util.UUID;

import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.session.AbstractExecutionSessionEntity;
import net.ivoa.calycopis.broker.engine.functional.factory.FactoryBaseImpl;
import net.ivoa.calycopis.broker.engine.functional.platform.Platform;
import net.ivoa.calycopis.openapi.spring.model.IvoaAbstractUpdate;
import net.ivoa.calycopis.openapi.spring.model.IvoaEnumValueUpdate;
import net.ivoa.calycopis.openapi.spring.model.IvoaSimpleExecutionSessionPhase;

/**
 * 
 */
@Slf4j
public class SimpleExecutionSessionEntityUpdateHandlerImpl
extends FactoryBaseImpl
implements SimpleExecutionSessionEntityUpdater
    {

    private final Platform platform;
    
    /**
     * Public constructor, used by our Platform.
     * 
     */
    public SimpleExecutionSessionEntityUpdateHandlerImpl(
        final Platform platform
        ){
        super();
        this.platform = platform;
        }

    @Override
    public Optional<SimpleExecutionSessionEntity> update(final UUID uuid, final IvoaAbstractUpdate update)
        {
        log.debug("update(UUID)");
        log.debug("UUID   [{}]", uuid);
        log.debug("Update [{}]", update.getClass());

        Optional<SimpleExecutionSessionEntity> optional = this.platform.getExecutionSessionEntityFactory().select(
            uuid
            );
        if (optional.isEmpty())
            {
            log.warn("Session not found [{}]", uuid);
            return Optional.empty();
            }
        else {
            return Optional.of(
                this.platform.getExecutionSessionEntityFactory().save(
                    this.update(
                        optional.get(),
                        update
                        )
                    )
                );
            }
        }

    protected SimpleExecutionSessionEntity update(SimpleExecutionSessionEntity entity , final IvoaAbstractUpdate update)
        {
        log.debug("update(Entity, Update)");
        log.debug("Entity [{}]", entity.getUuid());
        log.debug("Update [{}]", update.getClass());
        switch(update)
            {
            case IvoaEnumValueUpdate valueupdate :
                entity = this.update(
                    entity,
                    valueupdate
                    );
                break ;

            default:
                throw InvalidExecutionSessionUpdateException.unknownType(
                    update.getClass().getName()
                    );
            }
        return entity ;
        }

    protected SimpleExecutionSessionEntity update(SimpleExecutionSessionEntity entity , final IvoaEnumValueUpdate update)
        {
        log.debug("update(Entity, EnumValueUpdate)");
        log.debug("Entity [{}][{}]", entity.getUuid(), entity.getPhase());
        log.debug("Update [{}][{}]", update.getPath(), update.getValue());
        switch(update.getPath())
            {
            case "phase"  :
            case "/phase" :
                try {
                    IvoaSimpleExecutionSessionPhase newphase = IvoaSimpleExecutionSessionPhase.fromValue(
                        update.getValue()
                        );
                    entity = this.update(
                        entity,
                        newphase
                        );
                    }
                catch (IllegalArgumentException ouch)
                    {
                    InvalidExecutionSessionUpdateException invalid = InvalidExecutionSessionUpdateException.invalidValue(
                        update.getPath(),
                        update.getValue()
                        );
                    invalid.initCause(
                        ouch
                        );
                    throw invalid;
                    }
                break ;
            default:
                throw InvalidExecutionSessionUpdateException.unknownPath(
                    update.getPath()
                    );
            }
        return entity ;
        }

    protected SimpleExecutionSessionEntity update(SimpleExecutionSessionEntity entity, final IvoaSimpleExecutionSessionPhase newphase)
        {
        log.debug("update(Entity, Phase) [{}][{}][{}]", entity.getUuid(), entity.getPhase(), newphase);
        switch(newphase)
            {
            case ACCEPTED:
                entity = accept(
                    entity
                    );
                break ;
            case REJECTED:
                entity = reject(
                    entity
                    );
                break ;
            case CANCELLED:
                entity = cancel(
                    entity
                    );
                break ;
            case FAILED:
                entity = fail(
                    entity
                    );
                break ;
            default:
                throw InvalidExecutionSessionUpdateException.invalidTransition(
                    entity.getPhase().toString(),
                    newphase.toString()
                    );
            }
        return entity ;
        }

    protected SimpleExecutionSessionEntity accept(final SimpleExecutionSessionEntity entity)
        {
        log.debug("accept(Entity, Phase) [{}][{}]", entity.getUuid(), entity.getPhase());
        switch(entity.getPhase())
            {
            case OFFERED:
                entity.setPhase(
                    IvoaSimpleExecutionSessionPhase.ACCEPTED
                    );
                //
                // REJECT the other Sessions in the offer.
                for (AbstractExecutionSessionEntity sibling : entity.getOfferSetEntity().getOfferEntities())
                    {
                    if (sibling.getUuid().equals(entity.getUuid()) == false)
                        {
                        if (sibling instanceof SimpleExecutionSessionEntity siblingSession)
                            {
                            if (siblingSession.getPhase() == IvoaSimpleExecutionSessionPhase.OFFERED)
                                {
                                reject(
                                    siblingSession
                                    );
                                }
                            }
                        }
                    }

                log.debug("Creating prepare request for session [{}]", entity.getUuid());
                this.platform.getProcessingRequestFactory().getSessionProcessingRequestFactory().createPrepareSessionRequest(
                    entity
                    );

                break;
            default:
                throw InvalidExecutionSessionUpdateException.invalidTransition(
                    entity.getPhase().toString(),
                    IvoaSimpleExecutionSessionPhase.ACCEPTED.toString()
                    );
            }
        return entity ;
        }

    // This just sets the phase, without scheduling a processing request.
    // TODO This should schedule a corresponding processing requests.
    // TODO Should this cancel the components here ?
    // see https://github.com/uksrc/Calycopis-broker/issues/99
    protected SimpleExecutionSessionEntity reject(final SimpleExecutionSessionEntity entity)
        {
        log.debug("reject(Entity, Phase) [{}][{}]", entity.getUuid(), entity.getPhase());
        switch(entity.getPhase())
            {
            case OFFERED:
                entity.setPhase(
                    IvoaSimpleExecutionSessionPhase.REJECTED
                    );
                break;
            default:
                throw InvalidExecutionSessionUpdateException.invalidTransition(
                    entity.getPhase().toString(),
                    IvoaSimpleExecutionSessionPhase.REJECTED.toString()
                    );
            }
        return entity ;
        }

    // This just sets the phase, without scheduling a processing request.
    // TODO This should schedule a corresponding processing requests.
    // TODO Should this cancel the components here ?
    // see https://github.com/uksrc/Calycopis-broker/issues/99
    protected SimpleExecutionSessionEntity cancel(final SimpleExecutionSessionEntity entity)
        {
        log.debug("cancel(Entity, Phase) [{}][{}]", entity.getUuid(), entity.getPhase());
        switch(entity.getPhase())
            {
            case INITIAL:
            case OFFERED:
            case ACCEPTED:
            case WAITING:
            case PREPARING:
            case AVAILABLE:
            case RUNNING:
                entity.setPhase(
                    IvoaSimpleExecutionSessionPhase.CANCELLED
                    );
                break;
            default:
                throw InvalidExecutionSessionUpdateException.invalidTransition(
                    entity.getPhase().toString(),
                    IvoaSimpleExecutionSessionPhase.CANCELLED.toString()
                    );
            }
        return entity ;
        }

    // This just sets the phase, without scheduling a processing request.
    // TODO This should schedule a corresponding processing requests.
    // TODO Should this cancel the components here ?
    // see https://github.com/uksrc/Calycopis-broker/issues/99
    // TODO This should require a reason.
    // see https://github.com/uksrc/Calycopis-broker/issues/100
    protected SimpleExecutionSessionEntity fail(final SimpleExecutionSessionEntity entity)
        {
        log.debug("fail(Entity, Phase) [{}][{}]", entity.getUuid(), entity.getPhase());
        switch(entity.getPhase())
            {
            case INITIAL:
            case OFFERED:
            case ACCEPTED:
            case WAITING:
            case PREPARING:
            case AVAILABLE:
            case RUNNING:
            case RELEASING:
                // If a sessions fails during releasing - has it actually failed ?
                entity.setPhase(
                    IvoaSimpleExecutionSessionPhase.FAILED
                    );
                break;
            default:
                throw InvalidExecutionSessionUpdateException.invalidTransition(
                    entity.getPhase().toString(),
                    IvoaSimpleExecutionSessionPhase.FAILED.toString()
                    );
            }
        return entity ;
        }
    }
