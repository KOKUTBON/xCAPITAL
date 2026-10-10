package com.alphamath.portfolio.infrastructure.funding;

import com.alphamath.portfolio.application.funding.FundingAdapter;
import com.alphamath.portfolio.domain.execution.BrokerAccount;
import com.alphamath.portfolio.domain.funding.FundingDepositReceipt;
import com.alphamath.portfolio.domain.funding.FundingDepositRequest;
import com.alphamath.portfolio.domain.funding.FundingSource;
import com.alphamath.portfolio.domain.funding.FundingTransactionStatus;
import com.alphamath.portfolio.domain.funding.FundingTransferReceipt;
import com.alphamath.portfolio.domain.funding.FundingTransferRequest;
import com.alphamath.portfolio.domain.funding.FundingWithdrawalReceipt;
import com.alphamath.portfolio.domain.funding.FundingWithdrawalRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
@Order(Ordered.LOWEST_PRECEDENCE)
public class SimulatedFundingAdapter implements FundingAdapter {
  @Override
  public boolean isSimulation() { return true; }
  @Override
  public boolean supports(String providerId) {
    return true;
  }

  @Override
  public FundingDepositReceipt deposit(FundingSource source, FundingDepositRequest request) {
    FundingDepositReceipt receipt = new FundingDepositReceipt();
    receipt.setId(UUID.randomUUID().toString());
    receipt.setSourceId(source.getId());
    receipt.setAmount(request.getAmount() == null ? 0.0 : request.getAmount());
    receipt.setCurrency(source.getCurrency());
    receipt.setStatus(FundingTransactionStatus.COMPLETED.name());
    receipt.setProviderId(source.getProviderId());
    receipt.setProviderReference("sim-dep-" + receipt.getId());
    receipt.setNote("Simulated deposit via " + source.getProviderId());
    receipt.setCreatedAt(Instant.now());
    receipt.setUpdatedAt(receipt.getCreatedAt());
    return receipt;
  }

  @Override
  public FundingWithdrawalReceipt withdraw(FundingSource source, FundingWithdrawalRequest request) {
    FundingWithdrawalReceipt receipt = new FundingWithdrawalReceipt();
    receipt.setId(UUID.randomUUID().toString());
    receipt.setSourceId(source.getId());
    receipt.setAmount(request.getAmount() == null ? 0.0 : request.getAmount());
    receipt.setCurrency(source.getCurrency());
    receipt.setStatus(FundingTransactionStatus.COMPLETED.name());
    receipt.setProviderId(source.getProviderId());
    receipt.setProviderReference("sim-wd-" + receipt.getId());
    receipt.setNote("Simulated withdrawal to " + source.getProviderId());
    receipt.setCreatedAt(Instant.now());
    receipt.setUpdatedAt(receipt.getCreatedAt());
    return receipt;
  }

  @Override
  public FundingTransferReceipt transfer(FundingSource source, BrokerAccount brokerAccount, FundingTransferRequest request) {
    FundingTransferReceipt receipt = new FundingTransferReceipt();
    receipt.setId(UUID.randomUUID().toString());
    receipt.setSourceId(source.getId());
    receipt.setBrokerAccountId(brokerAccount.getId());
    receipt.setDirection(request.getDirection());
    receipt.setAmount(request.getAmount() == null ? 0.0 : request.getAmount());
    receipt.setCurrency(source.getCurrency());
    receipt.setStatus(FundingTransactionStatus.COMPLETED.name());
    receipt.setProviderId(source.getProviderId());
    receipt.setProviderReference("sim-xfer-" + receipt.getId());
    receipt.setNote("Simulated transfer to broker " + brokerAccount.getProviderName());
    receipt.setCreatedAt(Instant.now());
    receipt.setUpdatedAt(receipt.getCreatedAt());
    return receipt;
  }
}
