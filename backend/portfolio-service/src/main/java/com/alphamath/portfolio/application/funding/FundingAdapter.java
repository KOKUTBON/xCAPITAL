package com.alphamath.portfolio.application.funding;

import com.alphamath.portfolio.domain.execution.BrokerAccount;
import com.alphamath.portfolio.domain.funding.FundingDepositReceipt;
import com.alphamath.portfolio.domain.funding.FundingDepositRequest;
import com.alphamath.portfolio.domain.funding.FundingSource;
import com.alphamath.portfolio.domain.funding.FundingTransferReceipt;
import com.alphamath.portfolio.domain.funding.FundingTransferRequest;
import com.alphamath.portfolio.domain.funding.FundingWithdrawalReceipt;
import com.alphamath.portfolio.domain.funding.FundingWithdrawalRequest;

public interface FundingAdapter {
  boolean supports(String providerId);

  default boolean isSimulation() { return false; }

  FundingDepositReceipt deposit(FundingSource source, FundingDepositRequest request);

  FundingWithdrawalReceipt withdraw(FundingSource source, FundingWithdrawalRequest request);

  FundingTransferReceipt transfer(FundingSource source, BrokerAccount brokerAccount, FundingTransferRequest request);
}
