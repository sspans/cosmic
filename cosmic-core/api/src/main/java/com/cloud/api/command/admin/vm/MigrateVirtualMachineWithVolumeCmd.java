package com.cloud.api.command.admin.vm;

import com.cloud.api.APICommand;
import com.cloud.api.ApiConstants;
import com.cloud.api.ApiErrorCode;
import com.cloud.api.BaseAsyncCmd;
import com.cloud.api.Parameter;
import com.cloud.api.ResponseObject.ResponseView;
import com.cloud.api.ServerApiException;
import com.cloud.api.response.HostResponse;
import com.cloud.api.response.UserVmResponse;
import com.cloud.event.EventTypes;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.ManagementServerException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.exception.VirtualMachineMigrationException;
import com.cloud.host.Host;
import com.cloud.user.Account;
import com.cloud.uservm.UserVm;
import com.cloud.utils.exception.InvalidParameterValueException;
import com.cloud.vm.VirtualMachine;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "migrateVirtualMachineWithVolume", group = "Virtual Machine",
        description = "Attempts Migration of a VM with its volumes to a different host",
        responseObject = UserVmResponse.class, entityType = {VirtualMachine.class},
        requestHasSensitiveInfo = false,
        responseHasSensitiveInfo = true)
public class MigrateVirtualMachineWithVolumeCmd extends BaseAsyncCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(MigrateVMCmd.class.getName());

    private static final String s_name = "migratevirtualmachinewithvolumeresponse";

    /////////////////////////////////////////////////////
    //////////////// API parameters /////////////////////
    /////////////////////////////////////////////////////

    @Parameter(name = ApiConstants.HOST_ID,
            type = CommandType.UUID,
            entityType = HostResponse.class,
            required = true,
            description = "Destination Host ID to migrate VM to.")
    private Long hostId;

    @Parameter(name = ApiConstants.VIRTUAL_MACHINE_ID,
            type = CommandType.UUID,
            entityType = UserVmResponse.class,
            required = true,
            description = "the ID of the virtual machine")
    private Long virtualMachineId;

    @Parameter(name = ApiConstants.MIGRATE_TO,
            type = CommandType.MAP,
            required = false,
            description = "Storage to pool mapping. This parameter specifies the mapping between a volume and a pool where you want to migrate that volume. Format of this " +
                    "parameter: migrateto[volume-index].volume=<uuid>&migrateto[volume-index].pool=<uuid>Where, [volume-index] indicates the index to identify the volume that " +
                    "you " +
                    "want to migrate, volume=<uuid> indicates the UUID of the volume that you want to migrate, and pool=<uuid> indicates the UUID of the pool where you want to " +
                    "migrate the volume. Example: migrateto[0].volume=<71f43cd6-69b0-4d3b-9fbc-67f50963d60b>&migrateto[0].pool=<a382f181-3d2b-4413-b92d-b8931befa7e1>&" +
                    "migrateto[1].volume=<88de0173-55c0-4c1c-a269-83d0279eeedf>&migrateto[1].pool=<95d6e97c-6766-4d67-9a30-c449c15011d1>&migrateto[2].volume=" +
                    "<1b331390-59f2-4796-9993-bf11c6e76225>&migrateto[2].pool=<41fdb564-9d3b-447d-88ed-7628f7640cbc>")
    private Map migrateVolumeTo;

    /////////////////////////////////////////////////////
    /////////////////// Accessors ///////////////////////
    /////////////////////////////////////////////////////

    @Override
    public String getEventType() {
        return EventTypes.EVENT_VM_MIGRATE;
    }

    @Override
    public String getEventDescription() {
        return "Attempting to migrate VM Id: " + getVirtualMachineId() + " to host Id: " + getHostId();
    }

    public Long getVirtualMachineId() {
        return virtualMachineId;
    }

    /////////////////////////////////////////////////////
    /////////////// API Implementation///////////////////
    /////////////////////////////////////////////////////

    public Long getHostId() {
        return hostId;
    }

    @Override
    public void execute() {
        final UserVm userVm = _userVmService.getUserVm(getVirtualMachineId());
        if (userVm == null) {
            throw new InvalidParameterValueException("Unable to find the VM by id=" + getVirtualMachineId());
        }

        final Host destinationHost = _resourceService.getHost(getHostId());
        if (destinationHost == null) {
            throw new InvalidParameterValueException("Unable to find the host to migrate the VM, host id =" + getHostId());
        }

        try {
            final VirtualMachine migratedVm = _userVmService.migrateVirtualMachineWithVolume(getVirtualMachineId(), destinationHost, getVolumeToPool());
            if (migratedVm != null) {
                final UserVmResponse response = _responseGenerator.createUserVmResponse(ResponseView.Full, "virtualmachine", (UserVm) migratedVm).get(0);
                response.setResponseName(getCommandName());
                setResponseObject(response);
            } else {
                throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, "Failed to migrate vm");
            }
        } catch (final ResourceUnavailableException ex) {
            s_logger.warn("Exception: ", ex);
            throw new ServerApiException(ApiErrorCode.RESOURCE_UNAVAILABLE_ERROR, ex.getMessage());
        } catch (final ConcurrentOperationException | ManagementServerException | VirtualMachineMigrationException e) {
            s_logger.warn("Exception: ", e);
            throw new ServerApiException(ApiErrorCode.INTERNAL_ERROR, e.getMessage());
        }
    }

    public Map<String, String> getVolumeToPool() {
        final Map<String, String> volumeToPoolMap = new HashMap<>();
        if (migrateVolumeTo != null && !migrateVolumeTo.isEmpty()) {
            final Collection<?> allValues = migrateVolumeTo.values();
            for (final Object allValue : allValues) {
                final HashMap<String, String> volumeToPool = (HashMap<String, String>) allValue;
                final String volume = volumeToPool.get("volume");
                final String pool = volumeToPool.get("pool");
                volumeToPoolMap.put(volume, pool);
            }
        }
        return volumeToPoolMap;
    }

    @Override
    public String getCommandName() {
        return s_name;
    }

    @Override
    public long getEntityOwnerId() {
        final UserVm userVm = _entityMgr.findById(UserVm.class, getVirtualMachineId());
        if (userVm != null) {
            return userVm.getAccountId();
        }

        return Account.ACCOUNT_ID_SYSTEM;
    }
}
