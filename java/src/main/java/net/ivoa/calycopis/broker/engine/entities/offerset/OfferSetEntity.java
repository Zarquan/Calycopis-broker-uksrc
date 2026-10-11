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
 *     "interval": "2026-10-11T11:15:00/2026-10-11T11:34:00",
 *     "name": "@deepseek-ai/dsh",
 *     "version": "0.2.0-rc.2",
 *     "model": "deepseek-flash",
 *     "contribution": {
 *       "value": 10,
 *       "units": "%"
 *       }
 *     }
 *   ]
 *
 */
package net.ivoa.calycopis.broker.engine.entities.offerset;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.extern.slf4j.Slf4j;
import net.ivoa.calycopis.broker.engine.entities.component.ComponentEntity;
import net.ivoa.calycopis.broker.engine.entities.identity.IdentityEntity;
import net.ivoa.calycopis.broker.engine.entities.message.Message;
import net.ivoa.calycopis.broker.engine.entities.message.MessageEntity;
import net.ivoa.calycopis.broker.engine.entities.profiling.ProfilingEntry;
import net.ivoa.calycopis.broker.engine.entities.profiling.ProfilingEntryEntity;
import net.ivoa.calycopis.broker.engine.entities.profiling.ProfilingTarget;
import net.ivoa.calycopis.broker.engine.entities.session.AbstractExecutionSession;
import net.ivoa.calycopis.broker.engine.entities.session.AbstractExecutionSessionEntity;
import net.ivoa.calycopis.broker.engine.util.ListWrapper;
import net.ivoa.calycopis.broker.engine.util.URIBuilder;
import net.ivoa.calycopis.openapi.spring.model.IvoaAbstractExecutionSession;
import net.ivoa.calycopis.openapi.spring.model.IvoaOfferSetResponse;
import net.ivoa.calycopis.openapi.spring.model.IvoaOfferSetResponse.ResultEnum;

@Slf4j
@Entity
@Table(
    name = "offersets"
    )
@DiscriminatorValue(
    value = "uri:offerset"
    )
public class OfferSetEntity
extends ComponentEntity
implements OfferSet, ProfilingTarget
    {
    @Override
    protected URI getWebappPath()
        {
        return OfferSet.WEBAPP_PATH;
        }

    @Override
    public URI getKind()
        {
        return OfferSet.KIND_DISCRIMINATOR ;
        }

    /**
     * Protected constructor for JPA entities.
     *  
     */
    protected OfferSetEntity()
        {
        super();
        }

    /**
     * Protected constructor used by our Factories.
     *  
     */
    protected OfferSetEntity(final String name, final String description, final Instant created, final Instant expires, final IdentityEntity owner)
        {
        super(
            name,
            description,
            created,
            owner
            );
        this.expires = expires;
        }

    @Column(name = "expires")
    private Instant expires;

    @Override
    public Instant getExpires()
        {
        return this.expires;
        }

    @Column(name = "result")
    private ResultEnum result;

    public ResultEnum getResult()
        {
        return this.result;
        }

    public void setResult(final ResultEnum result)
        {
        this.result = result;
        }

    /**
     * 
     * Hibernate JPA OneToMany relationship.
     * https://vladmihalcea.com/the-best-way-to-map-a-onetomany-association-with-jpa-and-hibernate/
     * https://vladmihalcea.com/jpa-hibernate-synchronize-bidirectional-entity-associations/
     * 
     */
    @OneToMany(
        mappedBy = "offerset",
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL,
        orphanRemoval = true
        )
    List<AbstractExecutionSessionEntity> executions = new ArrayList<AbstractExecutionSessionEntity>();

    @Override
    public Iterable<AbstractExecutionSession> getOffers()
        {
        return new ListWrapper<AbstractExecutionSession, AbstractExecutionSessionEntity>(
            this.executions
            ){
            public AbstractExecutionSession wrap(final AbstractExecutionSessionEntity inner)
                {
                return inner;
                }
            };
        }

    /**
     * Get a list of the Execution offers.
     * 
     */
    public Iterable<AbstractExecutionSessionEntity> getOfferEntities()
        {
        return new ListWrapper<AbstractExecutionSessionEntity, AbstractExecutionSessionEntity>(
            this.executions
            ){
            public AbstractExecutionSessionEntity wrap(final AbstractExecutionSessionEntity inner)
                {
                return inner;
                }
            };
        }

    public void addExecutionSession(final AbstractExecutionSessionEntity execution)
        {
        executions.add(execution);
        }

    /**
     * 
     * Hibernate JPA OneToMany relationship.
     * https://vladmihalcea.com/the-best-way-to-map-a-onetomany-association-with-jpa-and-hibernate/
     * https://vladmihalcea.com/jpa-hibernate-synchronize-bidirectional-entity-associations/
     * 
     */
    @OneToMany(
        mappedBy = "parent",
        fetch = FetchType.LAZY,
        cascade = CascadeType.ALL,
        orphanRemoval = true
        )
    protected List<ProfilingEntryEntity> profilingEntries = new ArrayList<ProfilingEntryEntity>();

    public Iterable<ProfilingEntryEntity> getProfilingEntryEntities()
        {
        return this.profilingEntries;
        }

    @Override
    public List<ProfilingEntry> getProfilingEntries()
        {
        return new ListWrapper<ProfilingEntry, ProfilingEntryEntity>(
            this.profilingEntries
            ){
            public ProfilingEntry wrap(final ProfilingEntryEntity inner)
                {
                return inner;
                }
            };
        }

    /**
     * Add a profiling entry, setting its parent to this OfferSetEntity.
     *
     */
    @Override
    public void addProfilingEntry(final ProfilingEntry entry)
        {
        if (entry instanceof ProfilingEntryEntity profilingEntry)
            {
            profilingEntry.setParent(
                this
                );
            this.profilingEntries.add(
                profilingEntry
                );
            }
        else {
            log.error(
                "Unexpected profiling entry type [{}]",
                entry.getClass().getSimpleName()
                );
            throw new IllegalArgumentException(
                "Unexpected profiling entry type [" + entry.getClass().getSimpleName() + "]"
                );
            }
        }

    public IvoaOfferSetResponse makeBean(final URIBuilder uribuilder)
        {
        return this.fillBean(
            uribuilder,
            new IvoaOfferSetResponse().meta(
                this.makeMeta(
                    uribuilder
                    )
                )
            );
        }

    public IvoaOfferSetResponse fillBean(final URIBuilder uribuilder, final IvoaOfferSetResponse bean)
        {
        bean.setKind(
            this.getKind()
            );
        bean.setResult(
            this.getResult()
            );
        bean.setOffers(
            new ListWrapper<IvoaAbstractExecutionSession, AbstractExecutionSessionEntity>(
                this.executions
                ){
                public IvoaAbstractExecutionSession wrap(final AbstractExecutionSessionEntity inner)
                    {
                    return inner.makeBean(
                        uribuilder
                        );
                    }
                }
            );
        return bean;
        }

    /**
     * Claim a set of messages by setting the message parent and adding it to our list.
     * TODO - create new MessageEntities from Message interfaces. 
     */
    public void claimMessages(final Iterable<Message> messages)
        {
        for (Message message : messages)
            {
            if (message instanceof MessageEntity)
                {
                ((MessageEntity) message).setParent(
                    this
                    );
                this.messages.add(
                    ((MessageEntity) message)
                    );
                }
            else {
                log.error(
                    "Unexpected message type [{}]",
                    message.getClass().getSimpleName()
                    );
                throw new IllegalArgumentException(
                    "Unexpected message type [" + message.getClass().getSimpleName() + "]"
                    );
                }
            }
        }
    }
