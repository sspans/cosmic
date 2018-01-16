package com.cloud.api.command;

import com.cloud.api.APICommand;
import com.cloud.api.BaseListCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.command.admin.user.ListUsersCmd;
import com.cloud.api.response.LdapUserResponse;
import com.cloud.api.response.ListResponse;
import com.cloud.api.response.UserResponse;
import com.cloud.ldap.LdapManager;
import com.cloud.ldap.LdapUser;
import com.cloud.ldap.NoLdapUserMatchingQueryException;
import com.cloud.query.QueryService;
import com.cloud.user.Account;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "listLdapUsers", group = "Authentication", responseObject = LdapUserResponse.class, description = "Lists all LDAP Users", since = "4.2.0",
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class LdapListUsersCmd extends BaseListCmd {

    public static final Logger s_logger = LoggerFactory.getLogger(LdapListUsersCmd.class.getName());
    private static final String s_name = "ldapuserresponse";
    @Inject
    private LdapManager _ldapManager;

    @Inject
    private QueryService _queryService;

    @Parameter(name = "listtype",
            type = CommandType.STRING,
            required = false,
            description = "Determines whether all ldap users are returned or just non-cloudstack users")
    private String listType;

    public LdapListUsersCmd() {
        super();
    }

    public LdapListUsersCmd(final LdapManager ldapManager, final QueryService queryService) {
        super();
        _ldapManager = ldapManager;
        _queryService = queryService;
    }

    @Override
    public void execute() throws ServerApiException {
        List<LdapUserResponse> ldapResponses = null;
        final ListResponse<LdapUserResponse> response = new ListResponse<>();
        try {
            final List<LdapUser> users = _ldapManager.getUsers();
            ldapResponses = createLdapUserResponse(users);
        } catch (final NoLdapUserMatchingQueryException ex) {
            ldapResponses = new ArrayList<>();
        } finally {
            response.setResponses(ldapResponses);
            response.setResponseName(getCommandName());
            setResponseObject(response);
        }
    }

    private List<LdapUserResponse> createLdapUserResponse(final List<LdapUser> users) {
        final List<LdapUserResponse> ldapResponses = new ArrayList<>();
        for (final LdapUser user : users) {
            if (getListType().equals("all") || !isACloudstackUser(user)) {
                final LdapUserResponse ldapResponse = _ldapManager.createLdapUserResponse(user);
                ldapResponse.setObjectName("LdapUser");
                ldapResponses.add(ldapResponse);
            }
        }
        return ldapResponses;
    }

    private String getListType() {
        return listType == null ? "all" : listType;
    }

    private boolean isACloudstackUser(final LdapUser ldapUser) {
        final ListResponse<UserResponse> response = _queryService.searchForUsers(new ListUsersCmd());
        final List<UserResponse> cloudstackUsers = response.getResponses();
        if (cloudstackUsers != null && cloudstackUsers.size() != 0) {
            for (final UserResponse cloudstackUser : response.getResponses()) {
                if (ldapUser.getUsername().equals(cloudstackUser.getUsername())) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }
}
