package com.cloud.api.command;

import com.cloud.api.APICommand;
import com.cloud.api.BaseListCmd;
import com.cloud.api.Parameter;
import com.cloud.api.response.LdapUserResponse;
import com.cloud.api.response.ListResponse;
import com.cloud.ldap.LdapManager;
import com.cloud.ldap.LdapUser;
import com.cloud.ldap.NoLdapUserMatchingQueryException;
import com.cloud.user.Account;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "searchLdap", group = "Authentication", responseObject = LdapUserResponse.class, description = "Searches LDAP based on the username attribute", since = "4.2.0",
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class LdapUserSearchCmd extends BaseListCmd {

    public static final Logger s_logger = LoggerFactory.getLogger(LdapUserSearchCmd.class.getName());
    private static final String s_name = "ldapuserresponse";
    @Inject
    private LdapManager _ldapManager;

    @Parameter(name = "query", type = CommandType.STRING, entityType = LdapUserResponse.class, required = true, description = "query to search using")
    private String query;

    public LdapUserSearchCmd() {
        super();
    }

    public LdapUserSearchCmd(final LdapManager ldapManager) {
        super();
        _ldapManager = ldapManager;
    }

    @Override
    public void execute() {
        final ListResponse<LdapUserResponse> response = new ListResponse<>();
        List<LdapUser> users = null;

        try {
            users = _ldapManager.searchUsers(query);
        } catch (final NoLdapUserMatchingQueryException e) {
            s_logger.debug(e.getMessage());
        }

        final List<LdapUserResponse> ldapUserResponses = createLdapUserResponse(users);

        response.setResponses(ldapUserResponses);
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }

    private List<LdapUserResponse> createLdapUserResponse(final List<LdapUser> users) {
        final List<LdapUserResponse> ldapUserResponses = new ArrayList<>();
        if (users != null) {
            for (final LdapUser user : users) {
                final LdapUserResponse ldapUserResponse = _ldapManager.createLdapUserResponse(user);
                ldapUserResponse.setObjectName("LdapUser");
                ldapUserResponses.add(ldapUserResponse);
            }
        }
        return ldapUserResponses;
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
