package com.example.account.service;

import com.example.account.domain.Account;
import com.example.account.domain.AccountUser;
import com.example.account.domain.Transaction;
import com.example.account.dto.TransactionDto;
import com.example.account.exception.AccountException;
import com.example.account.repository.AccountRepository;
import com.example.account.repository.AccountUserRepository;
import com.example.account.repository.TransactionRepository;
import com.example.account.type.AccountStatus;
import com.example.account.type.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.example.account.type.AccountStatus.IN_USE;
import static com.example.account.type.TransactionResultType.F;
import static com.example.account.type.TransactionResultType.S;
import static com.example.account.type.TransactionType.CANCEL;
import static com.example.account.type.TransactionType.USE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {
    private static final long USE_AMOUNT = 200L;
    private static final long CANCEL_AMOUNT = 200L;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountUserRepository accountUserRepository;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void successUseBalance() {
        // given
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(IN_USE)
                .balance(10000L)
                .accountNumber("1000000000")
                .build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));
        given(transactionRepository.save(any()))
                .willReturn(Transaction.builder()
                        .account(account)
                        .transactionType(USE)
                        .transactionResultType(S)
                        .transactionId("transactionId")
                        .transactedAt(LocalDateTime.now())
                        .amount(1000L)
                        .balanceSnapShot(9000L)
                        .build());
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);


        // when
        TransactionDto transactionDto = transactionService.useBalance(1L, "1000000000", USE_AMOUNT);

        // then
        verify(transactionRepository, times(1)).save(captor.capture());
        assertEquals(USE_AMOUNT, captor.getValue().getAmount());
        assertEquals(9800L, captor.getValue().getBalanceSnapShot());
        assertEquals(S, transactionDto.getTransactionResultType());
        assertEquals(USE, transactionDto.getTransactionType());
        assertEquals(9000L, transactionDto.getBalanceSnapShot());
        assertEquals(1000L, transactionDto.getAmount());
    }

    @Test
    @DisplayName("해당 유저 없음 - 잔액 사용 실패")
    void useBalance_UserNotFound() {
        // given
        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.empty());

        // when
        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L, "1000000000", 1000L));

        // then
        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("해당 계좌 없음 - 잔액 사용 실패")
    void deleteAccount_AccountNotFound() {
        // given
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.empty());

        // when
        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L, "1000000000", 1000L));

        // then
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("계좌 소유주 다름 - 잔액 사용 실패")
    void deleteAccountFailed_UserUnmatch() {
        // given
        AccountUser pobi = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        AccountUser harry = AccountUser.builder()
                .id(13L)
                .name("Harry")
                .build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(pobi));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(Account.builder()
                        .accountUser(harry)
                        .balance(0L)
                        .accountNumber("1000000012")
                        .build()
                ));

        // when
        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L, "1234567890", 1000L));

        // then
        assertEquals(ErrorCode.USER_ACCOUNT_UNMATCH, exception.getErrorCode());
    }

    @Test
    @DisplayName("이미 해지 계좌 - 잔액 사용 실패")
    void deleteAccountFailed_AlreadyUnregistered() {
        // given
        AccountUser pobi = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(pobi));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(Account.builder()
                        .accountUser(pobi)
                        .accountStatus(AccountStatus.UNREGISTERED)
                        .balance(0L)
                        .accountNumber("1000000012")
                        .build()
                ));

        // when
        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L, "1234567890", 1000L));

        // then
        assertEquals(ErrorCode.ACCOUNT_ALREADY_UNREGISTERED, exception.getErrorCode());
    }

    @Test
    @DisplayName("거래 금액 잔액 초과 - 잔액 사용 실패")
    void useBalance_ExceedAmount() {
        // given
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(IN_USE)
                .balance(100L)
                .accountNumber("1000000000")
                .build();

        given(accountUserRepository.findById(anyLong()))
                .willReturn(Optional.of(user));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));

        // when
        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.useBalance(1L, "1234567890", 1000L));


        // then
        assertEquals(ErrorCode.AMOUNT_EXCEED_BALANCE, exception.getErrorCode());
        verify(transactionRepository, times(0)).save(any());
    }

    @Test
    @DisplayName("실패 트랜잭션 저장 성공")
    void saveFailedUseTransaction() {
        // given
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(IN_USE)
                .balance(10000L)
                .accountNumber("1000000000")
                .build();

        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));
        given(transactionRepository.save(any()))
                .willReturn(Transaction.builder()
                        .account(account)
                        .transactionType(USE)
                        .transactionResultType(S)
                        .transactionId("transactionId")
                        .transactedAt(LocalDateTime.now())
                        .amount(1000L)
                        .balanceSnapShot(9000L)
                        .build());
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);


        // when
        transactionService.saveFailedUseTransaction("1000000000", USE_AMOUNT);

        // then
        verify(transactionRepository, times(1)).save(captor.capture());
        assertEquals(USE_AMOUNT, captor.getValue().getAmount());
        assertEquals(10000L, captor.getValue().getBalanceSnapShot());
        assertEquals(F, captor.getValue().getTransactionResultType());
    }

    @Test
    void successCancelBalance() {
        // given
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        Account account = Account.builder()
                .accountUser(user)
                .accountStatus(IN_USE)
                .balance(10000L)
                .accountNumber("1000000000")
                .build();
        Transaction transaction = Transaction.builder()
                .account(account)
                .transactionType(USE)
                .transactionResultType(S)
                .transactionId("transactionId")
                .transactedAt(LocalDateTime.now())
                .amount(CANCEL_AMOUNT)
                .balanceSnapShot(9000L)
                .build();

        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(transaction));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));
        given(transactionRepository.save(any()))
                .willReturn(Transaction.builder()
                        .account(account)
                        .transactionType(CANCEL)
                        .transactionResultType(S)
                        .transactionId("transactionIdForCancel")
                        .transactedAt(LocalDateTime.now())
                        .amount(CANCEL_AMOUNT)
                        .balanceSnapShot(10000L)
                        .build());
        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);

        // when
        TransactionDto transactionDto = transactionService.cancelBalance("transactionId", "1000000000", CANCEL_AMOUNT);

        // then
        verify(transactionRepository, times(1)).save(captor.capture());
        assertEquals(CANCEL_AMOUNT, captor.getValue().getAmount());
        assertEquals(10000L + CANCEL_AMOUNT, captor.getValue().getBalanceSnapShot());
        assertEquals(S, transactionDto.getTransactionResultType());
        assertEquals(CANCEL, transactionDto.getTransactionType());
        assertEquals(10000L, transactionDto.getBalanceSnapShot());
        assertEquals(CANCEL_AMOUNT, transactionDto.getAmount());
    }

    @Test
    @DisplayName("해당 계좌 없음 - 잔액 사용 취소 실패")
    void cancelTransaction_AccountNotFound() {
        // given
        Transaction transaction = Transaction.builder().build();
        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(transaction));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.empty());

        // when
        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("transactionId", "1000000000", 1000L));

        // then
        assertEquals(ErrorCode.ACCOUNT_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("원 사용 거래 없음 - 잔액 사용 취소 실패")
    void cancelTransaction_TransactionNotFound() {
        // given
        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.empty());

        // when
        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("transactionId", "1000000000", 1000L));

        // then
        assertEquals(ErrorCode.TRANSACTION_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    @DisplayName("거래와 계좌 간 매칭 실패 - 잔액 사용 취소 실패")
    void cancelTransaction_TransactionAccountUnmatch() {
        // given
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        Account account = Account.builder()
                .id(1L)
                .accountUser(user)
                .accountStatus(IN_USE)
                .balance(10000L)
                .accountNumber("1000000012")
                .build();
        Account accountNotUse = Account.builder()
                .id(2L)
                .accountUser(user)
                .accountStatus(IN_USE)
                .balance(10000L)
                .accountNumber("1000000013")
                .build();
        Transaction transaction = Transaction.builder()
                .account(account)
                .transactionType(USE)
                .transactionResultType(S)
                .transactionId("transactionId")
                .transactedAt(LocalDateTime.now())
                .amount(CANCEL_AMOUNT)
                .balanceSnapShot(9000L)
                .build();
        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(transaction));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(accountNotUse));

        // when
        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("transactionId", "1000000000", CANCEL_AMOUNT));

        // then
        assertEquals(ErrorCode.TRANSACTION_ACCOUNT_UNMATCH, exception.getErrorCode());
    }

    @Test
    @DisplayName("거래금액과 취소금액 다름 - 잔액 사용 취소 실패")
    void cancelTransaction_CancelMustFully() {
        // given
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        Account account = Account.builder()
                .id(1L)
                .accountUser(user)
                .accountStatus(IN_USE)
                .balance(10000L)
                .accountNumber("1000000012")
                .build();
        Transaction transaction = Transaction.builder()
                .account(account)
                .transactionType(USE)
                .transactionResultType(S)
                .transactionId("transactionId")
                .transactedAt(LocalDateTime.now())
                .amount(CANCEL_AMOUNT + 1000L)
                .balanceSnapShot(9000L)
                .build();
        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(transaction));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));

        // when
        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("transactionId", "1000000000", CANCEL_AMOUNT));

        // then
        assertEquals(ErrorCode.CANCEL_MUST_FULLY, exception.getErrorCode());
    }

    @Test
    @DisplayName("취소는 1년까지만 가능 - 잔액 사용 취소 실패")
    void cancelTransaction_TooOldOrderToCancel() {
        // given
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        Account account = Account.builder()
                .id(1L)
                .accountUser(user)
                .accountStatus(IN_USE)
                .balance(10000L)
                .accountNumber("1000000012")
                .build();
        Transaction transaction = Transaction.builder()
                .account(account)
                .transactionType(USE)
                .transactionResultType(S)
                .transactionId("transactionId")
                .transactedAt(LocalDateTime.now().minusYears(1).minusDays(1))
                .amount(CANCEL_AMOUNT)
                .balanceSnapShot(9000L)
                .build();
        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(transaction));
        given(accountRepository.findByAccountNumber(anyString()))
                .willReturn(Optional.of(account));

        // when
        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.cancelBalance("transactionId", "1000000000", CANCEL_AMOUNT));

        // then
        assertEquals(ErrorCode.TOO_OLD_ORDER_TO_CANCEL, exception.getErrorCode());
    }

    @Test
    void successQueryTransaction() {
        // given
        AccountUser user = AccountUser.builder()
                .id(12L)
                .name("Pobi")
                .build();
        Account account = Account.builder()
                .id(1L)
                .accountUser(user)
                .accountStatus(IN_USE)
                .balance(10000L)
                .accountNumber("1000000012")
                .build();
        Transaction transaction = Transaction.builder()
                .account(account)
                .transactionType(USE)
                .transactionResultType(S)
                .transactionId("transactionId")
                .transactedAt(LocalDateTime.now().minusYears(1).minusDays(1))
                .amount(CANCEL_AMOUNT)
                .balanceSnapShot(9000L)
                .build();
        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.of(transaction));

        // when
        TransactionDto transactionDto = transactionService.queryTransaction("transactionId");

        // then
         assertEquals(USE, transactionDto.getTransactionType());
         assertEquals(S, transactionDto.getTransactionResultType());
         assertEquals(CANCEL_AMOUNT, transactionDto.getAmount());
         assertEquals("transactionId", transactionDto.getTransactionId());
    }

    @Test
    @DisplayName("원 거래 없음 - 거래 조회 실패")
    void queryTransaction_TransactionNotFound() {
        // given
        given(transactionRepository.findByTransactionId(anyString()))
                .willReturn(Optional.empty());

        // when
        AccountException exception = assertThrows(AccountException.class,
                () -> transactionService.queryTransaction("transactionId"));

        // then
        assertEquals(ErrorCode.TRANSACTION_NOT_FOUND, exception.getErrorCode());
    }
}