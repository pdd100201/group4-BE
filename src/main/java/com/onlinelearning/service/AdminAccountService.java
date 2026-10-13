package com.onlinelearning.service;

import com.onlinelearning.dto.account.AccountResponse;
import com.onlinelearning.dto.account.AccountStatus;
import com.onlinelearning.dto.account.CreateAccountRequest;
import com.onlinelearning.dto.account.UpdateAccountRequest;
import com.onlinelearning.dto.management.ManagementPage;

public interface AdminAccountService {
    ManagementPage<AccountResponse> list(String query, String roleCode, AccountStatus status, int page, int size);
    AccountResponse detail(long id);
    AccountResponse create(CreateAccountRequest request);
    AccountResponse update(long id, UpdateAccountRequest request);
    AccountResponse updateStatus(long id, AccountStatus status);
}
