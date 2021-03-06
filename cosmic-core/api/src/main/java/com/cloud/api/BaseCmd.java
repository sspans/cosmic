package com.cloud.api;

import com.cloud.acl.RoleType;
import com.cloud.affinity.AffinityGroupService;
import com.cloud.alert.AlertService;
import com.cloud.configuration.ConfigurationService;
import com.cloud.context.CallContext;
import com.cloud.dao.EntityManager;
import com.cloud.dao.UUIDManager;
import com.cloud.db.repository.ZoneRepository;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.NetworkRuleConflictException;
import com.cloud.exception.ResourceAllocationException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.framework.config.dao.ConfigurationDao;
import com.cloud.network.NetworkModel;
import com.cloud.network.NetworkService;
import com.cloud.network.StorageNetworkService;
import com.cloud.network.VpcVirtualNetworkApplianceService;
import com.cloud.network.firewall.FirewallService;
import com.cloud.network.lb.LoadBalancingRulesService;
import com.cloud.network.rules.RulesService;
import com.cloud.network.security.SecurityGroupService;
import com.cloud.network.vpc.NetworkACLService;
import com.cloud.network.vpc.VpcProvisioningService;
import com.cloud.network.vpc.VpcService;
import com.cloud.network.vpn.RemoteAccessVpnService;
import com.cloud.network.vpn.Site2SiteVpnService;
import com.cloud.projects.ProjectService;
import com.cloud.query.QueryService;
import com.cloud.resource.ResourceService;
import com.cloud.server.ManagementService;
import com.cloud.server.ResourceMetaDataService;
import com.cloud.server.TaggedResourceService;
import com.cloud.storage.DataStoreProviderApiService;
import com.cloud.storage.StorageService;
import com.cloud.storage.VolumeApiService;
import com.cloud.storage.snapshot.SnapshotApiService;
import com.cloud.template.TemplateApiService;
import com.cloud.user.Account;
import com.cloud.user.AccountService;
import com.cloud.user.DomainService;
import com.cloud.user.ResourceLimitService;
import com.cloud.utils.HttpUtils;
import com.cloud.utils.ReflectUtil;
import com.cloud.vm.UserVmService;
import com.cloud.vm.snapshot.VMSnapshotService;

import javax.inject.Inject;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseCmd {
    public static final String RESPONSE_TYPE_XML = HttpUtils.RESPONSE_TYPE_XML;
    public static final String RESPONSE_TYPE_JSON = HttpUtils.RESPONSE_TYPE_JSON;
    public static final String USER_ERROR_MESSAGE = "Internal error executing command, please contact your system administrator";
    protected static final Map<Class<?>, List<Field>> fieldsForCmdClass = new HashMap<>();
    private static final Logger s_logger = LoggerFactory.getLogger(BaseCmd.class.getName());
    private static final DateFormat s_outputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
    public static Pattern newInputDateFormat = Pattern.compile("[\\d]+-[\\d]+-[\\d]+ [\\d]+:[\\d]+:[\\d]+");
    @Inject
    public ConfigurationService _configService;
    @Inject
    public AccountService _accountService;
    @Inject
    public UserVmService _userVmService;
    @Inject
    public ManagementService _mgr;
    @Inject
    public StorageService _storageService;
    @Inject
    public VolumeApiService _volumeService;
    @Inject
    public ResourceService _resourceService;
    @Inject
    public NetworkService _networkService;
    @Inject
    public TemplateApiService _templateService;
    @Inject
    public SecurityGroupService _securityGroupService;
    @Inject
    public SnapshotApiService _snapshotService;
    @Inject
    public VpcVirtualNetworkApplianceService _routerService;
    @Inject
    public ResponseGenerator _responseGenerator;
    @Inject
    public EntityManager _entityMgr;
    @Inject
    public RulesService _rulesService;
    @Inject
    public LoadBalancingRulesService _lbService;
    @Inject
    public RemoteAccessVpnService _ravService;
    @Inject
    public ProjectService _projectService;
    @Inject
    public FirewallService _firewallService;
    @Inject
    public DomainService _domainService;
    @Inject
    public ResourceLimitService _resourceLimitService;
    @Inject
    public StorageNetworkService _storageNetworkService;
    @Inject
    public TaggedResourceService _taggedResourceService;
    @Inject
    public ResourceMetaDataService _resourceMetaDataService;
    @Inject
    public VpcService _vpcService;
    @Inject
    public NetworkACLService _networkACLService;
    @Inject
    public Site2SiteVpnService _s2sVpnService;
    @Inject
    public QueryService _queryService;
    @Inject
    public VMSnapshotService _vmSnapshotService;
    @Inject
    public DataStoreProviderApiService dataStoreProviderApiService;
    @Inject
    public VpcProvisioningService _vpcProvSvc;
    @Inject
    public AffinityGroupService _affinityGroupService;
    @Inject
    public NetworkModel _ntwkModel;
    @Inject
    public AlertService _alertSvc;
    @Inject
    public UUIDManager _uuidMgr;
    @Inject
    public ZoneRepository zoneRepository;
    @Inject
    public ConfigurationDao _configDao;

    private Object _responseObject;
    private Map<String, String> fullUrlParams;
    private HTTPMethod httpMethod;
    @Parameter(name = "response", type = CommandType.STRING)
    private String responseType;

    public static String getDateString(final Date date) {
        if (date == null) {
            return "";
        }
        String formattedString = null;
        synchronized (s_outputFormat) {
            formattedString = s_outputFormat.format(date);
        }
        return formattedString;
    }

    public abstract void execute() throws ResourceUnavailableException, InsufficientCapacityException, ServerApiException, ConcurrentOperationException,
            ResourceAllocationException, NetworkRuleConflictException;

    public void configure() {
    }

    public HTTPMethod getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(final String method) {
        if (method != null) {
            if (method.equalsIgnoreCase("GET")) {
                httpMethod = HTTPMethod.GET;
            } else if (method.equalsIgnoreCase("PUT")) {
                httpMethod = HTTPMethod.PUT;
            } else if (method.equalsIgnoreCase("POST")) {
                httpMethod = HTTPMethod.POST;
            } else if (method.equalsIgnoreCase("DELETE")) {
                httpMethod = HTTPMethod.DELETE;
            }
        } else {
            httpMethod = HTTPMethod.GET;
        }
    }

    public String getResponseType() {
        if (responseType == null) {
            return RESPONSE_TYPE_XML;
        }
        return responseType;
    }

    public void setResponseType(final String responseType) {
        this.responseType = responseType;
    }

    /**
     * For some reason this method does not return the actual command name, but more a name that
     * is used to create the response. So you can expect for a XCmd a value like xcmdresponse. Anyways
     * this methods is used in too many places so for now instead of changing it we just create another
     * method {@link BaseCmd#getActualCommandName()} that returns the value from {@link APICommand#name()}
     *
     * @return
     */
    public abstract String getCommandName();

    /**
     * Gets the CommandName based on the class annotations: the value from {@link APICommand#name()}
     *
     * @return the value from {@link APICommand#name()}
     */
    public String getActualCommandName() {
        String cmdName = null;
        if (this.getClass().getAnnotation(APICommand.class) != null) {
            cmdName = this.getClass().getAnnotation(APICommand.class).name();
        } else {
            cmdName = this.getClass().getName();
        }
        return cmdName;
    }

    /**
     * For commands the API framework needs to know the owner of the object being acted upon. This method is
     * used to determine that information.
     *
     * @return the id of the account that owns the object being acted upon
     */
    public abstract long getEntityOwnerId();

    public Object getResponseObject() {
        return _responseObject;
    }

    public void setResponseObject(final Object responseObject) {
        _responseObject = responseObject;
    }

    /**
     * This method doesn't return all the @{link Parameter}, but only the ones exposed
     * and allowed for current @{link RoleType}. This method will get the fields for a given
     * Cmd class only once and never again, so in case of a dynamic update the result would
     * be obsolete (this might be a plugin update. It is agreed upon that we will not do
     * upgrades dynamically but in case we come back on that decision we need to revisit this)
     *
     * @return
     */
    public List<Field> getParamFields() {
        final List<Field> allFields = getAllFieldsForClass(this.getClass());
        final List<Field> validFields = new ArrayList<>();
        final Account caller = CallContext.current().getCallingAccount();

        for (final Field field : allFields) {
            final Parameter parameterAnnotation = field.getAnnotation(Parameter.class);

            //TODO: Annotate @Validate on API Cmd classes, FIXME how to process Validate
            final RoleType[] allowedRoles = parameterAnnotation.authorized();
            boolean roleIsAllowed = true;
            if (allowedRoles.length > 0) {
                roleIsAllowed = false;
                for (final RoleType allowedRole : allowedRoles) {
                    if (allowedRole.getValue() == caller.getType()) {
                        roleIsAllowed = true;
                        break;
                    }
                }
            }

            if (roleIsAllowed) {
                validFields.add(field);
            } else {
                s_logger.debug("Ignoring paremeter " + parameterAnnotation.name() + " as the caller is not authorized to pass it in");
            }
        }

        return validFields;
    }

    protected List<Field> getAllFieldsForClass(final Class<?> clazz) {
        List<Field> filteredFields = fieldsForCmdClass.get(clazz);

        // If list of fields was not cached yet
        if (filteredFields == null) {
            final List<Field> allFields = ReflectUtil.getAllFieldsForClass(this.getClass(), BaseCmd.class);
            filteredFields = new ArrayList<>();

            for (final Field field : allFields) {
                final Parameter parameterAnnotation = field.getAnnotation(Parameter.class);
                if ((parameterAnnotation != null) && parameterAnnotation.expose()) {
                    filteredFields.add(field);
                }
            }

            // Cache the prepared list for future use
            fieldsForCmdClass.put(clazz, filteredFields);
        }
        return filteredFields;
    }

    public Map<String, String> getFullUrlParams() {
        return fullUrlParams;
    }

    public void setFullUrlParams(final Map<String, String> map) {
        fullUrlParams = map;
    }

    /**
     * To be overwritten by any class who needs specific validation
     */
    public void validateSpecificParameters(final Map<String, String> params) {
        // To be overwritten by any class who needs specific validation
    }

    /**
     * display flag is used to control the display of the resource only to the end user. It doesnt affect Root Admin.
     *
     * @return display flag
     */
    public boolean isDisplay() {
        final CallContext context = CallContext.current();
        final Map<Object, Object> contextMap = context.getContextParameters();
        boolean isDisplay = true;

        // Iterate over all the first class entities in context and check their display property.
        for (final Map.Entry<Object, Object> entry : contextMap.entrySet()) {
            try {
                final Object key = entry.getKey();
                final Class clz = Class.forName((String) key);
                if (Displayable.class.isAssignableFrom(clz)) {
                    final Object objVO = getEntityVO(clz, entry.getValue());
                    isDisplay = ((Displayable) objVO).isDisplay();
                }

                // If the flag is false break immediately
                if (!isDisplay) {
                    break;
                }
            } catch (final Exception e) {
                s_logger.trace("Caught exception while checking first class entities for display property, continuing on", e);
            }
        }

        context.setEventDisplayEnabled(isDisplay);
        return isDisplay;
    }

    private Object getEntityVO(final Class entityType, final Object entityId) {

        // entityId can be internal db id or UUID so accordingly call findbyId or findByUUID

        if (entityId instanceof Long) {
            // Its internal db id - use findById
            return _entityMgr.findById(entityType, (Long) entityId);
        } else if (entityId instanceof String) {
            try {
                // In case its an async job the internal db id would be a string because of json deserialization
                final Long internalId = Long.valueOf((String) entityId);
                return _entityMgr.findById(entityType, internalId);
            } catch (final NumberFormatException e) {
                // It is uuid - use findByUuid`
                return _entityMgr.findByUuid(entityType, (String) entityId);
            }
        }

        return null;
    }

    public static enum HTTPMethod {
        GET, POST, PUT, DELETE
    }

    public static enum CommandType {
        BOOLEAN, DATE, FLOAT, DOUBLE, INTEGER, SHORT, LIST, LONG, OBJECT, MAP, STRING, TZDATE, UUID
    }
}
