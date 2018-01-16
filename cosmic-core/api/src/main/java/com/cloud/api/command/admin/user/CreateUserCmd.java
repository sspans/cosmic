package com.cloud.api.command.admin.user;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.DomainResponse;
import com.cloud.api.response.UserResponse;
import com.cloud.context.CallContext;
import com.cloud.user.Account;
import com.cloud.user.User;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "createUser", group = "User", description = "Creates a user for an account that already exists", responseObject = UserResponse.class,
        requestHasSensitiveInfo = true, responseHasSensitiveInfo = true)
public class CreateUserCmd extends BaseCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(CreateUserCmd.class.getName());

    private static final String s_name = "createuserresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ACCOUNT,
            type = CommandType.STRING,
            required = true,
            description = "Creates the user under the specified account. If no account is specified, the username will be used as the account name.")
    private String accountName;

    @Parameter(name = ApiConstants.DOMAIN_ID,
            type = CommandType.UUID,
            entityType = DomainResponse.class,
            required = true,
            description = "Creates the user under the specified domain. Has to be accompanied with the account parameter")
    private Long domainId;

    @Parameter(name = ApiConstants.EMAIL, type = CommandType.STRING, required = true, description = "email")
    private String email;

    @Parameter(name = ApiConstants.FIRSTNAME, type = CommandType.STRING, required = true, description = "firstname")
    private String firstname;

    @Parameter(name = ApiConstants.LASTNAME, type = CommandType.STRING, required = true, description = "lastname")
    private String lastname;

    @Parameter(name = ApiConstants.PASSWORD,
            type = CommandType.STRING,
            required = true,
            description = "Clear text password (Default hashed to SHA256SALT). If you wish to use any other hashing algorithm, you would need to write a custom authentication " +
                    "adapter See Docs section.")
    private String password;

    @Parameter(name = ApiConstants.TIMEZONE,
            type = CommandType.STRING,
            description = "Specifies a timezone for this command. For more information on the timezone parameter, see Time Zone Format.")
    private String timezone;

    @Parameter(name = ApiConstants.USERNAME, type = CommandType.STRING, required = true, description = "Unique username.")
    private String username;

    @Parameter(name = ApiConstants.USER_ID, type = CommandType.STRING, description = "User UUID, required for adding account from external provisioning system")
    private String userUUID;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() {
        validateParams();
        CallContext.current().setEventDetails("UserName: " + getUserName() + ", FirstName :" + getFirstName() + ", LastName: " + getLastName());
        final User user =
                _accountService.createUser(getUserName(), getPassword(), getFirstName(), getLastName(), getEmail(), getTimezone(), getAccountName(), getDomainId(),
                        getUserUUID());
        if (user != null) {
            final UserResponse response = _responseGenerator.createUserResponse(user);
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create a user");
        }
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        final Account account = CallContext.current().getCallingAccount();
        if ((account == null) || _accountService.isAdmin(account.getId())) {
            if ((domainId != null) && (accountName != null)) {
                final Account userAccount = _responseGenerator.findAccountByNameDomain(accountName, domainId);
                if (userAccount != null) {
                    return userAccount.getId();
                }
            }
        }

        if (account != null) {
            return account.getId();
        }

        return Account.ACCOUNT_ID_SYSTEM; // no account info given, parent this command to SYSTEM so ERROR events are tracked
    }

    /**
     * TODO: this should be done through a validator. for now replicating the validation logic in create account and user
     */
    private void validateParams() {
        if (StringUtils.isEmpty(getPassword())) {
            throw new ServerApiException(ApiErrorCode.PARAM_ERROR, "Empty passwords are not allowed");
        }
    }

    public String getUserName() {
        return username;
    }

    public String getFirstName() {
        return firstname;
    }

    public String getLastName() {
        return lastname;
    }

    public String getPassword() {
        return password;
    }

    public String getEmail() {
        return email;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    public String getTimezone() {
        return timezone;
    }

    public String getAccountName() {
        return accountName;
    }

    public Long getDomainId() {
        return domainId;
    }

    public String getUserUUID() {
        return userUUID;
    }
}
