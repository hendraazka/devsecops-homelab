package com.homelab.accountservice.controller;

import com.homelab.accountservice.model.Account;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final Map<String, Account> accounts = new HashMap<>();

    public AccountController() {
        accounts.put("1001", new Account("1001", "Budi Santoso", 5000000));
        accounts.put("1002", new Account("1002", "Siti Aminah", 12500000));
    }

    @GetMapping("/{accountNumber}")
    public Account getAccount(@PathVariable String accountNumber) {
        return accounts.get(accountNumber);
    }

    @GetMapping
    public Map<String, Account> getAllAccounts() {
        return accounts;
    }

    @PostMapping
    public Account createAccount(@RequestBody Account account) {
        accounts.put(account.getAccountNumber(), account);
        return account;
    }
}
