package be.vercauteren.accounting.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

public record FalcoInboundDocument(
    String id,
    @JsonProperty("received_at") LocalDate receivedAt,
    @JsonProperty("invoice_date") LocalDate invoiceDate,
    @JsonProperty("invoice_due_date") LocalDate invoiceDueDate,
    String amount,
    @JsonProperty("sender_name") String senderName,
    @JsonProperty("sender_vat_number") String senderVatNumber,
    @JsonProperty("sender_participant_identifier") String senderParticipantIdentifier,
    String currency,
    @JsonProperty("payment_reference") String paymentReference,
    @JsonProperty("bank_account_number") String bankAccountNumber,
    @JsonProperty("invoice_reference") String invoiceReference,
    @JsonProperty("is_credit_note") Boolean isCreditNote
) {}
