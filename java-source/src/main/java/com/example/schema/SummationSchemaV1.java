package com.example.schema;

import com.google.common.collect.ImmutableList;
import net.corda.core.schemas.MappedSchema;
import net.corda.core.schemas.PersistentState;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.UUID;

public class SummationSchemaV1 extends MappedSchema {
    public SummationSchemaV1() {
        super(SummationSchema.class, 1, ImmutableList.of(PersistentSummation.class));
    }

    @Entity
    @Table(name = "summation_states")
    public static class PersistentSummation extends PersistentState {
        @Column(name = "initiator") private final String initiator;
        @Column(name = "acceptor") private final String acceptor;
        @Column(name = "sum") private final int sum;
        @Column(name = "linear_id") private final UUID linearId;


        public PersistentSummation(String initiator, String acceptor, int sum, UUID linearId) {
            this.initiator = initiator;
            this.acceptor = acceptor;
            this.sum = sum;
            this.linearId = linearId;
        }

        // Default constructor required by hibernate.
        public PersistentSummation() {
            this.initiator = null;
            this.acceptor = null;
            this.sum = 0;
            this.linearId = null;
        }

        public String getInitiator() {
            return initiator;
        }

        public String getAcceptor() {
            return acceptor;
        }

        public int getSum() {
            return sum;
        }

        public UUID getId() {
            return linearId;
        }
    }
}