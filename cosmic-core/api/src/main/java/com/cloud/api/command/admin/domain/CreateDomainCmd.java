package com.cloud.api.command.admin.domain;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.DomainResponse;
import com.cloud.context.CallContext;
import com.cloud.domain.Domain;
import com.cloud.user.Account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "createDomain", group = "Domain", description = "Creates a domain", responseObject = DomainResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class CreateDomainCmd extends BaseCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(CreateDomainCmd.class.getName());

    private static final String s_name = "createdomainresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, required = true, description = "creates domain with this name")
    private String domainName;

    @Parameter(name = ApiConstants.PARENT_DOMAIN_ID,
            type = CommandType.UUID,
            entityType = DomainResponse.class,
            description = "assigns new domain a parent domain by domain ID of the parent.  If no parent domain is specied, the ROOT domain is assumed.")
    private Long parentDomainId;

    @Parameter(name = ApiConstants.NETWORK_DOMAIN, type = CommandType.STRING, description = "Network domain for networks in the domain")
    private String networkDomain;

    @Parameter(name = ApiConstants.DOMAIN_ID, type = CommandType.STRING, description = "Domain UUID, required for adding domain from another Region")
    private String domainUUID;

    @Parameter(name = ApiConstants.EMAIL,
            type = CommandType.STRING,
            description = "Email address associated with this domain")
    private String email;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() {
        CallContext.current().setEventDetails("Domain Name: " + getDomainName() + ((getParentDomainId() != null) ? ", Parent DomainId :" + getParentDomainId() : ""));
        final Domain domain = _domainService.createDomain(getDomainName(), getParentDomainId(), getNetworkDomain(), getDomainUUID(), getEmail());
        if (domain != null) {
            final DomainResponse response = _responseGenerator.createDomainResponse(domain);
            response.setResponseName(getCommandName());
            this.setResponseObject(response);
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create domain");
        }
    }

    public String getDomainName() {
        return domainName;
    }

    public Long getParentDomainId() {
        return parentDomainId;
    }

    public String getNetworkDomain() {
        return networkDomain;
    }

    public String getEmail() {
        return email;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    public String getDomainUUID() {
        return domainUUID;
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
