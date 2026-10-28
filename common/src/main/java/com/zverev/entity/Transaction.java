package com.zverev.entity;

import com.zverev.entity.enums.CurrencyCode;
import com.zverev.entity.enums.MerchantCountry;
import com.zverev.entity.enums.TransactionStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true, updatable = false)
    private UUID externalId;

    @Column(name = "card_token", nullable = false, updatable = false)
    private String cardToken;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "currency_code", nullable = false, length = 3, updatable = false)
    private CurrencyCode currencyCode;

    @Column(name = "merchant_name", nullable = false, updatable = false)
    private String merchantName;

    @Enumerated(EnumType.STRING)
    @Column(name = "merchant_country", nullable = false, updatable = false)
    private MerchantCountry merchantCountry;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Alert> alerts = new ArrayList<>();

    @Builder
    private Transaction(UUID externalId,
                        String cardToken,
                        BigDecimal amount,
                        CurrencyCode currencyCode,
                        String merchantName,
                        MerchantCountry merchantCountry,
                        Instant occurredAt,
                        Instant createdAt) {
        this.externalId = externalId;
        this.cardToken = cardToken;
        this.amount = amount;
        this.currencyCode = currencyCode;
        this.merchantName = merchantName;
        this.merchantCountry = merchantCountry;
        this.occurredAt = occurredAt;
        this.createdAt = createdAt;
        this.status = TransactionStatus.NEW;
    }

    public void addAlert(Alert alert) {
        alerts.add(alert);
        alert.setTransaction(this);
    }
}
