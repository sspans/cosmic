package com.cloud.api.command.user.snapshot;

import com.cloud.api.APICommand;
import com.cloud.api.ApiCommandJobType;
import com.cloud.api.ApiConstants;
import com.cloud.api.BaseListTaggedResourcesCmd;
import com.cloud.api.Parameter;
import com.cloud.api.response.ListResponse;
import com.cloud.api.response.SnapshotResponse;
import com.cloud.api.response.VolumeResponse;
import com.cloud.api.response.ZoneResponse;
import com.cloud.storage.Snapshot;
import com.cloud.utils.Pair;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "listSnapshots", group = "Snapshot", description = "Lists all available snapshots for the account.", responseObject = SnapshotResponse.class, entityType = {Snapshot.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListSnapshotsCmd extends BaseListTaggedResourcesCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(ListSnapshotsCmd.class.getName());

    private static final String s_name = "listsnapshotsresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ID, type = CommandType.UUID, entityType = SnapshotResponse.class, description = "lists snapshot by snapshot ID")
    private Long id;

    @Parameter(name = ApiConstants.INTERVAL_TYPE, type = CommandType.STRING, description = "valid values are HOURLY, DAILY, WEEKLY, and MONTHLY.")
    private String intervalType;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, description = "lists snapshot by snapshot name")
    private String snapshotName;

    @Parameter(name = ApiConstants.SNAPSHOT_TYPE, type = CommandType.STRING, description = "valid values are MANUAL or RECURRING.")
    private String snapshotType;

    @Parameter(name = ApiConstants.VOLUME_ID, type = CommandType.UUID, entityType = VolumeResponse.class, description = "the ID of the disk volume")
    private Long volumeId;

    @Parameter(name = ApiConstants.ZONE_ID, type = CommandType.UUID, entityType = ZoneResponse.class, description = "list snapshots by zone id")
    private Long zoneId;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    public Long getId() {
        return id;
    }

    public String getIntervalType() {
        return intervalType;
    }

    public String getSnapshotName() {
        return snapshotName;
    }

    public String getSnapshotType() {
        return snapshotType;
    }

    public Long getVolumeId() {
        return volumeId;
    }

    public Long getZoneId() {
        return zoneId;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    @Override
    public ApiCommandJobType getInstanceType() {
        return ApiCommandJobType.Snapshot;
    }

    @Override
    public void execute() {
        final Pair<List<? extends Snapshot>, Integer> result = _snapshotService.listSnapshots(this);
        final ListResponse<SnapshotResponse> response = new ListResponse<>();
        final List<SnapshotResponse> snapshotResponses = new ArrayList<>();
        for (final Snapshot snapshot : result.first()) {
            final SnapshotResponse snapshotResponse = _responseGenerator.createSnapshotResponse(snapshot);
            snapshotResponse.setObjectName("snapshot");
            snapshotResponses.add(snapshotResponse);
        }
        response.setResponses(snapshotResponses, result.second());
        response.setResponseName(getCommandName());

        setResponseObject(response);
    }

    @Override
    public String getCommandName() {
        return s_name;
    }
}
