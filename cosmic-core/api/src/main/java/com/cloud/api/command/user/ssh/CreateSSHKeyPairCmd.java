package com.cloud.api.command.user.ssh;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.BaseCmd;
import com.cloud.api.Parameter;
import com.cloud.api.response.CreateSSHKeyPairResponse;
import com.cloud.api.response.DomainResponse;
import com.cloud.api.response.ProjectResponse;
import com.cloud.context.CallContext;
import com.cloud.user.SSHKeyPair;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "createSSHKeyPair", group = "SSH", description = "Create a new keypair and returns the private key", responseObject = CreateSSHKeyPairResponse.class, entityType =
        {SSHKeyPair.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = true)
public class CreateSSHKeyPairCmd extends BaseCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(CreateSSHKeyPairCmd.class.getName());
    private static final String s_name = "createsshkeypairresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, required = true, description = "Name of the keypair")
    private String name;

    //Owner information
    @Parameter(name = ApiConstants.ACCOUNT, type = CommandType.STRING, description = "an optional account for the ssh key. Must be used with domainId.")
    private String accountName;

    @Parameter(name = ApiConstants.DOMAIN_ID,
            type = CommandType.UUID,
            entityType = DomainResponse.class,
            description = "an optional domainId for the ssh key. If the account parameter is used, domainId must also be used.")
    private Long domainId;

    @Parameter(name = ApiConstants.PROJECT_ID, type = CommandType.UUID, entityType = ProjectResponse.class, description = "an optional project for the ssh key")
    private Long projectId;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public String getName() {
        return name;
    }

    public String getAccountName() {
        return accountName;
    }

    public Long getDomainId() {
        return domainId;
    }

    public Long getProjectId() {
        return projectId;
    }

    @Override
    public void execute() {
        final SSHKeyPair r = _mgr.createSSHKeyPair(this);
        final CreateSSHKeyPairResponse response = (CreateSSHKeyPairResponse) _responseGenerator.createSSHKeyPairResponse(r, true);
        response.setResponseName(getCommandName());
        response.setObjectName("keypair");
        setResponseObject(response);
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
        final Long accountId = _accountService.finalyzeAccountId(accountName, domainId, projectId, true);
        if (accountId == null) {
            return CallContext.current().getCallingAccount().getId();
        }

        return accountId;
    }
}
