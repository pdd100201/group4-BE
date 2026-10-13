package com.onlinelearning.controller;

import com.onlinelearning.dto.account.AccountResponse;
import com.onlinelearning.dto.account.AccountStatus;
import com.onlinelearning.dto.account.CreateAccountRequest;
import com.onlinelearning.dto.account.UpdateAccountRequest;
import com.onlinelearning.dto.account.UpdateAccountStatusRequest;
import com.onlinelearning.dto.management.ManagementPage;
import com.onlinelearning.service.AdminAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/accounts")
@RequiredArgsConstructor
public class AdminAccountController {
    private final AdminAccountService service;

    @GetMapping
    public ManagementPage<AccountResponse> list(@RequestParam(required = false) String q,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(q, role, status, page, size);
    }

    @GetMapping("/{id}")
    public AccountResponse detail(@PathVariable long id) { return service.detail(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public AccountResponse update(@PathVariable long id,
            @Valid @RequestBody UpdateAccountRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public AccountResponse updateStatus(@PathVariable long id,
            @Valid @RequestBody UpdateAccountStatusRequest request) {
        return service.updateStatus(id, request.status());
    }
}
