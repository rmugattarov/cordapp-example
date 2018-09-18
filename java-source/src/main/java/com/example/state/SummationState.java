package com.example.state;

import com.example.schema.SummationSchemaV1;
import com.google.common.collect.ImmutableList;
import net.corda.core.contracts.LinearState;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.identity.AbstractParty;
import net.corda.core.identity.Party;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;
import net.corda.core.schemas.QueryableState;

import java.util.Arrays;
import java.util.List;

public class SummationState implements LinearState, QueryableState {
    private final Integer value;
    private final Party initiator;
    private final Party acceptor;
    private final UniqueIdentifier linearId;

    public SummationState(Integer value,
                          Party initiator,
                          Party acceptor,
                          UniqueIdentifier linearId)
    {
        this.value = value;
        this.initiator = initiator;
        this.acceptor = acceptor;
        this.linearId = linearId;
    }

    public Integer getValue() { return value; }
    public Party getInitiator() { return initiator; }
    public Party getAcceptor() { return acceptor; }
    @Override public UniqueIdentifier getLinearId() { return linearId; }
    @Override public List<AbstractParty> getParticipants() {
        return Arrays.asList(initiator, acceptor);
    }

    @Override public PersistentState generateMappedObject(MappedSchema schema) {
        if (schema instanceof SummationSchemaV1) {
            return new SummationSchemaV1.PersistentSummation(
                    this.initiator.getName().toString(),
                    this.acceptor.getName().toString(),
                    this.value,
                    this.linearId.getId());
        } else {
            throw new IllegalArgumentException("Unrecognised schema $schema");
        }
    }

    @Override public Iterable<MappedSchema> supportedSchemas() {
        return ImmutableList.of(new SummationSchemaV1());
    }

    @Override
    public String toString() {
        return String.format("SummationState(value=%s, initiator=%s, acceptor=%s, linearId=%s)", value, initiator, acceptor, linearId);
    }
}