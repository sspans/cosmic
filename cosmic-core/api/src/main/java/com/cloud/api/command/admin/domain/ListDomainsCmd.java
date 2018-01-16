package com.cloud.api.command.admin.domain;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.BaseListCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ResponseObject.ResponseView;
import com.cloud.api.response.DomainResponse;
import com.cloud.api.response.ListResponse;
import com.cloud.domain.Domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "listDomains", group = "Domain", description = "Lists domains and provides detailed information for listed domains", responseObject = DomainResponse.class, responseView =
        ResponseView.Restricted, entityType = {Domain.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListDomainsCmd extends BaseListCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(ListDomainsCmd.class.getName());

    private static final String s_name = "listdomainsresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = DomainResponse.class, description = "List domain by domain ID.")
    private Long id;

    @Parameter(name = ApiConstants.LEVEL, type = CommandType.INTEGER, description = "List domains by domain level.")
    private Integer level;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, description = "List domain by domain name.")
    private String domainName;

    @Parameter(name = ApiConstants.LIST_ALL,
            type = CommandType.BOOLEAN,
            description = "If set to false, list only resources belonging to the command's caller; if set to true - list resources that the caller is authorized to see. Default " +
                    "value is false")
    private Boolean listAll;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getId() {
        return id;
    }

    public Integer getLevel() {
        return level;
    }

    public String getDomainName() {
        return domainName;
    }

    public boolean listAll() {
        return listAll == null ? false : listAll;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() {
        final ListResponse<DomainResponse> response = _queryService.searchForDomains(this);
        response.setResponseName(getCommandName());
        this.setResponseObject(response);
    }

    @Override
    public String getCommandName() {
        return s_name;
    }
}
