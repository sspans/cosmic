package com.cloud.api.command.admin.account;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.BaseCmd;
import com.cloud.api.Parameter;
import com.cloud.api.response.AccountResponse;
import com.cloud.api.response.DomainResponse;
import com.cloud.user.Account;
import com.cloud.utils.exception.CloudRuntimeException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "lockAccount", group = "Account",
        description = "This deprecated function used to locks an account. Look for the API DisableAccount instead",
        responseObject = AccountResponse.class,
        entityType = {Account.class},
        requestHasSensitiveInfo = false,
        responseHasSensitiveInfo = true)
public class LockAccountCmd extends BaseCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(LockAccountCmd.class.getName());

    private static final String s_name = "lockaccountresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ACCOUNT, type = CommandType.STRING, required = true, description = "Locks the specified account.")
    private String accountName;

    @Parameter(name = ApiConstants.DOMAIN_ID,
            type = CommandType.UUID,
            entityType = DomainResponse.class,
            required = true,
            description = "Locks the specified account on this domain.")
    private Long domainId;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() {
        throw new CloudRuntimeException("LockAccount does not lock accounts. Its implementation is disabled. Use DisableAccount instead");
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public long getEntityOwnerId() {
        final Account account = _accountService.getActiveAccountByName(getAccountName(), getDomainId());
        if (account != null) {
            return account.getAccountId();
        }

        return Account.ACCOUNT_ID_SYSTEM; // no account info given, parent this command to SYSTEM so ERROR events are tracked
    }

    public String getAccountName() {
        return accountName;
    }

    public Long getDomainId() {
        return domainId;
    }
}
