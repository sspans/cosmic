package com.cloud.api.command.user.snapshot;

import com.cloud.api.APICommand;
import com.cloud.api.ApiCommandJobType;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseAsyncCmd;
import com.cloud.api.BaseAsyncCreateCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.DomainResponse;
import com.cloud.api.response.SnapshotResponse;
import com.cloud.api.response.VolumeResponse;
import com.cloud.context.CallContext;
import com.cloud.event.EventTypes;
import com.cloud.exception.PermissionDeniedException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.projects.Project;
import com.cloud.storage.Snapshot;
import com.cloud.storage.Volume;
import com.cloud.user.Account;
import com.cloud.utils.exception.InvalidParameterValueException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "createSnapshot", group = "Snapshot", description = "Creates an instant snapshot of a volume.", responseObject = SnapshotResponse.class, entityType = {Snapshot.class},
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class CreateSnapshotCmd extends BaseAsyncCreateCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(CreateSnapshotCmd.class.getName());
    private static final String s_name = "createsnapshotresponse";

    // ///////////////////////////////////////////////////
    // ////////////// API parameters /////////////////////
    // ///////////////////////////////////////////////////

    @Parameter(name = ApiConstants.ACCOUNT, type = CommandType.STRING, description = "The account of the snapshot. The account parameter must be used with the domainId parameter.")
    private String accountName;

    @Parameter(name = ApiConstants.DOMAIN_ID, type = CommandType.UUID, entityType = DomainResponse.class,
               description = "The domain ID of the snapshot. If used with the account parameter, specifies a domain for the account associated with the disk volume.")
    private Long domainId;

    @Parameter(name = ApiConstants.VOLUME_ID, type = CommandType.UUID, entityType = VolumeResponse.class, required = true, description = "The ID of the disk volume")
    private Long volumeId;

    @Parameter(name = ApiConstants.SNAPSHOT_QUIESCEVM, type = CommandType.BOOLEAN, required = false, description = "quiesce vm if true")
    private Boolean quiescevm;

    @Parameter(name = ApiConstants.NAME, type = CommandType.STRING, description = "the name of the snapshot")
    private String snapshotName;

    // ///////////////////////////////////////////////////
    // ///////////////// Accessors ///////////////////////
    // ///////////////////////////////////////////////////

    public static String getResultObjectName() {
        return ApiConstants.SNAPSHOT;
    }

    public String getAccountName() {
        return accountName;
    }

    public Long getDomainId() {
        return domainId;
    }

    @Override
    public String getEventType() {
        return EventTypes.EVENT_SNAPSHOT_CREATE;
    }

    @Override
    public String getEventDescription() {
        return "creating snapshot for volume: " + getVolumeUuid();
    }

    public String getVolumeUuid() {
        final Volume volume = (Volume) this._entityMgr.findById(Volume.class, getVolumeId());
        if (volume == null) {
            throw new InvalidParameterValueException("Unable to find volume's UUID");
        }
        return volume.getUuid();
    }

    public Long getVolumeId() {
        return volumeId;
    }

    @Override
    public ApiCommandJobType getInstanceType() {
        return ApiCommandJobType.Snapshot;
    }

    // ///////////////////////////////////////////////////
    // ///////////// API Implementation///////////////////
    // ///////////////////////////////////////////////////

    @Override
    public String getSyncObjType() {
        if (getSyncObjId() != null) {
            return BaseAsyncCmd.snapshotHostSyncObject;
        }
        return null;
    }

    private Long getHostId() {
        final Volume volume = _entityMgr.findById(Volume.class, getVolumeId());
        if (volume == null) {
            throw new InvalidParameterValueException("Unable to find volume by id");
        }
        return _snapshotService.getHostIdForSnapshotOperation(volume);
    }

    @Override
    public Long getSyncObjId() {
        if (getHostId() != null) {
            return getHostId();
        }
        return null;
    }

    @Override
    public void create() throws ResourceAllocationException {
        final Snapshot snapshot = _volumeService.allocSnapshot(getVolumeId(), getPolicyId(), getSnapshotName());
        if (snapshot != null) {
            setEntityId(snapshot.getId());
            setEntityUuid(snapshot.getUuid());
        } else {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create snapshot");
        }
    }

    public Long getPolicyId() {
            return Snapshot.MANUAL_POLICY_ID;
    }

    public String getSnapshotName() {
        return snapshotName;
    }

    @Override
    public void execute() {
        s_logger.info("VOLSS: createSnapshotCmd starts:" + System.currentTimeMillis());
        CallContext.current().setEventDetails("Volume Id: " + getVolumeUuid());
        final Snapshot snapshot;
        try {
            snapshot = _volumeService.takeSnapshot(getVolumeId(), getPolicyId(), getEntityId(), _accountService.getAccount(getEntityOwnerId()), getQuiescevm());
            if (snapshot != null) {
                final SnapshotResponse response = _responseGenerator.createSnapshotResponse(snapshot);
                response.setResponseName(getCommandName());
                setResponseObject(response);
            } else {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create snapshot due to an internal error creating snapshot for volume " + volumeId);
            }
        } catch (final Exception e) {
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to create snapshot due to an internal error creating snapshot for volume " + volumeId);
        }
    }

    public Boolean getQuiescevm() {
        if (quiescevm == null) {
            return false;
        } else {
            return quiescevm;
        }
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {

        final Volume volume = _entityMgr.findById(Volume.class, getVolumeId());
        if (volume == null) {
            throw new InvalidParameterValueException("Unable to find volume by id=" + volumeId);
        }

        final Account account = _accountService.getAccount(volume.getAccountId());
        //Can create templates for enabled projects/accounts only
        if (account.getType() == Account.ACCOUNT_TYPE_PROJECT) {
            final Project project = _projectService.findByProjectAccountId(volume.getAccountId());
            if (project.getState() != Project.State.Active) {
                throw new PermissionDeniedException("Can't add resources to the project id=" + project.getId() + " in state=" + project.getState() + " as it's no longer active");
            }
        } else if (account.getState() == Account.State.disabled) {
            throw new PermissionDeniedException("The owner of template is disabled: " + account);
        }

        return volume.getAccountId();
    }
}
