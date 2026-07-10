package com.homelab.accountservice.controller;

import com.homelab.accountservice.model.Account;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.Map;
import java.io.IOException;

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
    public ResponseEntity<?> createAccount(@RequestBody Account account) {
    	if (account.getAccountNumber() == null || account.getAccountNumber().isBlank()) {
        	return ResponseEntity.badRequest().body("accountNumber tidak boleh kosong");
    	}
    	if (account.getBalance() < 0) {
        	return ResponseEntity.badRequest().body("balance tidak boleh negatif");
   	}
    	accounts.put(account.getAccountNumber(), account);
    	return ResponseEntity.ok(account);
    }

    @DeleteMapping("/{accountNumber}")
    public String deleteAccount(@PathVariable String accountNumber) {
        accounts.remove(accountNumber);
        return "Akun " + accountNumber + " berhasil dihapus";
    }

    @GetMapping("/ping/{host}")
    public String pingHost(@PathVariable String host) throws IOException {
        ProcessBuilder pb = new ProcessBuilder("ping", "-c", "1", host);
	pb.start();
	return "Pinging " + host;
    }
}
