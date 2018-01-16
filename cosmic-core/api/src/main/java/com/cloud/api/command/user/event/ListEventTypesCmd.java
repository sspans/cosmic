package com.cloud.api.command.user.event;

import com.cloud.api.APICommand;
import com.cloud.api.BaseCmd;
import com.cloud.api.response.EventTypeResponse;
import com.cloud.api.response.ListResponse;
import com.cloud.user.Account;

import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@APICommand(name = "listEventTypes", group = "Event", description = "List Event Types", responseObject = EventTypeResponse.class,
        requestHasSensitiveInfo = false, responseHasSensitiveInfo = false)
public class ListEventTypesCmd extends BaseCmd {
    public static final Logger s_logger = LoggerFactory.getLogger(ListEventTypesCmd.class.getName());
    private static final String s_name = "listeventtypesresponse";

    @Override
    public void execute() {
        final String[] result = _mgr.listEventTypes();
        final ListResponse<EventTypeResponse> response = new ListResponse<>();
        final ArrayList<EventTypeResponse> responses = new ArrayList<>();
        if (result != null) {
            for (final String eventType : result) {
                final EventTypeResponse eventTypeResponse = new EventTypeResponse();
                eventTypeResponse.setName(eventType);
                eventTypeResponse.setObjectName("eventtype");
                responses.add(eventTypeResponse);
            }
        }
        response.setResponses(responses);
        response.setResponseName(getCommandName());
        this.setResponseObject(response);
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
