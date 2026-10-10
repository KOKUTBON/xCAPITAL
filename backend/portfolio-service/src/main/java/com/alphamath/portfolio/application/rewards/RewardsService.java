package com.alphamath.portfolio.application.rewards;

import com.alphamath.portfolio.domain.banking.BankingTransferStatus;
import com.alphamath.portfolio.domain.funding.FundingTransactionStatus;
import com.alphamath.portfolio.domain.rewards.RewardEnrollment;
import com.alphamath.portfolio.domain.rewards.RewardEnrollmentStatus;
import com.alphamath.portfolio.domain.rewards.RewardOffer;
import com.alphamath.portfolio.infrastructure.persistence.BankingAccountEntity;
import com.alphamath.portfolio.infrastructure.persistence.BankingAccountRepository;
import com.alphamath.portfolio.infrastructure.persistence.BankingTransferEntity;
import com.alphamath.portfolio.infrastructure.persistence.BankingTransferRepository;
import com.alphamath.portfolio.infrastructure.persistence.FundingDepositEntity;
import com.alphamath.portfolio.infrastructure.persistence.FundingDepositRepository;
import com.alphamath.portfolio.infrastructure.persistence.RewardEnrollmentEntity;
import com.alphamath.portfolio.infrastructure.persistence.RewardEnrollmentRepository;
import com.alphamath.portfolio.infrastructure.persistence.RewardOfferEntity;
import com.alphamath.portfolio.infrastructure.persistence.RewardOfferRepository;
import com.alphamath.portfolio.security.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class RewardsService {
  private static final String STATUS_ACTIVE = "ACTIVE";

  private final RewardOfferRepository offers;
  private final RewardEnrollmentRepository enrollments;
  private final FundingDepositRepository deposits;
  private final BankingTransferRepository transfers;
  private final BankingAccountRepository bankingAccounts;
  private final TenantContext tenantContext;
  private final String defaultCurrency;

  public RewardsService(RewardOfferRepository offers,
                        RewardEnrollmentRepository enrollments,
                        FundingDepositRepository deposits,
                        BankingTransferRepository transfers,
                        BankingAccountRepository bankingAccounts,
                        TenantContext tenantContext,
                        @Value("${alphamath.banking.defaultCurrency:USD}") String defaultCurrency) {
    this.offers = offers;
    this.enrollments = enrollments;
    this.deposits = deposits;
    this.transfers = transfers;
    this.bankingAccounts = bankingAccounts;
    this.tenantContext = tenantContext;
    this.defaultCurrency = defaultCurrency == null || defaultCurrency.isBlank() ? "USD" : defaultCurrency;
  }

  public List<RewardOffer> listOffers() {
    List<RewardOffer> out = new ArrayList<>();
    for (RewardOfferEntity entity : offers.findByStatusOrderByCreatedAtDesc(STATUS_ACTIVE)) {
      out.add(toDto(entity));
    }
    return out;
  }

  public List<RewardEnrollment> listEnrollments(String userId) {
    List<RewardEnrollment> out = new ArrayList<>();
    String orgId = tenantContext.getOrgId();
    List<RewardEnrollmentEntity> rows = orgId == null
        ? enrollments.findByUserIdOrderByCreatedAtDesc(userId)
        : enrollments.findByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId);
    for (RewardEnrollmentEntity entity : rows) {
      out.add(toDto(entity));
    }
    return out;
  }

  public RewardEnrollment enroll(String userId, String offerId) {
    RewardOfferEntity offer = offers.findById(offerId).orElse(null);
    if (offer == null || !STATUS_ACTIVE.equalsIgnoreCase(offer.getStatus())) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer not found");
    }
    RewardEnrollmentEntity entity = new RewardEnrollmentEntity();
    entity.setId(UUID.randomUUID().toString());
    entity.setOfferId(offerId);
    entity.setUserId(userId);
    entity.setOrgId(tenantContext.getOrgId());
    entity.setStatus(RewardEnrollmentStatus.PENDING.name());
    entity.setCreatedAt(Instant.now());
    enrollments.save(entity);
    return toDto(entity);
  }

  @Transactional
  public RewardEnrollment evaluate(String userId, String enrollmentId) {
    RewardEnrollmentEntity enrollment = enrollments.findById(enrollmentId).orElse(null);
    String orgId = tenantContext.getOrgId();
    if (enrollment == null || !userId.equals(enrollment.getUserId()) || (orgId != null && !orgId.equals(enrollment.getOrgId()))) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Enrollment not found");
    }
    if (!RewardEnrollmentStatus.PENDING.name().equals(enrollment.getStatus())) {
      return toDto(enrollment);
    }
    RewardOfferEntity offer = offers.findById(enrollment.getOfferId()).orElse(null);
    if (offer == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Offer not found");
    }

    Instant since = enrollment.getCreatedAt();
    double required = offer.getMinDeposit();
    if (hasQualifiedDeposit(userId, since, required)) {
      enrollment.setStatus(RewardEnrollmentStatus.QUALIFIED.name());
      enrollment.setQualifiedAt(Instant.now());
      enrollments.save(enrollment);

      payout(userId, offer);
      enrollment.setStatus(RewardEnrollmentStatus.PAID.name());
      enrollment.setPaidAt(Instant.now());
      enrollments.save(enrollment);
    }

    return toDto(enrollment);
  }

  public List<RewardEnrollment> evaluatePending() {
    List<RewardEnrollment> out = new ArrayList<>();
    String orgId = tenantContext.getOrgId();
    List<RewardEnrollmentEntity> rows = orgId == null
        ? enrollments.findByStatusOrderByCreatedAtDesc(RewardEnrollmentStatus.PENDING.name())
        : enrollments.findByOrgIdAndStatusOrderByCreatedAtDesc(orgId, RewardEnrollmentStatus.PENDING.name());
    for (RewardEnrollmentEntity enrollment : rows) {
      try {
        out.add(evaluate(enrollment.getUserId(), enrollment.getId()));
      } catch (ResponseStatusException ignored) {
      }
    }
    return out;
  }

  private boolean hasQualifiedDeposit(String userId, Instant since, double required) {
    String orgId = tenantContext.getOrgId();
    List<FundingDepositEntity> depositRows = orgId == null
        ? deposits.findByUserIdOrderByCreatedAtDesc(userId)
        : deposits.findByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId);
    for (FundingDepositEntity entity : depositRows) {
      if (entity.getCreatedAt() != null && entity.getCreatedAt().isBefore(since)) continue;
      if (!FundingTransactionStatus.COMPLETED.name().equalsIgnoreCase(entity.getStatus())) continue;
      if (entity.getAmount() >= required) return true;
    }

    List<BankingTransferEntity> transferRows = orgId == null
        ? transfers.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, 200))
        : transfers.findByUserIdAndOrgIdOrderByCreatedAtDesc(userId, orgId, PageRequest.of(0, 200));
    for (BankingTransferEntity entity : transferRows) {
      if (entity.getCreatedAt() != null && entity.getCreatedAt().isBefore(since)) continue;
      if (!BankingTransferStatus.COMPLETED.name().equalsIgnoreCase(entity.getStatus())) continue;
      if (!"TO_INVESTING".equalsIgnoreCase(entity.getDirection())) continue;
      if (entity.getAmount() >= required) return true;
    }

    return false;
  }

  private void payout(String userId, RewardOfferEntity offer) {
    BankingAccountEntity account = bankingAccounts.findByUserId(userId).orElseGet(() -> {
      BankingAccountEntity created = new BankingAccountEntity();
      created.setId(UUID.randomUUID().toString());
      created.setUserId(userId);
      created.setOrgId(tenantContext.getOrgId());
      created.setStatus("ACTIVE");
      created.setCash(0.0);
      created.setCurrency(defaultCurrency.toUpperCase(Locale.US));
      created.setCreatedAt(Instant.now());
      created.setUpdatedAt(created.getCreatedAt());
      return bankingAccounts.save(created);
    });
    account.setCash(account.getCash() + offer.getBonusAmount());
    account.setUpdatedAt(Instant.now());
    bankingAccounts.save(account);
  }

  private RewardOffer toDto(RewardOfferEntity entity) {
    RewardOffer out = new RewardOffer();
    out.setId(entity.getId());
    out.setName(entity.getName());
    out.setDescription(entity.getDescription());
    out.setMinDeposit(entity.getMinDeposit());
    out.setBonusAmount(entity.getBonusAmount());
    out.setCurrency(entity.getCurrency());
    out.setStatus(entity.getStatus());
    out.setCreatedAt(entity.getCreatedAt());
    return out;
  }

  private RewardEnrollment toDto(RewardEnrollmentEntity entity) {
    RewardEnrollment out = new RewardEnrollment();
    out.setId(entity.getId());
    out.setOfferId(entity.getOfferId());
    out.setUserId(entity.getUserId());
    out.setStatus(RewardEnrollmentStatus.valueOf(entity.getStatus()));
    out.setQualifiedAt(entity.getQualifiedAt());
    out.setPaidAt(entity.getPaidAt());
    out.setCreatedAt(entity.getCreatedAt());
    return out;
  }
}
