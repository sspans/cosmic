package com.cloud.api.command;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ResponseObject.ResponseView;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.AccountResponse;
import com.cloud.api.response.DomainResponse;
import com.cloud.context.CallContext;
import com.cloud.domain.DomainVO;
import com.cloud.ldap.LdapManager;
import com.cloud.ldap.LdapUser;
import com.cloud.ldap.NoLdapUserMatchingQueryException;
import com.cloud.user.Account;
import com.cloud.user.AccountService;
import com.cloud.user.User;
import com.cloud.user.UserAccount;

import javax.inject.Inject;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Map;

import org.bouncycastle.util.encoders.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "ldapCreateAccount", group = "Authentication", description = "Creates an account from an LDAP user", responseObject = AccountResponse.class, since = "4.2.0", requestHasSensitiveInfo =
        false, responseHasSensitiveInfo = false)
public class LdapCreateAccountCmd extends BaseCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(LdapCreateAccountCmd.class.getName());
    private static final String s_name = "createaccountresponse";

    @Inject
    private LdapManager _ldapManager;

    @Parameter(name = ApiConstants.ACCOUNT, type = CommandType.STRING, description = "Creates the user under the specified account. If no account is specified, the username will" +
            " be used as the account name.")
    private String accountName;

    @Parameter(name = ApiConstants.ACCOUNT_TYPE, type = CommandType.SHORT, required = true, description = "Type of the account.  Specify 0 for user, 1 for root admin, and 2 for " +
            "domain admin")
    private Short accountType;

    @Parameter(name = ApiConstants.DOMAIN_ID, type = CommandType.UUID, entityType = DomainResponse.class, description = "Creates the user under the specified domain.")
    private Long domainId;

    @Parameter(name = ApiConstants.TIMEZONE, type = CommandType.STRING, description = "Specifies a timezone for this command. For more information on the timezone parameter, see" +
            " Time Zone Format.")
    private String timezone;

    @Parameter(name = ApiConstants.USERNAME, type = CommandType.STRING, required = true, description = "Unique username.")
    private String username;

    @Parameter(name = ApiConstants.NETWORK_DOMAIN, type = CommandType.STRING, description = "Network domain for the account's networks")
    private String networkDomain;

    @Parameter(name = ApiConstants.ACCOUNT_DETAILS, type = CommandType.MAP, description = "details for account used to store specific parameters")
    private Map<String, String> details;

    @Parameter(name = ApiConstants.ACCOUNT_ID, type = CommandType.STRING, description = "Account UUID, required for adding account from external provisioning system")
    private String accountUUID;

    @Parameter(name = ApiConstants.USER_ID, type = CommandType.STRING, description = "User UUID, required for adding account from external provisioning system")
    private String userUUID;

    public LdapCreateAccountCmd() {
        super();
    }

    public LdapCreateAccountCmd(final LdapManager ldapManager, final AccountService accountService) {
        super();
        _ldapManager = ldapManager;
        _accountService = accountService;
    }

    @Override
    public void execute() throws ServerApiException {
        final CallContext callContext = getCurrentContext();
        final String finalAccountName = getAccountName();
        final Long finalDomainId = getDomainId();
        callContext.setEventDetails("Account Name: " + finalAccountName + ", Domain Id:" + finalDomainId);
        try {
            final LdapUser user = _ldapManager.getUser(username);
            validateUser(user);
            final UserAccount userAccount = createCloudstackUserAccount(user, finalAccountName, finalDomainId);
            if (userAccount != null) {
                final AccountResponse response = _responseGenerator.createUserAccountResponse(ResponseView.Full, userAccount);
                response.setResponseName(getCommandName());
                setResponseObject(response);
            } else {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create a user account");
            }
        } catch (final NoLdapUserMatchingQueryException e) {
            throw new ServerApiException(ApiErrorCode.RESOURCE_UNAVAILABLE_ERROR, "No LDAP user exists with the username of " + username);
        }
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }

    CallContext getCurrentContext() {
        return CallContext.current();
    }

    private String getAccountName() {
        String name = accountName;
        if (accountName == null) {
            name = username;
        }
        return name;
    }

    private Long getDomainId() {
        Long id = domainId;
        if (id == null) {
            id = DomainVO.ROOT_DOMAIN;
        }
        return id;
    }

    private boolean validateUser(final LdapUser user) throws ServerApiException {
        if (user.getEmail() == null) {
            throw new ServerApiException(ApiErrorCode.RESOURCE_UNAVAILABLE_ERROR, username + " has no email address set within LDAP");
        }
        if (user.getFirstname() == null) {
            throw new ServerApiException(ApiErrorCode.RESOURCE_UNAVAILABLE_ERROR, username + " has no firstname set within LDAP");
        }
        if (user.getLastname() == null) {
            throw new ServerApiException(ApiErrorCode.RESOURCE_UNAVAILABLE_ERROR, username + " has no lastname set within LDAP");
        }
        return true;
    }

    UserAccount createCloudstackUserAccount(final LdapUser user, final String accountName, final Long domainId) {
        final Account account = _accountService.getActiveAccountByName(accountName, domainId);
        if (account == null) {
            return _accountService.createUserAccount(username, generatePassword(), user.getFirstname(), user.getLastname(), user.getEmail(), timezone, accountName, accountType,
                    domainId, networkDomain, details, accountUUID, userUUID, User.Source.LDAP);
        } else {
            final User newUser = _accountService.createUser(username, generatePassword(), user.getFirstname(), user.getLastname(), user.getEmail(), timezone, accountName, domainId,
                    userUUID, User.Source.LDAP);
            return _accountService.getUserAccountById(newUser.getId());
        }
    }

    private String generatePassword() throws ServerApiException {
        try {
            final SecureRandom randomGen = SecureRandom.getInstance("SHA1PRNG");
            final byte bytes[] = new byte[20];
            randomGen.nextBytes(bytes);
            return new String(Base64.encode(bytes), "UTF-8");
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to generate random password");
        }
    }
}
