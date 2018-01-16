package com.cloud.api.command.admin.resource;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.BaseListCmd;
import com.cloud.api.Parameter;
import com.cloud.api.response.CapacityResponse;
import com.cloud.api.response.ClusterResponse;
import com.cloud.api.response.ListResponse;
import com.cloud.api.response.PodResponse;
import com.cloud.api.response.ZoneResponse;
import com.cloud.capacity.Capacity;
import com.cloud.utils.exception.InvalidParameterValueException;

import java.text.DecimalFormat;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "listCapacity", group = "System", description = "Lists all the system wide capacities.", responseObject = CapacityResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListCapacityCmd extends BaseListCmd {

    public static final Logger s_logger = LoggerFactory.getLogger(ListCapacityCmd.class.getName());
    private static final DecimalFormat s_percentFormat = new DecimalFormat("##.##");

    private static final String s_name = "listcapacityresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ZONE_ID, type = CommandType.UUID, entityType = ZoneResponse.class, description = "lists capacity by the Zone ID")
    private Long zoneId;

    @Parameter(name = ApiConstants.POD_ID, type = CommandType.UUID, entityType = PodResponse.class, description = "lists capacity by the Pod ID")
    private Long podId;

    @Parameter(name = ApiConstants.CLUSTER_ID,
            type = CommandType.UUID,
            entityType = ClusterResponse.class,
            since = "3.0.0",
            description = "lists capacity by the Cluster ID")
    private Long clusterId;

    @Parameter(name = ApiConstants.FETCH_LATEST, type = CommandType.BOOLEAN, since = "3.0.0", description = "recalculate capacities and fetch the latest")
    private Boolean fetchLatest;

    @Parameter(name = ApiConstants.TYPE, type = CommandType.INTEGER, description = "lists capacity by type" + "* CAPACITY_TYPE_MEMORY = 0" + "* CAPACITY_TYPE_CPU = 1"
            + "* CAPACITY_TYPE_STORAGE = 2" + "* CAPACITY_TYPE_STORAGE_ALLOCATED = 3" + "* CAPACITY_TYPE_VIRTUAL_NETWORK_PUBLIC_IP = 4" + "* CAPACITY_TYPE_PRIVATE_IP = 5"
            + "* CAPACITY_TYPE_SECONDARY_STORAGE = 6" + "* CAPACITY_TYPE_VLAN = 7" + "* CAPACITY_TYPE_DIRECT_ATTACHED_PUBLIC_IP = 8" + "* CAPACITY_TYPE_LOCAL_STORAGE = 9.")
    private Integer type;

    @Parameter(name = ApiConstants.SORT_BY, type = CommandType.STRING, since = "3.0.0", description = "Sort the results. Available values: Usage")
    private String sortBy;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getZoneId() {
        return zoneId;
    }

    public Long getPodId() {
        return podId;
    }

    public Long getClusterId() {
        return clusterId;
    }

    public Boolean getFetchLatest() {
        return fetchLatest;
    }

    public Integer getType() {
        return type;
    }

    @Override
    public void execute() {
        List<? extends Capacity> result = null;
        if (getSortBy() != null) {
            result = _mgr.listTopConsumedResources(this);
        } else {
            result = _mgr.listCapacities(this);
        }

        final ListResponse<CapacityResponse> response = new ListResponse<>();
        final List<CapacityResponse> capacityResponses = _responseGenerator.createCapacityResponse(result, s_percentFormat);
        response.setResponses(capacityResponses);
        response.setResponseName(getCommandName());
        this.setResponseObject(response);
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    public String getSortBy() {
        if (sortBy != null) {
            if (sortBy.equalsIgnoreCase("usage")) {
                return sortBy;
            } else {
                throw new InvalidParameterValueException("Only value supported for sortBy parameter is : usage");
            }
        }

        return null;
    }

    @Override
    public String getCommandName() {
        return s_name;
    }
}
