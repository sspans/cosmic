package com.cloud.api.command;

import com.cloud.api.APICommand;
import com.cloud.api.BaseListCmd;
import com.cloud.api.Parameter;
import com.cloud.api.response.LdapConfigurationResponse;
import com.cloud.api.response.ListResponse;
import com.cloud.ldap.LdapConfigurationVO;
import com.cloud.ldap.LdapManager;
import com.cloud.user.Account;
import com.cloud.utils.Pair;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "listLdapConfigurations", group = "Authentication", responseObject = LdapConfigurationResponse.class, description = "Lists all LDAP configurations", since = "4.2.0",
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class LdapListConfigurationCmd extends BaseListCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(LdapListConfigurationCmd.class.getName());

    private static final String s_name = "ldapconfigurationresponse";

    @Inject
    private LdapManager _ldapManager;

    @Parameter(name = "hostname", type = CommandType.STRING, required = false, description = "Hostname")
    private String hostname;

    @Parameter(name = "port", type = CommandType.INTEGER, required = false, description = "Port")
    private int port;

    public LdapListConfigurationCmd() {
        super();
    }

    public LdapListConfigurationCmd(final LdapManager ldapManager) {
        super();
        _ldapManager = ldapManager;
    }

    @Override
    public void execute() {
        final Pair<List<? extends LdapConfigurationVO>, Integer> result = _ldapManager.listConfigurations(this);
        final List<LdapConfigurationResponse> responses = createLdapConfigurationResponses(result.first());
        final ListResponse<LdapConfigurationResponse> response = new ListResponse<>();
        response.setResponses(responses, result.second());
        response.setResponseName(getCommandName());
        setResponseObject(response);
    }

    private List<LdapConfigurationResponse> createLdapConfigurationResponses(final List<? extends LdapConfigurationVO> configurations) {
        final List<LdapConfigurationResponse> responses = new ArrayList<>();
        for (final LdapConfigurationVO resource : configurations) {
            final LdapConfigurationResponse configurationResponse = _ldapManager.createLdapConfigurationResponse(resource);
            configurationResponse.setObjectName("LdapConfiguration");
            responses.add(configurationResponse);
        }
        return responses;
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        return Account.ACCOUNT_ID_SYSTEM;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(final String hostname) {
        this.hostname = hostname;
    }

    public int getPort() {
        return port;
    }

    public void setPort(final int port) {
        this.port = port;
    }
}
