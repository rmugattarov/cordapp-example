package com.example.flow;

import co.paralleluniverse.fibers.Suspendable;
import com.example.contract.SummationContract;
import com.example.state.SummationState;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.corda.core.contracts.Command;
import net.corda.core.contracts.UniqueIdentifier;
import net.corda.core.flows.*;
import net.corda.core.identity.Party;
import net.corda.core.transactions.SignedTransaction;
import net.corda.core.transactions.TransactionBuilder;
import net.corda.core.utilities.ProgressTracker;
import net.corda.core.utilities.ProgressTracker.Step;
import net.corda.core.utilities.UntrustworthyData;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;

import static com.example.contract.SummationContract.SUMMATION_CONTRACT_ID;

public class ExampleFlow {

    public static final UntrustworthyData.Validator<Object, Object> VALIDATOR = (UntrustworthyData.Validator<Object, Object>) data -> data;
    public static final BiFunction<Integer, Integer, Integer> ENCRYPT = (data , key) -> data + key;
    public static final BiFunction<Integer, Integer, Integer> DECRYPT = (data , key) -> data - key;

    @InitiatingFlow
    @StartableByRPC
    public static class Initiator extends FlowLogic<SignedTransaction> {
        private static final int ENCRYPTED_VALUE_TWO = 44;
        private static final int P = 23;
        private static final int G = 5;
        private int diffieKey;

        private int encryptedSum;
        private final Party otherParty;

        private final Step GENERATING_TRANSACTION = new Step("Generating transaction based on new input.");
        private final Step VERIFYING_TRANSACTION = new Step("Verifying contract constraints.");
        private final Step SIGNING_TRANSACTION = new Step("Signing transaction with our private key.");
        private final Step GATHERING_SIGS = new Step("Gathering the counterparty's signature.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return CollectSignaturesFlow.Companion.tracker();
            }
        };
        private final Step FINALISING_TRANSACTION = new Step("Obtaining notary signature and recording transaction.") {
            @Override
            public ProgressTracker childProgressTracker() {
                return FinalityFlow.Companion.tracker();
            }
        };

        private final ProgressTracker progressTracker = new ProgressTracker(
                GENERATING_TRANSACTION,
                VERIFYING_TRANSACTION,
                SIGNING_TRANSACTION,
                GATHERING_SIGS,
                FINALISING_TRANSACTION
        );

        public Initiator(Party otherParty) {
            this.otherParty = otherParty;
        }

        @Override
        public ProgressTracker getProgressTracker() {
            return progressTracker;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {
            // Obtain a reference to the notary we want to use.
            final Party notary = getServiceHub().getNetworkMapCache().getNotaryIdentities().get(0);

            FlowSession otherPartySession = initiateFlow(otherParty);

            int a = ThreadLocalRandom.current().nextInt(10);
            System.out.println("*** a: " + a);
            int A = (int) (Math.pow(G, a) % P);
            System.out.println("*** A: " + A);
            int B = (int) otherPartySession.sendAndReceive(Integer.class, A).unwrap(VALIDATOR);
            System.out.println("*** B: " + B);
            diffieKey = (int) (Math.pow(B, a) % P);
            System.out.println("*** Diffie Key: " + diffieKey);
            int doubleEncryptedOperand = ENCRYPT.apply(ENCRYPTED_VALUE_TWO, diffieKey);
            System.out.println("*** Double encrypted operand: " + doubleEncryptedOperand);
            int doubleEncryptedSum = (int) otherPartySession.sendAndReceive(Integer.class, doubleEncryptedOperand).unwrap(VALIDATOR);
            System.out.println("*** Double encrypted sum:" + doubleEncryptedSum);
            encryptedSum = DECRYPT.apply(doubleEncryptedSum, diffieKey);
            System.out.println("*** Encrypted sum:" + encryptedSum);

            // Stage 1.
            progressTracker.setCurrentStep(GENERATING_TRANSACTION);
            // Generate an unsigned transaction.
            Party me = getServiceHub().getMyInfo().getLegalIdentities().get(0);
            SummationState summationState = new SummationState(encryptedSum, me, otherParty, new UniqueIdentifier());
            final Command<SummationContract.Commands.Create> txCommand = new Command<>(
                    new SummationContract.Commands.Create(),
                    ImmutableList.of(summationState.getInitiator().getOwningKey(), summationState.getAcceptor().getOwningKey()));
            final TransactionBuilder txBuilder = new TransactionBuilder(notary)
                    .addOutputState(summationState, SUMMATION_CONTRACT_ID)
                    .addCommand(txCommand);

            // Stage 2.
            progressTracker.setCurrentStep(VERIFYING_TRANSACTION);
            // Verify that the transaction is valid.
            txBuilder.verify(getServiceHub());

            // Stage 3.
            progressTracker.setCurrentStep(SIGNING_TRANSACTION);
            // Sign the transaction.
            final SignedTransaction partSignedTx = getServiceHub().signInitialTransaction(txBuilder);

            // Stage 4.
            progressTracker.setCurrentStep(GATHERING_SIGS);
            // Send the state to the counterparty, and receive it back with their signature.
            final SignedTransaction fullySignedTx = subFlow(
                    new CollectSignaturesFlow(partSignedTx, ImmutableSet.of(otherPartySession), CollectSignaturesFlow.Companion.tracker()));

            // Stage 5.
            progressTracker.setCurrentStep(FINALISING_TRANSACTION);
            // Notarise and record the transaction in both parties' vaults.
            return subFlow(new FinalityFlow(fullySignedTx));
        }
    }

    @InitiatedBy(Initiator.class)
    public static class Acceptor extends FlowLogic<SignedTransaction> {
        private static final int ENCRYPTION_KEY = 42;
        private static final int SECOND_SUMMATION_OPERAND = 3;
        private static final int P = 23;
        private static final int G = 5;
        private int diffieKey;


        private final FlowSession otherPartyFlow;

        public Acceptor(FlowSession otherPartyFlow) {
            this.otherPartyFlow = otherPartyFlow;
        }

        @Suspendable
        @Override
        public SignedTransaction call() throws FlowException {

            int A = (int) otherPartyFlow.receive(Integer.class).unwrap(VALIDATOR);

            System.out.println("*** A:" + A);
            int b = ThreadLocalRandom.current().nextInt(10);
            System.out.println("*** b:" + b);
            int B = (int) (Math.pow(G, b) % P);
            System.out.println("*** B:" + B);
            diffieKey = (int) Math.pow(A, b) % P;
            System.out.println("*** Diffie Key:" + diffieKey);

            int doubleEncryptedOperand = (int) otherPartyFlow.sendAndReceive(Integer.class, B).unwrap(VALIDATOR);

            System.out.println("*** Double encrypted operand: " + doubleEncryptedOperand);
            int encryptedOperand = DECRYPT.apply(doubleEncryptedOperand, diffieKey);
            System.out.println("*** Encrypted operand:" + encryptedOperand);
            int decryptedOperand = DECRYPT.apply(encryptedOperand, ENCRYPTION_KEY);
            System.out.println("*** Decrypted operand: " + decryptedOperand);
            int sum = decryptedOperand + SECOND_SUMMATION_OPERAND;
            System.out.println("*** Sum: " + sum);
            int encryptedSum = ENCRYPT.apply(sum, ENCRYPTION_KEY);
            System.out.println("*** Encrypted sum: " + encryptedSum);
            int doubleEncryptedSUm = ENCRYPT.apply(encryptedSum, diffieKey);
            System.out.println("*** Double encrypted sum: " + doubleEncryptedSUm);

            otherPartyFlow.send(doubleEncryptedSUm);

            class SignTxFlow extends SignTransactionFlow {
                private SignTxFlow(FlowSession otherPartyFlow, ProgressTracker progressTracker) {
                    super(otherPartyFlow, progressTracker);
                }

                @Override
                protected void checkTransaction(SignedTransaction stx) {
                }
            }

            return subFlow(new SignTxFlow(otherPartyFlow, SignTransactionFlow.Companion.tracker()));
        }
    }


}
