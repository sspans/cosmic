package com.cloud.api.command.admin.domain;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.BaseListCmd;
import com.cloud.api.Parameter;
import com.cloud.api.response.DomainResponse;
import com.cloud.api.response.ListResponse;
import com.cloud.domain.Domain;
import com.cloud.utils.Pair;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "listDomainChildren", group = "Domain", description = "Lists all children domains belonging to a specified domain", responseObject = DomainResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListDomainChildrenCmd extends BaseListCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(ListDomainChildrenCmd.class.getName());

    private static final String s_name = "listdomainchildrenresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = DomainResponse.class, description = "list children domain by parent domain ID.")
    private Long id;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, description = "list children domains by name")
    private String domainName;

    @Parameter(name = ApiConstants.IS_RECURSIVE,
            type = CommandType.BOOLEAN,
            description = "to return the entire tree, use the value \"true\". To return the first level children, use the value \"false\".")
    private Boolean recursive;

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

    public String getDomainName() {
        return domainName;
    }

    public boolean listAll() {
        return listAll == null ? false : listAll;
    }

    public boolean isRecursive() {
        return recursive == null ? false : recursive;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public void execute() {
        final Pair<List<? extends Domain>, Integer> result = _domainService.searchForDomainChildren(this);
        final ListResponse<DomainResponse> response = new ListResponse<>();
        final List<DomainResponse> domainResponses = new ArrayList<>();
        for (final Domain domain : result.first()) {
            final DomainResponse domainResponse = _responseGenerator.createDomainResponse(domain);
            domainResponse.setObjectName("domain");
            domainResponses.add(domainResponse);
        }

        response.setResponses(domainResponses, result.second());
        response.setResponseName(getCommandName());
        this.setResponseObject(response);
    }

    @Override
    public String getCommandName() {
        return s_name;
    }
}
