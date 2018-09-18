package com.example.contract;

import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;

public class SummationContract implements Contract {
    public static final String SUMMATION_CONTRACT_ID = "com.example.contract.SummationContract";

    @Override
    public void verify(LedgerTransaction tx) {
    }

    /**
     * This contract only implements one command, Create.
     */
    public interface Commands extends CommandData {
        class Create implements Commands {}
    }
}