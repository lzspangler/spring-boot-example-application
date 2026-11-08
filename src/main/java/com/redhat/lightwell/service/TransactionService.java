package com.redhat.lightwell.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import com.redhat.lightwell.exception.InsufficientFundsException;
import com.redhat.lightwell.exception.ResourceNotFoundException;
import com.redhat.lightwell.model.Account;
import com.redhat.lightwell.model.Transaction;
import com.redhat.lightwell.model.TransactionType;
import com.redhat.lightwell.model.dto.TransactionRequest;
import com.redhat.lightwell.model.dto.TransactionResponse;
import com.redhat.lightwell.model.dto.TransferRequest;
import com.redhat.lightwell.repository.AccountRepository;
import com.redhat.lightwell.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;

    public TransactionService(AccountRepository accountRepository,
                              TransactionRepository transactionRepository,
                              AuditService auditService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.auditService = auditService;
    }

    @Transactional
    public TransactionResponse deposit(Long accountId, TransactionRequest request) {
        log.info("Deposit of {} to account {} - {}", request.getAmount(), accountId, request.getDescription());
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

        BigDecimal newBalance = account.getBalance().add(request.getAmount());
        account.setBalance(newBalance);
        accountRepository.save(account);

        Transaction txn = new Transaction(TransactionType.DEPOSIT, request.getAmount(),
                request.getDescription(), account, null, newBalance);
        txn = transactionRepository.save(txn);

        auditService.log("DEPOSIT", "Account", accountId,
                "Deposited " + request.getAmount() + " to account " + account.getAccountNumber());

        return toResponse(txn);
    }

    @Transactional
    public TransactionResponse withdraw(Long accountId, TransactionRequest request) {
        log.info("Withdrawal of {} from account {} - {}", request.getAmount(), accountId, request.getDescription());
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));

        if (account.getBalance().compareTo(request.getAmount()) < 0) {
            log.warn("Insufficient funds in account {}: balance={}, requested={}",
                    account.getAccountNumber(), account.getBalance(), request.getAmount());
            throw new InsufficientFundsException(accountId, request.getAmount(), account.getBalance());
        }

        BigDecimal newBalance = account.getBalance().subtract(request.getAmount());
        account.setBalance(newBalance);
        accountRepository.save(account);

        Transaction txn = new Transaction(TransactionType.WITHDRAWAL, request.getAmount(),
                request.getDescription(), account, null, newBalance);
        txn = transactionRepository.save(txn);

        auditService.log("WITHDRAWAL", "Account", accountId,
                "Withdrew " + request.getAmount() + " from account " + account.getAccountNumber());

        return toResponse(txn);
    }

    @Transactional
    public TransactionResponse transfer(TransferRequest request) {
        log.info("Transfer of {} from account {} to account {} - {}",
                request.getAmount(), request.getSourceAccountId(),
                request.getTargetAccountId(), request.getDescription());
        if (request.getSourceAccountId().equals(request.getTargetAccountId())) {
            throw new IllegalArgumentException("Source and target accounts must be different");
        }

        Account source = accountRepository.findById(request.getSourceAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", request.getSourceAccountId()));
        Account target = accountRepository.findById(request.getTargetAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", request.getTargetAccountId()));

        if (source.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(
                    request.getSourceAccountId(), request.getAmount(), source.getBalance());
        }

        BigDecimal sourceNewBalance = source.getBalance().subtract(request.getAmount());
        source.setBalance(sourceNewBalance);
        accountRepository.save(source);

        BigDecimal targetNewBalance = target.getBalance().add(request.getAmount());
        target.setBalance(targetNewBalance);
        accountRepository.save(target);

        String description = request.getDescription() != null
                ? request.getDescription()
                : "Transfer from " + source.getAccountNumber() + " to " + target.getAccountNumber();

        Transaction sourceTxn = new Transaction(TransactionType.TRANSFER, request.getAmount(),
                description, source, target, sourceNewBalance);
        sourceTxn = transactionRepository.save(sourceTxn);

        Transaction targetTxn = new Transaction(TransactionType.TRANSFER, request.getAmount(),
                description, target, source, targetNewBalance);
        transactionRepository.save(targetTxn);

        auditService.log("TRANSFER", "Account", source.getId(),
                "Transferred " + request.getAmount() + " from " + source.getAccountNumber()
                        + " to " + target.getAccountNumber());
        auditService.log("TRANSFER", "Account", target.getId(),
                "Received " + request.getAmount() + " from " + source.getAccountNumber());

        return toResponse(sourceTxn);
    }

    // readOnly: the stream().map(toResponse) below dereferences the lazy
    // Transaction.account proxy, and the app runs with
    // spring.jpa.open-in-view=false -- without a session here, any account
    // with transaction history answers 500 with LazyInitializationException.
    // deposit/withdraw need write semantics; this method only reads.
    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionHistory(Long accountId) {
        if (!accountRepository.existsById(accountId)) {
            throw new ResourceNotFoundException("Account", accountId);
        }
        return transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private TransactionResponse toResponse(Transaction txn) {
        return new TransactionResponse(
                txn.getId(),
                txn.getTransactionType(),
                txn.getAmount(),
                txn.getDescription(),
                txn.getBalanceAfter(),
                txn.getAccount().getId(),
                txn.getAccount().getAccountNumber(),
                txn.getRelatedAccount() != null ? txn.getRelatedAccount().getId() : null,
                txn.getCreatedAt());
    }
}
