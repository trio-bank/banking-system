package com.triobank.ledger;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.triobank.ledger.transaction.Channel;
import com.triobank.ledger.transaction.TransactionType;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PostingCommandTest {

    @Test
    void validCommandIsAccepted() {
        assertThatCode(() -> builder("10.50").build()).doesNotThrowAnyException();
    }

    @Test
    void trailingZerosBeyondTwoDecimalsAreFine() {
        assertThatCode(() -> builder("10.5000").build()).doesNotThrowAnyException();
    }

    @Test
    void zeroOrNegativeAmountIsABug() {
        assertThatThrownBy(() -> builder("0.00").build()).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder("-1.00").build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void moreThanTwoDecimalsIsABug() {
        assertThatThrownBy(() -> builder("10.001").build()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingRequiredFieldIsABug() {
        assertThatThrownBy(() -> builder("10.00").title(null).build()).isInstanceOf(NullPointerException.class);
    }

    @Test
    void idempotencyKeyWithoutHashIsABug() {
        assertThatThrownBy(() -> builder("10.00").idempotencyKey("key").build())
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> builder("10.00").requestHash("a".repeat(64)).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static PostingCommand.PostingCommandBuilder builder(String amount) {
        return PostingCommand.builder()
                .type(TransactionType.CASH_DEPOSIT)
                .channel(Channel.ATM)
                .sourceAccountId(UUID.randomUUID())
                .targetAccountId(UUID.randomUUID())
                .amount(new BigDecimal(amount))
                .currency("PLN")
                .title("ATM cash deposit")
                .senderName("Trio Bank")
                .recipientName("Anna Nowak");
    }

}
