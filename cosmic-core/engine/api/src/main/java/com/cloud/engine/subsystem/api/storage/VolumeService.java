package com.cloud.engine.subsystem.api.storage;

import com.cloud.agent.api.to.VirtualMachineTO;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.framework.async.AsyncCallFuture;
import com.cloud.host.Host;
import com.cloud.hypervisor.Hypervisor.HypervisorType;
import com.cloud.offering.DiskOffering;
import com.cloud.storage.command.CommandResult;
import com.cloud.utils.Pair;

import java.util.Map;

public interface VolumeService {
    ChapInfo getChapInfo(VolumeInfo volumeInfo, DataStore dataStore);

    boolean grantAccess(DataObject dataObject, Host host, DataStore dataStore);

    void revokeAccess(DataObject dataObject, Host host, DataStore dataStore);

    AsyncCallFuture<VolumeApiResult> createVolumeAsync(VolumeInfo volume, DataStore store);

    AsyncCallFuture<VolumeApiResult> expungeVolumeAsync(VolumeInfo volume);

    AsyncCallFuture<VolumeApiResult> createVolumeFromSnapshot(VolumeInfo volume, DataStore store, SnapshotInfo snapshot);

    AsyncCallFuture<VolumeApiResult> createManagedStorageAndVolumeFromTemplateAsync(VolumeInfo volumeInfo, long destDataStoreId,
                                                                                    TemplateInfo srcTemplateInfo, long destHostId);

    AsyncCallFuture<VolumeApiResult> createVolumeFromTemplateAsync(VolumeInfo volume, long dataStoreId,
                                                                   TemplateInfo template);

    AsyncCallFuture<VolumeApiResult> copyVolume(VolumeInfo srcVolume, DataStore destStore);

    AsyncCallFuture<VolumeApiResult> migrateVolume(VolumeInfo srcVolume, DataStore destStore);

    AsyncCallFuture<CommandResult> migrateVolumes(Map<VolumeInfo, DataStore> volumeMap, VirtualMachineTO vmTo, Host srcHost, Host destHost);

    boolean destroyVolume(long volumeId) throws ConcurrentOperationException;

    AsyncCallFuture<VolumeApiResult> registerVolume(VolumeInfo volume, DataStore store);

    Pair<EndPoint, DataObject> registerVolumeForPostUpload(VolumeInfo volume, DataStore store);

    AsyncCallFuture<VolumeApiResult> resize(VolumeInfo volume);

    void resizeVolumeOnHypervisor(long volumeId, long newSize, long destHostId, String instanceName);

    void handleVolumeSync(DataStore store);

    SnapshotInfo takeSnapshot(VolumeInfo volume);

    VolumeInfo updateHypervisorSnapshotReserveForVolume(DiskOffering diskOffering, long volumeId, HypervisorType hyperType);

    class VolumeApiResult extends CommandResult {
        private final VolumeInfo volume;

        public VolumeApiResult(final VolumeInfo volume) {
            super();
            this.volume = volume;
        }

        public VolumeInfo getVolume() {
            return this.volume;
        }
    }
}
